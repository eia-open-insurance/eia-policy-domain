package com.eia.policy.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Anti-corruption boundary to the Policy expert service.
 */
@Component
public class PolicyClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public PolicyClient(RestClient.Builder builder,
                        ObjectMapper objectMapper,
                        @Value("${eia.policy.expert-url:http://localhost:8083}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public List<PolicyDto> findAll(UUID tenantId) {
        List<PolicyDto> policies = call("list policies", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/policies").queryParam("tenantId", tenantId).build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<PolicyDto>>() {}));
        return policies == null ? List.of() : policies;
    }

    public PolicyDto findById(UUID tenantId, UUID id) {
        PolicyDto policy = call("get policy", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/policies/{id}").queryParam("tenantId", tenantId).build(id))
                .retrieve()
                .body(PolicyDto.class));
        if (policy == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return policy;
    }

    public PolicyDto create(UUID tenantId, CreatePolicyCommand command) {
        PolicyDto policy = call("create policy", () -> client.post()
                .uri("/internal/v1/policies")
                .body(new ExpertCreatePolicyRequest(tenantId, command.policyNumber(), command.productCode(),
                        command.insuredPartyId(), command.effectiveDate(), command.expirationDate(),
                        command.premiumAmount(), command.premiumCurrency(),
                        command.limitAmount(), command.limitCurrency()))
                .retrieve()
                .body(PolicyDto.class));
        if (policy == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return policy;
    }

    public PolicyDto updateStatus(UUID tenantId, UUID id, String status, int expectedVersion, String reason) {
        PolicyDto policy = call("update policy status", () -> client.patch()
                .uri(uri -> uri.path("/internal/v1/policies/{id}/status")
                        .queryParam("tenantId", tenantId).build(id))
                .body(new ExpertStatusRequest(status, expectedVersion, reason))
                .retrieve()
                .body(PolicyDto.class));
        if (policy == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return policy;
    }

    private <T> T call(String operation, Supplier<T> invocation) {
        try {
            return invocation.get();
        } catch (RestClientResponseException ex) {
            throw translate(operation, ex);
        } catch (ResourceAccessException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EXPERT_UNAVAILABLE",
                    "Policy expert is unavailable: " + ex.getMessage());
        }
    }

    private ApiException translate(String operation, RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        JsonNode body = readBody(ex);
        String expertCode = body == null ? null : text(body, "code");
        String expertMessage = body == null ? null : text(body, "message");
        String message = expertMessage == null
                ? "Policy expert " + operation + " failed with HTTP " + status
                : expertMessage;

        if (status == 404) {
            return new ApiException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND", message);
        }
        if (status == 409) {
            if ("DUPLICATE_POLICY_NUMBER".equals(expertCode)) {
                return new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_POLICY_NUMBER", message);
            }
            return new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", message);
        }
        if (status >= 500) {
            return new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_ERROR", message);
        }
        return new ApiException(HttpStatus.BAD_REQUEST,
                expertCode == null ? "EXPERT_VALIDATION_ERROR" : expertCode, message);
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

    private static String text(JsonNode body, String field) {
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
    ) {}

    private record ExpertStatusRequest(String status, int expectedVersion, String reason) {}
}
