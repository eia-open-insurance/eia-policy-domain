package com.eia.policy.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final String TENANT_HEADER = "X-EIA-Tenant";

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

    // ------------------------------------------------------------------
    // Second-slice policy contract: versions, coverage/limits, endorsements,
    // insureds/locations, cancellation/reinstatement.
    // ------------------------------------------------------------------

    public ExpertItemPage<PolicyVersionDto> findVersions(UUID tenantId, UUID policyId,
                                                         int page, int size) {
        return list("list policy versions", "/internal/v1/policies/{id}/versions", tenantId,
                policyId, page, size,
                new ParameterizedTypeReference<ExpertItemPage<PolicyVersionDto>>() {
                });
    }

    public CurrentVersionDto findCurrentVersion(UUID tenantId, UUID policyId) {
        CurrentVersionDto current = call("get current policy version", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/policies/{id}/versions/current")
                        .queryParam("tenantId", tenantId)
                        .build(policyId))
                .retrieve()
                .body(CurrentVersionDto.class));
        if (current == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return current;
    }

    public PolicyVersionDto findVersion(UUID tenantId, UUID policyId, UUID versionId) {
        PolicyVersionDto version = call("get policy version", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/policies/{id}/versions/{versionId}")
                        .queryParam("tenantId", tenantId)
                        .build(policyId, versionId))
                .retrieve()
                .body(PolicyVersionDto.class));
        if (version == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return version;
    }

    public ExpertItemPage<CoverageViewDto> findCoverages(UUID tenantId, UUID policyId,
                                                         int page, int size) {
        return list("list coverages", "/internal/v1/policies/{id}/coverages", tenantId, policyId,
                page, size, new ParameterizedTypeReference<ExpertItemPage<CoverageViewDto>>() {
                });
    }

    public Mutation<CoverageViewDto> createCoverage(UUID tenantId, UUID policyId,
                                                    CreateCoverageCommand command,
                                                    String ifMatch, String idempotencyKey) {
        return mutate("create coverage", "/internal/v1/policies/{id}/coverages", tenantId,
                policyId, command, ifMatch, idempotencyKey, CoverageViewDto.class);
    }

    public ExpertItemPage<LimitDto> findLimits(UUID tenantId, UUID policyId, int page, int size) {
        return list("list limits", "/internal/v1/policies/{id}/limits", tenantId, policyId, page,
                size, new ParameterizedTypeReference<ExpertItemPage<LimitDto>>() {
                });
    }

    public ExpertItemPage<LimitStateDto> findAggregateState(UUID tenantId, UUID policyId,
                                                            int page, int size) {
        return list("list aggregate state", "/internal/v1/policies/{id}/aggregate-state", tenantId,
                policyId, page, size,
                new ParameterizedTypeReference<ExpertItemPage<LimitStateDto>>() {
                });
    }

    public Mutation<LimitStateDto> applyErosion(UUID tenantId, UUID policyId, ErosionCommand command,
                                                String idempotencyKey) {
        return mutate("apply erosion", "/internal/v1/policies/{id}/erosion", tenantId, policyId,
                command, null, idempotencyKey, LimitStateDto.class);
    }

    public ExpertItemPage<EndorsementDto> findEndorsements(UUID tenantId, UUID policyId,
                                                           int page, int size) {
        return list("list endorsements", "/internal/v1/policies/{id}/endorsements", tenantId,
                policyId, page, size,
                new ParameterizedTypeReference<ExpertItemPage<EndorsementDto>>() {
                });
    }

    public EndorsementDto findEndorsement(UUID tenantId, UUID policyId, UUID endorsementId) {
        EndorsementDto endorsement = call("get endorsement", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/policies/{id}/endorsements/{endorsementId}")
                        .queryParam("tenantId", tenantId)
                        .build(policyId, endorsementId))
                .retrieve()
                .body(EndorsementDto.class));
        if (endorsement == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return endorsement;
    }

    public Mutation<EndorsementDto> createEndorsement(UUID tenantId, UUID policyId,
                                                      CreateEndorsementCommand command,
                                                      String ifMatch, String idempotencyKey) {
        return mutate("create endorsement", "/internal/v1/policies/{id}/endorsements", tenantId,
                policyId, command, ifMatch, idempotencyKey, EndorsementDto.class);
    }

    public ExpertItemPage<InsuredDto> findInsureds(UUID tenantId, UUID policyId,
                                                   int page, int size) {
        return list("list insureds", "/internal/v1/policies/{id}/insureds", tenantId, policyId,
                page, size, new ParameterizedTypeReference<ExpertItemPage<InsuredDto>>() {
                });
    }

    public Mutation<InsuredDto> createInsured(UUID tenantId, UUID policyId,
                                              CreateInsuredCommand command, String ifMatch,
                                              String idempotencyKey) {
        return mutate("create insured", "/internal/v1/policies/{id}/insureds", tenantId, policyId,
                command, ifMatch, idempotencyKey, InsuredDto.class);
    }

    public ExpertItemPage<LocationDto> findLocations(UUID tenantId, UUID policyId,
                                                     int page, int size) {
        return list("list locations", "/internal/v1/policies/{id}/locations", tenantId, policyId,
                page, size, new ParameterizedTypeReference<ExpertItemPage<LocationDto>>() {
                });
    }

    public Mutation<LocationDto> createLocation(UUID tenantId, UUID policyId,
                                                CreateLocationCommand command, String ifMatch,
                                                String idempotencyKey) {
        return mutate("create location", "/internal/v1/policies/{id}/locations", tenantId, policyId,
                command, ifMatch, idempotencyKey, LocationDto.class);
    }

    public ExpertItemPage<CancellationDto> findCancellations(UUID tenantId, UUID policyId,
                                                             int page, int size) {
        return list("list cancellations", "/internal/v1/policies/{id}/cancellations", tenantId,
                policyId, page, size,
                new ParameterizedTypeReference<ExpertItemPage<CancellationDto>>() {
                });
    }

    public Mutation<CancellationDto> cancelPolicy(UUID tenantId, UUID policyId,
                                                  CancelPolicyCommand command, String ifMatch,
                                                  String idempotencyKey) {
        return mutate("cancel policy", "/internal/v1/policies/{id}/cancel", tenantId, policyId,
                command, ifMatch, idempotencyKey, CancellationDto.class);
    }

    public ExpertItemPage<ReinstatementDto> findReinstatements(UUID tenantId, UUID policyId,
                                                               int page, int size) {
        return list("list reinstatements", "/internal/v1/policies/{id}/reinstatements", tenantId,
                policyId, page, size,
                new ParameterizedTypeReference<ExpertItemPage<ReinstatementDto>>() {
                });
    }

    public Mutation<ReinstatementDto> reinstatePolicy(UUID tenantId, UUID policyId,
                                                      ReinstatePolicyCommand command,
                                                      String ifMatch, String idempotencyKey) {
        return mutate("reinstate policy", "/internal/v1/policies/{id}/reinstate", tenantId,
                policyId, command, ifMatch, idempotencyKey, ReinstatementDto.class);
    }

    /**
     * Renews a policy into a successor term. Renewal is a DIRECT-book command,
     * so the tenant and the DIRECT book are forwarded on every call in addition
     * to the query-scoped tenant used by the other proxy routes.
     */
    public Mutation<PolicyDto> renewPolicy(UUID tenantId, UUID policyId,
                                           RenewPolicyCommand command) {
        return mutateDirect("renew policy", "/internal/v1/policies/{id}/renew", tenantId, policyId,
                command, PolicyDto.class);
    }

    // ------------------------------------------------------------------
    // Premium audit (SPEC 11.2.8 policy.premium-audit). The expert owns the
    // audit eligibility, the new version, the premium delta, and the posting
    // event. It normally returns the updated policy alone; an envelope
    // carrying the policy and an audit row is also accepted.
    // ------------------------------------------------------------------

    /**
     * Applies an audited premium to a policy. The expert's bare
     * {@link PolicyDto} response is the current shape; an envelope is also
     * accepted with the policy under {@code policy} (or {@code updatedPolicy})
     * and the audit under {@code premiumAudit}, {@code audit},
     * {@code auditResult}, or {@code result}. The tenant and DIRECT book are
     * forwarded on every call, together with the optional {@code If-Match} and
     * {@code Idempotency-Key} headers.
     */
    public Mutation<PremiumAuditResult> applyPremiumAudit(
            UUID tenantId, UUID policyId, ApplyPremiumAuditCommand command,
            String ifMatch, String idempotencyKey) {
        ResponseEntity<JsonNode> response = callEntity("apply premium audit", () -> {
            RestClient.RequestBodySpec spec = client.post()
                    .uri(uri -> uri.path("/internal/v1/policies/{id}/premium-audit")
                            .queryParam("tenantId", tenantId)
                            .build(policyId))
                    .header(TENANT_HEADER, tenantId.toString())
                    .header(BookScope.BOOK_HEADER, BookScope.DIRECT);
            if (ifMatch != null && !ifMatch.isBlank()) {
                spec = spec.header(HttpHeaders.IF_MATCH, ifMatch);
            }
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                spec = spec.header("Idempotency-Key", idempotencyKey);
            }
            return spec.body(new ExpertPremiumAuditRequest(command.auditedPremium(),
                            command.effectiveDate(), command.reason(), command.approvedBy(),
                            command.approvalRef()))
                    .retrieve()
                    .toEntity(JsonNode.class);
        });
        return new Mutation<>(toPremiumAuditResult(response.getBody()),
                response.getHeaders().getETag(), isReplayed(response));
    }

    /**
     * Projects either expert response shape onto the internal result. A bare
     * policy body is used as the policy and leaves the audit null; an envelope
     * carries both members and the controller publishes the public envelope.
     * Missing or unreadable bodies are expert-boundary failures, never a public
     * {@code 500}.
     */
    private PremiumAuditResult toPremiumAuditResult(JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        JsonNode policyNode = objectMember(body, "policy", "updatedPolicy", "policyDto");
        if (policyNode == null) {
            policyNode = body;
        }
        PolicyDto policy;
        try {
            policy = objectMapper.treeToValue(policyNode, PolicyDto.class);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an unreadable policy response");
        }
        if (policy == null || policy.id() == null || policy.policyNumber() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        JsonNode auditNode = objectMember(body, "premiumAudit", "audit", "auditResult", "result");
        PremiumAuditDto premiumAudit = null;
        if (auditNode != null) {
            try {
                premiumAudit = objectMapper.treeToValue(auditNode, PremiumAuditDto.class);
            } catch (JsonProcessingException ex) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                        "Policy expert returned an unreadable premium audit response");
            }
        }
        return new PremiumAuditResult(policy, premiumAudit);
    }

    private static JsonNode objectMember(JsonNode body, String... names) {
        for (String name : names) {
            JsonNode node = body.get(name);
            if (node != null && node.isObject()) {
                return node;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Billing plan and installment schedule. The expert owns billing
    // (SPEC 11.2.13); the domain forwards the tenant and DIRECT book
    // context on every call.
    // ------------------------------------------------------------------

    public BillingPlan findBillingPlan(UUID tenantId, UUID policyId) {
        ExpertBillingPlanView view = call("get billing plan", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/policies/{id}/billing")
                        .queryParam("tenantId", tenantId)
                        .build(policyId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .retrieve()
                .body(ExpertBillingPlanView.class));
        return toBillingPlan(view);
    }

    public BillingPlan createBillingPlan(UUID tenantId, UUID policyId,
                                         CreateBillingPlanCommand command) {
        ExpertBillingPlanView view = call("create billing plan", () -> client.post()
                .uri(uri -> uri.path("/internal/v1/policies/{id}/billing")
                        .queryParam("tenantId", tenantId)
                        .build(policyId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .body(command)
                .retrieve()
                .body(ExpertBillingPlanView.class));
        return toBillingPlan(view);
    }

    public Installment updateInstallment(UUID tenantId, UUID policyId, UUID installmentId,
                                         UpdateInstallmentCommand command) {
        ExpertInstallment installment = call("update billing installment", () -> client.patch()
                .uri(uri -> uri.path(
                                "/internal/v1/policies/{id}/billing/installments/{installmentId}")
                        .queryParam("tenantId", tenantId)
                        .build(policyId, installmentId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .body(command)
                .retrieve()
                .body(ExpertInstallment.class));
        if (installment == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return toInstallment(installment);
    }

    /**
     * Appends a positive premium adjustment installment. The expert derives the
     * amount from the policy premium less the plan total and returns the updated
     * plan, the appended installment, and the adjustment facts. The tenant and
     * DIRECT book context travel on every call; the optional {@code If-Match}
     * and {@code Idempotency-Key} headers are forwarded when supplied, and an
     * expert replay marker is preserved on the mutation result.
     */
    public Mutation<BillingAdjustmentResult> adjustBillingPlan(
            UUID tenantId, UUID policyId, BillingAdjustmentCommand command,
            String ifMatch, String idempotencyKey) {
        ResponseEntity<ExpertBillingAdjustmentView> response = callEntity(
                "adjust billing plan", () -> {
                    RestClient.RequestBodySpec spec = client.post()
                            .uri(uri -> uri.path(
                                            "/internal/v1/policies/{id}/billing/adjustment")
                                    .queryParam("tenantId", tenantId)
                                    .build(policyId))
                            .header(TENANT_HEADER, tenantId.toString())
                            .header(BookScope.BOOK_HEADER, BookScope.DIRECT);
                    if (ifMatch != null && !ifMatch.isBlank()) {
                        spec = spec.header(HttpHeaders.IF_MATCH, ifMatch);
                    }
                    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        spec = spec.header("Idempotency-Key", idempotencyKey);
                    }
                    return spec.body(command).retrieve()
                            .toEntity(ExpertBillingAdjustmentView.class);
                });
        if (response.getBody() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return Mutation.of(new ResponseEntity<>(toBillingAdjustment(response.getBody()),
                response.getHeaders(), response.getStatusCode()));
    }

    // ------------------------------------------------------------------
    // Mass tort profile (SPEC 11.2.11). The expert owns the tort catalog,
    // the trigger and allocation vocabulary, the coverage-block period,
    // and the profile state; the domain forwards the tenant and DIRECT
    // book context on every call.
    // ------------------------------------------------------------------

    public MassTortProfile findMassTortProfile(UUID tenantId, UUID policyId) {
        ExpertMassTortProfile view = call("get mass tort profile", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/policies/{id}/mass-tort-profile")
                        .queryParam("tenantId", tenantId)
                        .build(policyId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .retrieve()
                .body(ExpertMassTortProfile.class));
        return toMassTortProfile(view);
    }

    public MassTortProfile updateMassTortProfile(UUID tenantId, UUID policyId,
                                                 UpdateMassTortProfileCommand command) {
        ExpertMassTortProfile view = call("update mass tort profile", () -> client.put()
                .uri(uri -> uri.path("/internal/v1/policies/{id}/mass-tort-profile")
                        .queryParam("tenantId", tenantId)
                        .build(policyId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .body(command)
                .retrieve()
                .body(ExpertMassTortProfile.class));
        return toMassTortProfile(view);
    }

    private static MassTortProfile toMassTortProfile(ExpertMassTortProfile view) {
        if (view == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return new MassTortProfile(view.profileId(), view.policyId(), view.tortType(),
                view.triggerType(), view.allocationMethod(), view.allocationNotes(),
                view.coverageBlockStart(), view.coverageBlockEnd(), view.status(),
                view.updatedAt());
    }

    // ------------------------------------------------------------------
    // Program catalog and policy program membership (SPEC 10.11.3,
    // 11.2.16). The expert owns program numbering, the type/status
    // vocabularies, tenant-scoped uniqueness, and the membership rules;
    // the domain forwards the tenant and DIRECT book context on every
    // call.
    // ------------------------------------------------------------------

    public List<ProgramDto> findPrograms(UUID tenantId) {
        List<ExpertProgram> programs = call("list programs", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/programs")
                        .queryParam("tenantId", tenantId)
                        .build())
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ExpertProgram>>() {
                }));
        if (programs == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return programs.stream()
                .filter(Objects::nonNull)
                .map(PolicyClient::toProgram)
                .toList();
    }

    public ProgramDto findProgram(UUID tenantId, UUID programId) {
        ExpertProgram program = call("get program", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/programs/{programId}")
                        .queryParam("tenantId", tenantId)
                        .build(programId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .retrieve()
                .body(ExpertProgram.class));
        return toProgram(program);
    }

    public ProgramDto createProgram(UUID tenantId, CreateProgramCommand command) {
        ExpertProgram program = call("create program", () -> client.post()
                .uri(uri -> uri.path("/internal/v1/programs")
                        .queryParam("tenantId", tenantId)
                        .build())
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .body(command)
                .retrieve()
                .body(ExpertProgram.class));
        return toProgram(program);
    }

    /**
     * Assigns the policy to a program, or clears the assignment when
     * {@code programId} is null. The updated policy carries the new
     * {@code programId} and the expert's aggregate ETag.
     */
    public Mutation<PolicyDto> updatePolicyProgram(UUID tenantId, UUID policyId,
                                                   UUID programId) {
        ResponseEntity<PolicyDto> response = callEntity("assign policy program", () -> client.patch()
                .uri(uri -> uri.path("/internal/v1/policies/{policyId}/program")
                        .queryParam("tenantId", tenantId)
                        .build(policyId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .body(new ExpertPolicyProgramRequest(programId))
                .retrieve()
                .toEntity(PolicyDto.class));
        if (response.getBody() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return Mutation.of(response);
    }

    private static ProgramDto toProgram(ExpertProgram view) {
        if (view == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return new ProgramDto(view.programId(), view.programNumber(), view.name(),
                view.programType(), view.effectiveFrom(), view.effectiveTo(), view.status(),
                view.leadPolicyId(), view.capacityAmount(), view.capacityCurrency(),
                view.usedAmount(), view.utilizationPct(), view.policyCount(), view.createdAt());
    }

    // ------------------------------------------------------------------
    // Legal entity catalog and issuing-legal-entity membership (SPEC
    // 10.11.2, 11.2.16). The expert owns the entity code, the entity-type
    // and status vocabularies, tenant-scoped uniqueness, and the license
    // rules; the domain forwards the tenant and DIRECT book context on
    // every call.
    // ------------------------------------------------------------------

    public List<LegalEntityDto> findLegalEntities(UUID tenantId) {
        List<ExpertLegalEntity> legalEntities = call("list legal entities", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/legal-entities")
                        .queryParam("tenantId", tenantId)
                        .build())
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ExpertLegalEntity>>() {
                }));
        if (legalEntities == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return legalEntities.stream()
                .filter(Objects::nonNull)
                .map(PolicyClient::toLegalEntity)
                .toList();
    }

    public LegalEntityDto findLegalEntity(UUID tenantId, UUID legalEntityId) {
        ExpertLegalEntity legalEntity = call("get legal entity", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/legal-entities/{legalEntityId}")
                        .queryParam("tenantId", tenantId)
                        .build(legalEntityId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .retrieve()
                .body(ExpertLegalEntity.class));
        return toLegalEntity(legalEntity);
    }

    public LegalEntityDto createLegalEntity(UUID tenantId, CreateLegalEntityCommand command) {
        ExpertLegalEntity legalEntity = call("create legal entity", () -> client.post()
                .uri(uri -> uri.path("/internal/v1/legal-entities")
                        .queryParam("tenantId", tenantId)
                        .build())
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .body(command)
                .retrieve()
                .body(ExpertLegalEntity.class));
        return toLegalEntity(legalEntity);
    }

    /**
     * Assigns the policy's issuing legal entity. SPEC LE-01 requires exactly one
     * issuer, so the expert rejects a null member; the updated policy carries
     * the new {@code legalEntityId} and the expert's aggregate ETag.
     */
    public Mutation<PolicyDto> updatePolicyLegalEntity(UUID tenantId, UUID policyId,
                                                       UUID legalEntityId) {
        ResponseEntity<PolicyDto> response = callEntity("assign policy legal entity",
                () -> client.patch()
                        .uri(uri -> uri.path("/internal/v1/policies/{policyId}/legal-entity")
                                .queryParam("tenantId", tenantId)
                                .build(policyId))
                        .header(TENANT_HEADER, tenantId.toString())
                        .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                        .body(new ExpertPolicyLegalEntityRequest(legalEntityId))
                        .retrieve()
                        .toEntity(PolicyDto.class));
        if (response.getBody() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return Mutation.of(response);
    }

    private static LegalEntityDto toLegalEntity(ExpertLegalEntity view) {
        if (view == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return new LegalEntityDto(view.legalEntityId(), view.code(), view.name(),
                view.entityType(), view.jurisdiction(), view.regulator(), view.licenseNumber(),
                view.status(), view.createdAt());
    }

    // ------------------------------------------------------------------
    // Premium receivables: the cross-policy installment view that backs a
    // collections/ageing screen (SPEC 11.2.13). The expert owns the
    // projection and the tenant wall; the domain forwards the tenant and
    // the DIRECT book context on every read.
    // ------------------------------------------------------------------

    /**
     * Lists the tenant's premium receivables, optionally narrowed by effective
     * installment {@code status} and/or a due date on or before
     * {@code dueBefore}. The expert applies the filters, ordering, and limit;
     * the domain normalizes the request shape and maps the wire projection.
     */
    public ReceivableSummary findReceivables(UUID tenantId, String status, LocalDate dueBefore,
                                             int limit) {
        ExpertReceivableSummary view = call("list premium receivables", () -> client.get()
                .uri(uri -> {
                    uri.path("/internal/v1/billing/installments")
                            .queryParam("tenantId", tenantId);
                    if (status != null) {
                        uri.queryParam("status", status);
                    }
                    if (dueBefore != null) {
                        uri.queryParam("dueBefore", dueBefore);
                    }
                    uri.queryParam("limit", limit);
                    return uri.build();
                })
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .retrieve()
                .body(ExpertReceivableSummary.class));
        return toReceivableSummary(view);
    }

    /**
     * The expert returns the receivables with their totals. Totals, the count,
     * and {@code daysPastDue} are recomputed only when the expert omits them,
     * so an older projection still produces a coherent public summary. The
     * fallback totals use each receivable's outstanding amount, so applied
     * credits reduce the collectible balance.
     */
    private static ReceivableSummary toReceivableSummary(ExpertReceivableSummary view) {
        if (view == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        List<Receivable> items = view.items() == null ? List.of()
                : view.items().stream()
                        .filter(Objects::nonNull)
                        .map(PolicyClient::toReceivable)
                        .toList();
        BigDecimal totalAmount = view.totalAmount() == null
                ? sum(items, false) : view.totalAmount();
        BigDecimal overdueAmount = view.overdueAmount() == null
                ? sum(items, true) : view.overdueAmount();
        int count = view.count() == null ? items.size() : view.count();
        return new ReceivableSummary(items, totalAmount, overdueAmount, count);
    }

    private static Receivable toReceivable(ExpertReceivable receivable) {
        long daysPastDue = receivable.daysPastDue() == null
                ? daysPastDue(receivable.dueDate(), receivable.status())
                : receivable.daysPastDue();
        return new Receivable(receivable.installmentId(), receivable.planId(),
                receivable.planNumber(), receivable.policyId(), receivable.policyNumber(),
                receivable.insuredPartyId(), receivable.installmentNumber(), receivable.dueDate(),
                receivable.amount(), receivable.currency(), receivable.status(), daysPastDue,
                receivable.paidAt(), receivable.paymentId(), receivable.lastDunningLevel(),
                receivable.lastDunningAt(), receivable.creditedAmount(),
                receivable.outstandingAmount());
    }

    private static BigDecimal sum(List<Receivable> items, boolean overdueOnly) {
        return items.stream()
                .filter(item -> !overdueOnly || isOverdue(item))
                .map(Receivable::outstandingAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isOverdue(Receivable receivable) {
        if ("PAID".equalsIgnoreCase(receivable.status())
                || "WAIVED".equalsIgnoreCase(receivable.status())) {
            return false;
        }
        return "OVERDUE".equalsIgnoreCase(receivable.status())
                || receivable.daysPastDue() > 0;
    }

    /**
     * Ageing fallback for an expert projection that predates
     * {@code daysPastDue}: settled installments and future due dates are not
     * past due, everything else counts whole days from the due date.
     */
    static long daysPastDue(LocalDate dueDate, String status) {
        if (dueDate == null || "PAID".equalsIgnoreCase(status)
                || "WAIVED".equalsIgnoreCase(status)) {
            return 0L;
        }
        long days = ChronoUnit.DAYS.between(dueDate, LocalDate.now(ZoneOffset.UTC));
        return Math.max(days, 0L);
    }

    // ------------------------------------------------------------------
    // Installment dunning (SPEC 11.2.13 collections and dunning). The
    // expert owns the level/channel vocabulary, the transition rules, and
    // the action history; the domain forwards the tenant and DIRECT book
    // context on every call.
    // ------------------------------------------------------------------

    /** Reads the installment's dunning history, newest first. */
    public List<DunningAction> findDunningActions(UUID tenantId, UUID installmentId) {
        List<ExpertDunningAction> history = call("list billing dunning actions", () -> client.get()
                .uri(uri -> uri.path(
                                "/internal/v1/billing/installments/{installmentId}/dunning")
                        .queryParam("tenantId", tenantId)
                        .build(installmentId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ExpertDunningAction>>() {
                }));
        if (history == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return history.stream()
                .filter(Objects::nonNull)
                .map(PolicyClient::toDunningAction)
                .toList();
    }

    /** Records one dunning action against an installment. */
    public DunningAction recordDunningAction(UUID tenantId, UUID installmentId,
                                             CreateDunningActionCommand command) {
        ExpertDunningAction view = call("record billing dunning action", () -> client.post()
                .uri(uri -> uri.path(
                                "/internal/v1/billing/installments/{installmentId}/dunning")
                        .queryParam("tenantId", tenantId)
                        .build(installmentId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .body(command)
                .retrieve()
                .body(ExpertDunningAction.class));
        return toDunningAction(view);
    }

    private static DunningAction toDunningAction(ExpertDunningAction view) {
        if (view == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return new DunningAction(view.dunningId(), view.installmentId(), view.planId(),
                view.policyId(), view.level(), view.channel(), view.notes(), view.status(),
                view.sentAt());
    }

    // ------------------------------------------------------------------
    // Policy credit notes (SPEC 11.2.13 billing and receivables). The
    // expert owns the note number, the DRAFT/ISSUED/APPLIED/CANCELLED/
    // REVERSED lifecycle, the policy-currency match, the approval gate,
    // the credit release on reversal, and the SAP credit-memo reference;
    // the domain forwards the tenant and DIRECT book context on every call.
    // ------------------------------------------------------------------

    /** Lists the policy's credit notes, newest first. */
    public List<CreditNote> findCreditNotes(UUID tenantId, UUID policyId) {
        List<ExpertCreditNote> notes = call("list credit notes", () -> client.get()
                .uri(uri -> uri.path("/internal/v1/policies/{id}/credit-notes")
                        .queryParam("tenantId", tenantId)
                        .build(policyId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ExpertCreditNote>>() {
                }));
        if (notes == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return notes.stream()
                .filter(Objects::nonNull)
                .map(PolicyClient::toCreditNote)
                .toList();
    }

    /** Requests one credit note against the policy, optionally linked to a cancellation. */
    public CreditNote createCreditNote(UUID tenantId, UUID policyId,
                                       CreateCreditNoteCommand command) {
        ExpertCreditNote view = call("create credit note", () -> client.post()
                .uri(uri -> uri.path("/internal/v1/policies/{id}/credit-notes")
                        .queryParam("tenantId", tenantId)
                        .build(policyId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .body(command)
                .retrieve()
                .body(ExpertCreditNote.class));
        return toCreditNote(view);
    }

    /**
     * Advances one credit note through the expert-owned status lifecycle. The
     * optional installment is forwarded only for an {@code APPLIED} transition
     * and the optional reason only for a {@code REVERSED} transition; every
     * other status keeps the pre-application payload unchanged.
     */
    public CreditNote updateCreditNoteStatus(UUID tenantId, UUID policyId, UUID creditNoteId,
                                             UpdateCreditNoteStatusCommand command) {
        ExpertCreditNote view = call("update credit note status", () -> client.patch()
                .uri(uri -> uri.path(
                                "/internal/v1/policies/{id}/credit-notes/{creditNoteId}/status")
                        .queryParam("tenantId", tenantId)
                        .build(policyId, creditNoteId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .body(new ExpertCreditNoteStatusRequest(command.status(),
                        isApplied(command.status()) ? command.installmentId() : null,
                        isReversed(command.status()) ? command.reason() : null))
                .retrieve()
                .body(ExpertCreditNote.class));
        return toCreditNote(view);
    }

    private static boolean isApplied(String status) {
        return status != null && "APPLIED".equalsIgnoreCase(status.trim());
    }

    private static boolean isReversed(String status) {
        return status != null && "REVERSED".equalsIgnoreCase(status.trim());
    }

    private static CreditNote toCreditNote(ExpertCreditNote view) {
        if (view == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return new CreditNote(view.creditNoteId(), view.noteNumber(), view.policyId(),
                view.amount(), view.currency(), view.reason(), view.status(),
                view.issuedAt(), view.appliedAt(), view.appliedInstallmentId(), view.createdAt(),
                view.reversedAt(), view.reversalReason(), view.cancellationId(),
                view.cancellationNumber());
    }

    /**
     * The expert returns the plan under {@code plan}, the ordered installment
     * list, and (when present) the plan summary. The public projection is flat
     * and always carries a summary, derived from the rows when the expert omits
     * it so an older projection still produces a coherent reading.
     */
    private static BillingPlan toBillingPlan(ExpertBillingPlanView view) {
        if (view == null || view.plan() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        ExpertBillingPlan plan = view.plan();
        List<Installment> installments = view.installments() == null ? List.of()
                : view.installments().stream().map(PolicyClient::toInstallment).toList();
        BillingSummary summary = toBillingSummary(view.summary(), plan, installments);
        return new BillingPlan(plan.id(), plan.planNumber(), plan.policyId(),
                plan.totalPremium(), plan.currency(), plan.installmentCount(), plan.frequency(),
                plan.firstDueDate(), plan.status(), installments, summary);
    }

    private static Installment toInstallment(ExpertInstallment installment) {
        return new Installment(installment.id(), installment.installmentNumber(),
                installment.dueDate(), installment.amount(), installment.status(),
                installment.paidAt(), installment.paymentId(), installment.creditedAmount(),
                installment.outstandingAmount());
    }

    /**
     * The adjustment response repeats the plan without its schedule: the updated
     * plan totals travel under {@code plan}, the appended row travels separately
     * under {@code installment}. The public plan projection keeps its flat shape
     * and publishes no schedule rows for this envelope, so the plan totals stay
     * authoritative without inventing a partial installment list.
     */
    private static BillingAdjustmentResult toBillingAdjustment(
            ExpertBillingAdjustmentView view) {
        if (view == null || view.plan() == null || view.installment() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        ExpertBillingPlan plan = view.plan();
        BillingPlan projectedPlan = new BillingPlan(plan.id(), plan.planNumber(),
                plan.policyId(), plan.totalPremium(), plan.currency(), plan.installmentCount(),
                plan.frequency(), plan.firstDueDate(), plan.status(), List.of(), null);
        BigDecimal newTotalPremium = view.newTotalPremium() == null
                ? plan.totalPremium() : view.newTotalPremium();
        return new BillingAdjustmentResult(projectedPlan, toInstallment(view.installment()),
                view.adjustmentAmount(), newTotalPremium);
    }

    /**
     * Merges the expert summary with the derived one. An older expert that only
     * returns the plan and installments still gets a complete summary, while a
     * supplied total/paid/credited/outstanding value wins over the derivation.
     */
    private static BillingSummary toBillingSummary(ExpertBillingSummary view,
                                                   ExpertBillingPlan plan,
                                                   List<Installment> installments) {
        BillingSummary derived = BillingSummary.of(plan.totalPremium(), plan.currency(),
                installments);
        if (view == null) {
            return derived;
        }
        BigDecimal totalAmount = view.totalAmount() == null
                ? derived.totalAmount() : view.totalAmount();
        BigDecimal paid = view.paidAmount() == null ? derived.paidAmount() : view.paidAmount();
        BigDecimal waived = view.waivedAmount() == null
                ? derived.waivedAmount() : view.waivedAmount();
        BigDecimal credited = view.creditedAmount() == null
                ? derived.creditedAmount() : view.creditedAmount();
        BigDecimal outstanding = view.outstandingAmount() == null
                ? derived.outstandingAmount() : view.outstandingAmount();
        BigDecimal overdue = view.overdueAmount() == null
                ? derived.overdueAmount() : view.overdueAmount();
        String currency = view.currency() == null || view.currency().isBlank()
                ? derived.currency() : view.currency();
        int installmentCount = view.installmentCount() == null
                ? derived.installmentCount() : view.installmentCount();
        return new BillingSummary(totalAmount, paid, waived, credited, outstanding, overdue,
                currency, installmentCount);
    }

    private <T> ExpertItemPage<T> list(String operation, String path, UUID tenantId, UUID policyId,
                                       int page, int size,
                                       ParameterizedTypeReference<ExpertItemPage<T>> type) {
        ExpertItemPage<T> result = call(operation, () -> client.get()
                .uri(uri -> uri.path(path)
                        .queryParam("tenantId", tenantId)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build(policyId))
                .retrieve()
                .body(type));
        if (result == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return result;
    }

    private <T> Mutation<T> mutate(String operation, String path, UUID tenantId, UUID policyId,
                                   Object body, String ifMatch, String idempotencyKey,
                                   Class<T> type) {
        ResponseEntity<T> response = callEntity(operation, () -> {
            RestClient.RequestBodySpec spec = client.post()
                    .uri(uri -> uri.path(path)
                            .queryParam("tenantId", tenantId)
                            .build(policyId));
            if (ifMatch != null && !ifMatch.isBlank()) {
                spec = spec.header(HttpHeaders.IF_MATCH, ifMatch);
            }
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                spec = spec.header("Idempotency-Key", idempotencyKey);
            }
            return spec.body(body).retrieve().toEntity(type);
        });
        if (response.getBody() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return Mutation.of(response);
    }

    /**
     * A mutation on a DIRECT-book route: the tenant is carried both as the
     * query scope used by the internal API and as the canonical context header,
     * together with the book header.
     */
    private <T> Mutation<T> mutateDirect(String operation, String path, UUID tenantId,
                                         UUID policyId, Object body, Class<T> type) {
        ResponseEntity<T> response = callEntity(operation, () -> client.post()
                .uri(uri -> uri.path(path)
                        .queryParam("tenantId", tenantId)
                        .build(policyId))
                .header(TENANT_HEADER, tenantId.toString())
                .header(BookScope.BOOK_HEADER, BookScope.DIRECT)
                .body(body)
                .retrieve()
                .toEntity(type));
        if (response.getBody() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_BAD_RESPONSE",
                    "Policy expert returned an empty response");
        }
        return Mutation.of(response);
    }

    private <T> ResponseEntity<T> callEntity(String operation,
                                             Supplier<ResponseEntity<T>> invocation) {
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
            String code = expertCode == null || expertCode.isBlank()
                    ? "POLICY_NOT_FOUND" : expertCode;
            return new ApiException(HttpStatus.NOT_FOUND, code, message, issues, meta);
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ExpertPremiumAuditRequest(
            BigDecimal auditedPremium,
            LocalDate effectiveDate,
            String reason,
            UUID approvedBy,
            UUID approvalRef
    ) {
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

    /**
     * Wire shape of the expert billing view: the plan, its ordered
     * installments, and the plan summary are separate members, unlike the flat
     * public projection. Older experts omit the summary.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertBillingPlanView(ExpertBillingPlan plan, List<ExpertInstallment> installments,
                                 ExpertBillingSummary summary) {
    }

    /**
     * Wire shape of the expert positive-adjustment view: the updated plan, the
     * appended installment, the derived adjustment amount, and the resulting
     * plan total.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertBillingAdjustmentView(
            ExpertBillingPlan plan,
            ExpertInstallment installment,
            BigDecimal adjustmentAmount,
            BigDecimal newTotalPremium
    ) {
    }

    /**
     * Wire shape of the expert billing summary. The aliases accept the plan
     * vocabulary differences while the internal expert contract settles; every
     * member is optional and the domain derives what is missing.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertBillingSummary(
            @JsonAlias({"totalPremium", "totalPremiumAmount"}) BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal waivedAmount,
            BigDecimal creditedAmount,
            BigDecimal outstandingAmount,
            BigDecimal overdueAmount,
            String currency,
            Integer installmentCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertBillingPlan(
            @JsonAlias("planId") UUID id,
            UUID policyId,
            String planNumber,
            BigDecimal totalPremium,
            String currency,
            int installmentCount,
            String frequency,
            LocalDate firstDueDate,
            String status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertInstallment(
            @JsonAlias("installmentId") UUID id,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal amount,
            String status,
            Instant paidAt,
            UUID paymentId,
            @JsonAlias("credited_amount") BigDecimal creditedAmount,
            @JsonAlias("outstanding_amount") BigDecimal outstandingAmount
    ) {
    }

    /**
     * Wire shape of the expert mass tort profile. The public projection is
     * flat; the aliases accept the SPEC column names and the expert's
     * {@code id} identity while the internal expert contract settles.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertMassTortProfile(
            @JsonAlias({"id", "massTortProfileId"}) UUID profileId,
            UUID policyId,
            @JsonAlias({"directTortCode", "direct_tort_code", "tortCode"}) String tortType,
            @JsonAlias({"triggerTheory", "trigger_theory"}) String triggerType,
            @JsonAlias("allocation_method") String allocationMethod,
            @JsonAlias("allocation_notes") String allocationNotes,
            @JsonAlias("coverage_block_start") LocalDate coverageBlockStart,
            @JsonAlias("coverage_block_end") LocalDate coverageBlockEnd,
            String status,
            @JsonAlias("updated_at") Instant updatedAt
    ) {
    }

    /**
     * Wire shape of one expert program. The public projection is flat; the
     * aliases accept the SPEC column names, the expert's {@code id} identity,
     * and the snake-case capacity columns while the internal expert contract
     * settles. The capacity aggregates are optional so an expert that does not
     * derive them still returns a usable program.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertProgram(
            @JsonAlias({"id", "program_id"}) UUID programId,
            @JsonAlias({"code", "programCode", "program_code"}) String programNumber,
            @JsonAlias("program_name") String name,
            @JsonAlias({"type", "program_type"}) String programType,
            @JsonAlias({"effective_from", "startDate"}) LocalDate effectiveFrom,
            @JsonAlias({"effective_to", "expirationDate", "endDate"}) LocalDate effectiveTo,
            @JsonAlias("program_status") String status,
            @JsonAlias("lead_policy_id") UUID leadPolicyId,
            @JsonAlias("capacity_amount") BigDecimal capacityAmount,
            @JsonAlias("capacity_currency") String capacityCurrency,
            @JsonAlias("used_amount") BigDecimal usedAmount,
            @JsonAlias({"utilization_pct", "utilization_percentage"}) BigDecimal utilizationPct,
            @JsonAlias("policy_count") Integer policyCount,
            @JsonAlias("created_at") Instant createdAt
    ) {
    }

    /** Wire shape of the policy program assignment command. */
    record ExpertPolicyProgramRequest(UUID programId) {
    }

    /**
     * Wire shape of the expert premium-receivable summary. The expert may omit
     * the derived members; the domain then recomputes them from the items.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertReceivableSummary(
            List<ExpertReceivable> items,
            BigDecimal totalAmount,
            BigDecimal overdueAmount,
            Integer count
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertReceivable(
            @JsonAlias("id") UUID installmentId,
            UUID planId,
            String planNumber,
            UUID policyId,
            String policyNumber,
            UUID insuredPartyId,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal amount,
            String currency,
            String status,
            Long daysPastDue,
            Instant paidAt,
            UUID paymentId,
            @JsonAlias("last_dunning_level") String lastDunningLevel,
            @JsonAlias("last_dunning_at") Instant lastDunningAt,
            @JsonAlias("credited_amount") BigDecimal creditedAmount,
            @JsonAlias("outstanding_amount") BigDecimal outstandingAmount
    ) {
    }

    /**
     * Wire shape of one expert dunning action. The expert's row id is accepted
     * as {@code id} as well as the public {@code dunningId} name.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertDunningAction(
            @JsonAlias("id") UUID dunningId,
            UUID installmentId,
            UUID planId,
            UUID policyId,
            String level,
            String channel,
            String notes,
            String status,
            Instant sentAt
    ) {
    }

    /**
     * Wire shape of the credit-note status command. {@code installmentId} is
     * included only for an {@code APPLIED} transition and {@code reason} only
     * for a {@code REVERSED} transition, so an older expert that does not know
     * the new member still receives the unchanged command for ISSUED and
     * CANCELLED.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ExpertCreditNoteStatusRequest(String status, UUID installmentId, String reason) {
    }

    /**
     * Wire shape of one expert credit note. The public projection is flat; the
     * aliases accept the expert's {@code id} identity and the SPEC column names
     * while the internal expert contract settles. A reversed note keeps its
     * application timestamps and installment and adds the reversal stamp and
     * reason. A cancellation-linked note carries the cancellation id and its
     * human-readable number; both are absent for every other note.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertCreditNote(
            @JsonAlias({"id", "credit_note_id"}) UUID creditNoteId,
            @JsonAlias({"note_number", "creditNoteNumber"}) String noteNumber,
            @JsonAlias("policy_id") UUID policyId,
            BigDecimal amount,
            String currency,
            String reason,
            String status,
            @JsonAlias("issued_at") Instant issuedAt,
            @JsonAlias("applied_at") Instant appliedAt,
            @JsonAlias("created_at") Instant createdAt,
            @JsonAlias({"applied_installment_id", "installmentId"}) UUID appliedInstallmentId,
            @JsonAlias("reversed_at") Instant reversedAt,
            @JsonAlias("reversal_reason") String reversalReason,
            @JsonAlias("cancellation_id") UUID cancellationId,
            @JsonAlias("cancellation_number") String cancellationNumber
    ) {
    }

    /**
     * Wire shape of one expert legal entity. The public projection is flat; the
     * aliases accept the expert's {@code id} identity and the SPEC column names
     * while the internal expert contract settles.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertLegalEntity(
            @JsonAlias({"id", "legal_entity_id"}) UUID legalEntityId,
            @JsonAlias({"entity_code", "entityCode"}) String code,
            @JsonAlias({"legal_name", "legalName"}) String name,
            @JsonAlias("entity_type") String entityType,
            @JsonAlias({"domicile_country", "domicileCountry"}) String jurisdiction,
            String regulator,
            @JsonAlias("license_number") String licenseNumber,
            @JsonAlias({"entity_status", "legal_entity_status"}) String status,
            @JsonAlias("created_at") Instant createdAt
    ) {
    }

    /** Wire shape of the policy legal entity assignment command. */
    record ExpertPolicyLegalEntityRequest(UUID legalEntityId) {
    }
}
