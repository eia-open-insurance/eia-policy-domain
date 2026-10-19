package com.eia.policy.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Anti-corruption boundary to the Policy expert service. Canonical
 * {@code application/problem+json} failures are translated into the domain's
 * own {@link ApiException} so the public error envelope is built in one place.
 */
@Component
public class PolicyClient {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyClient.class);

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public PolicyClient(RestClient.Builder builder,
                        ObjectMapper objectMapper,
                        @Value("${eia.policy.expert-url:http://localhost:8083}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    /** Compatibility overload: returns the first page using the maximum size. */
    public List<PolicyDto> findAll(UUID tenantId) {
        return findAll(tenantId, Pagination.DEFAULT_PAGE, Pagination.MAX_SIZE).items();
    }

    public ExpertPolicyPage findAll(UUID tenantId, int page, int size) {
        ExpertPolicyPage result = call("list policies", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/policies")
                        .queryParam("tenantId", tenantId)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .retrieve()
                .body(ExpertPolicyPage.class));
        if (result == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return result;
    }

    public PolicyDto findById(UUID tenantId, UUID id) {
        PolicyDto policy = call("get policy", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/policies/{id}")
                        .queryParam("tenantId", tenantId)
                        .build(id))
                .retrieve()
                .body(PolicyDto.class));
        if (policy == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return policy;
    }

    /** Compatibility overload: creates without an idempotency scope. */
    public PolicyDto create(UUID tenantId, CreatePolicyCommand command) {
        return create(tenantId, command, null).policy();
    }

    public CreateOutcome create(UUID tenantId, CreatePolicyCommand command, String rawIdempotencyKey) {
        String idempotencyKey = IdempotencyKeys.normalize(rawIdempotencyKey);
        ResponseEntity<PolicyDto> response = call("create policy", () -> {
            RestClient.RequestBodySpec spec = client.post().uri("/internal/v1/policies");
            if (idempotencyKey != null) {
                spec = spec.header("Idempotency-Key", idempotencyKey);
            }
            return spec.body(new ExpertCreatePolicyRequest(tenantId, command.policyNumber(),
                            command.productCode(), command.insuredPartyId(), command.effectiveDate(),
                            command.expirationDate(), command.premiumAmount(), command.premiumCurrency(),
                            command.limitAmount(), command.limitCurrency()))
                    .retrieve()
                    .toEntity(PolicyDto.class);
        });
        PolicyDto policy = response.getBody();
        if (policy == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return new CreateOutcome(policy, isReplayed(response));
    }

    /** Compatibility overload: optimistic concurrency from the body only. */
    public PolicyDto updateStatus(UUID tenantId, UUID id, String status, int expectedVersion,
                                  String reason) {
        return updateStatus(tenantId, id, status, expectedVersion, reason, null);
    }

    public PolicyDto updateStatus(UUID tenantId, UUID id, String status, int expectedVersion,
                                  String reason, String ifMatch) {
        PolicyDto policy = call("update policy status", () -> {
            RestClient.RequestBodySpec spec = client.patch()
                    .uri(uri -> uri.path("/internal/v1/policies/{id}/status")
                            .queryParam("tenantId", tenantId)
                            .build(id));
            if (ifMatch != null && !ifMatch.isBlank()) {
                spec = spec.header(HttpHeaders.IF_MATCH, ifMatch);
            }
            return spec.body(new ExpertStatusRequest(status, expectedVersion, reason))
                    .retrieve()
                    .body(PolicyDto.class);
        });
        if (policy == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return policy;
    }

    private static boolean isReplayed(ResponseEntity<?> response) {
        String header = response.getHeaders().getFirst("Idempotent-Replayed");
        if (header != null) {
            return Boolean.parseBoolean(header);
        }
        return response.getStatusCode().value() == HttpStatus.OK.value();
    }

    private <T> T call(String operation, Supplier<T> invocation) {
        try {
            return invocation.get();
        } catch (RestClientResponseException ex) {
            throw translate(operation, ex);
        } catch (ResourceAccessException ex) {
            LOG.warn("Policy expert is unreachable during {}", operation, ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EXPERT_UNAVAILABLE",
                    "The Policy expert is unavailable; retry later.");
        } catch (RestClientException ex) {
            LOG.warn("Policy expert call failed during {}", operation, ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_ERROR",
                    "The Policy expert could not complete the request.");
        }
    }

    private ApiException translate(String operation, RestClientResponseException ex) {
        int rawStatus = ex.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(rawStatus);
        JsonNode body = readBody(ex);
        String expertCode = text(body, "code");
        String expertDetail = firstNonBlank(text(body, "detail"), text(body, "message"));
        List<ApiException.FieldIssue> issues = parseIssues(body);
        Map<String, Object> meta = parseMeta(body);
        String message = expertDetail == null
                ? "Policy expert " + operation + " failed with HTTP " + rawStatus
                : expertDetail;

        if (rawStatus == HttpStatus.NOT_FOUND.value()) {
            return new ApiException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND", message, issues, meta);
        }
        if (rawStatus == HttpStatus.CONFLICT.value()) {
            String code = expertCode == null || expertCode.isBlank()
                    ? "CONCURRENT_MODIFICATION" : expertCode;
            return new ApiException(HttpStatus.CONFLICT, code, message, issues, meta);
        }
        if (status != null && status.is4xxClientError()) {
            String code = expertCode == null || expertCode.isBlank()
                    ? "VALIDATION_ERROR" : expertCode;
            return new ApiException(status, code, message, issues, meta);
        }
        LOG.warn("Policy expert {} failed with HTTP {}", operation, rawStatus);
        return new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_ERROR", message, issues, meta);
    }

    private JsonNode readBody(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<ApiException.FieldIssue> parseIssues(JsonNode body) {
        if (body == null) {
            return List.of();
        }
        List<ApiException.FieldIssue> issues = new ArrayList<>();
        JsonNode errors = body.get("errors");
        if (errors != null && errors.isArray()) {
            for (JsonNode error : errors) {
                String field = text(error, "field");
                String code = text(error, "code");
                String message = text(error, "message");
                if (field != null) {
                    issues.add(new ApiException.FieldIssue(field,
                            code == null || code.isBlank() ? "INVALID_FIELD" : code,
                            message == null ? "is invalid" : message));
                }
            }
        }
        // Legacy expert bodies used a details[] array; accept it during rollout.
        JsonNode details = body.get("details");
        if (issues.isEmpty() && details != null && details.isArray()) {
            for (JsonNode detail : details) {
                String field = text(detail, "field");
                String message = text(detail, "message");
                if (field != null) {
                    issues.add(new ApiException.FieldIssue(field, "INVALID_FIELD",
                            message == null ? "is invalid" : message));
                }
            }
        }
        return issues;
    }

    private Map<String, Object> parseMeta(JsonNode body) {
        if (body == null) {
            return Map.of();
        }
        JsonNode meta = body.get("meta");
        if (meta == null || !meta.isObject()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(meta, new TypeReference<Map<String, Object>>() {
            });
        } catch (IllegalArgumentException ignored) {
            return Map.of();
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String text(JsonNode body, String field) {
        if (body == null) {
            return null;
        }
        JsonNode node = body.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private record ExpertCreatePolicyRequest(
            UUID tenantId,
            String policyNumber,
            String productCode,
            UUID insuredPartyId,
            LocalDate effectiveDate,
            LocalDate expirationDate,
            BigDecimal premiumAmount,
            String premiumCurrency,
            BigDecimal limitAmount,
            String limitCurrency
    ) {
    }

    private record ExpertStatusRequest(String status, int expectedVersion, String reason) {
    }
}
