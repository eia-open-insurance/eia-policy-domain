package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Public domain facade for the second Policy slice. It validates page bounds,
 * the optional {@code If-Match} tag, and the idempotency key before delegating
 * to the expert, then rebuilds the {@code _links} for the public base path.
 * Business rules stay in the expert so both services enforce one rule set.
 */
@Service
public class PolicyContractService {
    private final PolicyClient client;

    public PolicyContractService(PolicyClient client) {
        this.client = client;
    }

    public ItemPage<PolicyVersionDto> listVersions(UUID tenantId, UUID policyId, int page, int size) {
        Pagination.validate(page, size);
        return page("/api/v1/policies/" + policyId + "/versions",
                client.findVersions(tenantId, policyId, page, size));
    }

    public CurrentVersionDto currentVersion(UUID tenantId, UUID policyId) {
        return client.findCurrentVersion(tenantId, policyId);
    }

    public PolicyVersionDto version(UUID tenantId, UUID policyId, UUID versionId) {
        return client.findVersion(tenantId, policyId, versionId);
    }

    public ItemPage<CoverageViewDto> listCoverages(UUID tenantId, UUID policyId, int page,
                                                   int size) {
        Pagination.validate(page, size);
        return page("/api/v1/policies/" + policyId + "/coverages",
                client.findCoverages(tenantId, policyId, page, size));
    }

    public Mutation<CoverageViewDto> createCoverage(UUID tenantId, UUID policyId,
                                                    CreateCoverageCommand command, String ifMatch,
                                                    String idempotencyKey) {
        EntityTags.parse(ifMatch);
        return client.createCoverage(tenantId, policyId, command, ifMatch,
                IdempotencyKeys.normalize(idempotencyKey));
    }

    public ItemPage<LimitDto> listLimits(UUID tenantId, UUID policyId, int page, int size) {
        Pagination.validate(page, size);
        return page("/api/v1/policies/" + policyId + "/limits",
                client.findLimits(tenantId, policyId, page, size));
    }

    public ItemPage<LimitStateDto> aggregateState(UUID tenantId, UUID policyId, int page, int size) {
        Pagination.validate(page, size);
        return page("/api/v1/policies/" + policyId + "/aggregate-state",
                client.findAggregateState(tenantId, policyId, page, size));
    }

    public Mutation<LimitStateDto> applyErosion(UUID tenantId, UUID policyId, ErosionCommand command,
                                                String idempotencyKey) {
        return client.applyErosion(tenantId, policyId, command,
                IdempotencyKeys.normalize(idempotencyKey));
    }

    public ItemPage<EndorsementDto> listEndorsements(UUID tenantId, UUID policyId, int page,
                                                      int size) {
        Pagination.validate(page, size);
        return page("/api/v1/policies/" + policyId + "/endorsements",
                client.findEndorsements(tenantId, policyId, page, size));
    }

    public EndorsementDto endorsement(UUID tenantId, UUID policyId, UUID endorsementId) {
        return client.findEndorsement(tenantId, policyId, endorsementId);
    }

    public Mutation<EndorsementDto> createEndorsement(UUID tenantId, UUID policyId,
                                                      CreateEndorsementCommand command,
                                                      String ifMatch, String idempotencyKey) {
        EntityTags.parse(ifMatch);
        return client.createEndorsement(tenantId, policyId, command, ifMatch,
                IdempotencyKeys.normalize(idempotencyKey));
    }

    public ItemPage<InsuredDto> listInsureds(UUID tenantId, UUID policyId, int page, int size) {
        Pagination.validate(page, size);
        return page("/api/v1/policies/" + policyId + "/insureds",
                client.findInsureds(tenantId, policyId, page, size));
    }

    public Mutation<InsuredDto> createInsured(UUID tenantId, UUID policyId,
                                              CreateInsuredCommand command, String ifMatch,
                                              String idempotencyKey) {
        EntityTags.parse(ifMatch);
        return client.createInsured(tenantId, policyId, command, ifMatch,
                IdempotencyKeys.normalize(idempotencyKey));
    }

    public ItemPage<LocationDto> listLocations(UUID tenantId, UUID policyId, int page, int size) {
        Pagination.validate(page, size);
        return page("/api/v1/policies/" + policyId + "/locations",
                client.findLocations(tenantId, policyId, page, size));
    }

    public Mutation<LocationDto> createLocation(UUID tenantId, UUID policyId,
                                                CreateLocationCommand command, String ifMatch,
                                                String idempotencyKey) {
        EntityTags.parse(ifMatch);
        return client.createLocation(tenantId, policyId, command, ifMatch,
                IdempotencyKeys.normalize(idempotencyKey));
    }

    public ItemPage<CancellationDto> listCancellations(UUID tenantId, UUID policyId, int page,
                                                        int size) {
        Pagination.validate(page, size);
        return page("/api/v1/policies/" + policyId + "/cancellations",
                client.findCancellations(tenantId, policyId, page, size));
    }

    public Mutation<CancellationDto> cancelPolicy(UUID tenantId, UUID policyId,
                                                  CancelPolicyCommand command, String ifMatch,
                                                  String idempotencyKey) {
        EntityTags.parse(ifMatch);
        return client.cancelPolicy(tenantId, policyId, command, ifMatch,
                IdempotencyKeys.normalize(idempotencyKey));
    }

    public ItemPage<ReinstatementDto> listReinstatements(UUID tenantId, UUID policyId, int page,
                                                          int size) {
        Pagination.validate(page, size);
        return page("/api/v1/policies/" + policyId + "/reinstatements",
                client.findReinstatements(tenantId, policyId, page, size));
    }

    public Mutation<ReinstatementDto> reinstatePolicy(UUID tenantId, UUID policyId,
                                                      ReinstatePolicyCommand command, String ifMatch,
                                                      String idempotencyKey) {
        EntityTags.parse(ifMatch);
        return client.reinstatePolicy(tenantId, policyId, command, ifMatch,
                IdempotencyKeys.normalize(idempotencyKey));
    }

    private static <T> ItemPage<T> page(String basePath, ExpertItemPage<T> expertPage) {
        return new ItemPage<>(expertPage.items(), expertPage.page(), expertPage.size(),
                expertPage.total(), PageLinks.buildItem(basePath, Map.of(), expertPage.page(),
                expertPage.size(), expertPage.total()));
    }
}
