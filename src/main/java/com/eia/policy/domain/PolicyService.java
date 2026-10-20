package com.eia.policy.domain;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PolicyService {
    private final PolicyClient client;
    private final PolicyQueryRepository queryRepository;

    public PolicyService(PolicyClient client, PolicyQueryRepository queryRepository) {
        this.client = client;
        this.queryRepository = queryRepository;
    }

    /**
     * Compatibility overload for callers that predate pagination. New callers
     * should use the page/size form.
     */
    public List<PolicyDto> list(UUID tenantId) {
        return client.findAll(tenantId);
    }

    public PolicyPage list(UUID tenantId, int page, int size) {
        return list(tenantId, page, size, null);
    }

    /**
     * Lists tenant-scoped policies, optionally filtered by insured party. The
     * unfiltered read keeps proxying the Policy expert; the expert exposes no
     * insured-party filter, so the filtered read uses the tenant-scoped
     * {@link PolicyQueryRepository} with the same ordering and page envelope.
     * The filter is carried into the {@code _links} so paging never widens the
     * result set.
     */
    public PolicyPage list(UUID tenantId, int page, int size, UUID insuredPartyId) {
        Pagination.validate(page, size);
        if (insuredPartyId == null) {
            ExpertPolicyPage expertPage = client.findAll(tenantId, page, size);
            return page(expertPage.items(), page, size, expertPage.total(), Map.of());
        }
        PolicyPageData data = queryRepository.findPageByInsuredParty(
                tenantId, insuredPartyId, page, size);
        return page(data.items(), page, size, data.total(),
                PageLinks.filters("insuredPartyId", insuredPartyId.toString()));
    }

    private static PolicyPage page(List<PolicyDto> items, int page, int size, long total,
                                  Map<String, String> filters) {
        return new PolicyPage(items, page, size, total,
                PageLinks.build("/api/v1/policies", filters, page, size, total));
    }

    public PolicyDto get(UUID tenantId, UUID id) {
        return client.findById(tenantId, id);
    }

    /** Compatibility overload: creates without an idempotency scope. */
    public PolicyDto create(UUID tenantId, CreatePolicyCommand command) {
        return create(tenantId, command, null).policy();
    }

    public CreateOutcome create(UUID tenantId, CreatePolicyCommand command, String idempotencyKey) {
        validateCreate(command.effectiveDate(), command.expirationDate(),
                command.premiumAmount(), command.limitAmount());
        return client.create(tenantId, command, IdempotencyKeys.normalize(idempotencyKey));
    }

    /** Compatibility overload: uses the body {@code expectedVersion} only. */
    public PolicyDto updateStatus(UUID tenantId, UUID id, UpdatePolicyStatusCommand command) {
        return updateStatus(tenantId, id, command, null);
    }

    /**
     * Applies one lifecycle transition with optimistic concurrency. A present
     * {@code If-Match} header takes precedence over the compatibility
     * {@code expectedVersion} body field; a malformed header is a 400 and a
     * stale version is a 409 problem.
     */
    public PolicyDto updateStatus(UUID tenantId, UUID id, UpdatePolicyStatusCommand command,
                                  String ifMatch) {
        Integer headerVersion = EntityTags.parse(ifMatch);
        PolicyDto current = client.findById(tenantId, id);

        if (headerVersion != null && headerVersion != EntityTags.ANY
                && headerVersion != current.version()) {
            throw conflict(current.version(), headerVersion, "If-Match");
        }

        String targetStatus = PolicyLifecycle.validateTransition(current.status(), command.status());

        if (headerVersion == null && command.expectedVersion() != null
                && command.expectedVersion() != current.version()) {
            throw conflict(current.version(), command.expectedVersion(), "expectedVersion");
        }

        int expectedVersion;
        if (headerVersion == null) {
            expectedVersion = command.expectedVersion() == null
                    ? current.version()
                    : command.expectedVersion();
        } else {
            expectedVersion = headerVersion == EntityTags.ANY ? current.version() : headerVersion;
        }

        return client.updateStatus(tenantId, id, targetStatus, expectedVersion, command.reason(), ifMatch);
    }

    private static ApiException conflict(int currentVersion, int expectedVersion, String field) {
        String message = "Expected version " + expectedVersion + " but policy is at version "
                + currentVersion + "; reload and retry";
        return new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", message,
                List.of(new ApiException.FieldIssue(field, "STALE_VERSION", message)),
                Map.of("currentVersion", currentVersion, "expectedVersion", expectedVersion));
    }

    private static void validateCreate(LocalDate effectiveDate, LocalDate expirationDate,
                                       BigDecimal premiumAmount, BigDecimal limitAmount) {
        List<ApiException.FieldIssue> issues = new ArrayList<>();
        if (effectiveDate != null && expirationDate != null && effectiveDate.isAfter(expirationDate)) {
            issues.add(new ApiException.FieldIssue("expirationDate", "INVALID_DATE_RANGE",
                    "expirationDate must be on or after effectiveDate"));
        }
        if (premiumAmount != null && premiumAmount.signum() < 0) {
            issues.add(new ApiException.FieldIssue("premiumAmount", "MIN_VALUE",
                    "premiumAmount must be non-negative"));
        }
        if (limitAmount != null && limitAmount.signum() < 0) {
            issues.add(new ApiException.FieldIssue("limitAmount", "MIN_VALUE",
                    "limitAmount must be non-negative"));
        }
        if (!issues.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Policy validation failed", issues);
        }
    }
}
