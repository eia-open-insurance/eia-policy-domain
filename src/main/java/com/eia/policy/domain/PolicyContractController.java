package com.eia.policy.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public second-slice Policy API: immutable versions, coverage/limit schedules
 * with aggregate erosion, endorsements, insureds and locations,
 * cancellation/reinstatement, and renewal into a successor policy. Mutations
 * echo the strong aggregate ETag and the {@code Idempotent-Replayed} marker
 * from the expert.
 */
@RestController
@RequestMapping("/api/v1/policies/{policyId}")
public class PolicyContractController {
    private static final String TENANT_HEADER = "X-EIA-Tenant";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IF_MATCH = "If-Match";
    private static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final PolicyContractService service;

    public PolicyContractController(PolicyContractService service) {
        this.service = service;
    }

    @GetMapping("/versions")
    public ItemPage<PolicyVersionDto> listVersions(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listVersions(tenantId, policyId, page, size);
    }

    @GetMapping("/versions/current")
    public CurrentVersionDto currentVersion(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                            @PathVariable UUID policyId) {
        return service.currentVersion(tenantId, policyId);
    }

    @GetMapping("/versions/{versionId}")
    public PolicyVersionDto version(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                    @PathVariable UUID policyId,
                                    @PathVariable UUID versionId) {
        return service.version(tenantId, policyId, versionId);
    }

    @GetMapping("/coverages")
    public ItemPage<CoverageViewDto> listCoverages(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listCoverages(tenantId, policyId, page, size);
    }

    @PostMapping("/coverages")
    public ResponseEntity<CoverageViewDto> createCoverage(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateCoverageCommand command) {
        return respond(service.createCoverage(tenantId, policyId, command, ifMatch, idempotencyKey),
                HttpStatus.CREATED);
    }

    @GetMapping("/limits")
    public ItemPage<LimitDto> listLimits(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listLimits(tenantId, policyId, page, size);
    }

    @GetMapping("/aggregate-state")
    public ItemPage<LimitStateDto> aggregateState(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.aggregateState(tenantId, policyId, page, size);
    }

    @PostMapping("/erosion")
    public ResponseEntity<LimitStateDto> applyErosion(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ErosionCommand command) {
        return respond(service.applyErosion(tenantId, policyId, command, idempotencyKey),
                HttpStatus.CREATED);
    }

    @GetMapping("/endorsements")
    public ItemPage<EndorsementDto> listEndorsements(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listEndorsements(tenantId, policyId, page, size);
    }

    @GetMapping("/endorsements/{endorsementId}")
    public EndorsementDto endorsement(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                      @PathVariable UUID policyId,
                                      @PathVariable UUID endorsementId) {
        return service.endorsement(tenantId, policyId, endorsementId);
    }

    @PostMapping("/endorsements")
    public ResponseEntity<EndorsementDto> createEndorsement(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateEndorsementCommand command) {
        return respond(service.createEndorsement(tenantId, policyId, command, ifMatch,
                idempotencyKey), HttpStatus.CREATED);
    }

    @GetMapping("/insureds")
    public ItemPage<InsuredDto> listInsureds(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listInsureds(tenantId, policyId, page, size);
    }

    @PostMapping("/insureds")
    public ResponseEntity<InsuredDto> createInsured(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateInsuredCommand command) {
        return respond(service.createInsured(tenantId, policyId, command, ifMatch, idempotencyKey),
                HttpStatus.CREATED);
    }

    @GetMapping("/locations")
    public ItemPage<LocationDto> listLocations(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listLocations(tenantId, policyId, page, size);
    }

    @PostMapping("/locations")
    public ResponseEntity<LocationDto> createLocation(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateLocationCommand command) {
        return respond(service.createLocation(tenantId, policyId, command, ifMatch, idempotencyKey),
                HttpStatus.CREATED);
    }

    @GetMapping("/cancellations")
    public ItemPage<CancellationDto> listCancellations(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listCancellations(tenantId, policyId, page, size);
    }

    @PostMapping("/cancel")
    public ResponseEntity<CancellationDto> cancel(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CancelPolicyCommand command) {
        return respond(service.cancelPolicy(tenantId, policyId, command, ifMatch, idempotencyKey),
                HttpStatus.CREATED);
    }

    @GetMapping("/reinstatements")
    public ItemPage<ReinstatementDto> listReinstatements(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listReinstatements(tenantId, policyId, page, size);
    }

    @PostMapping("/reinstate")
    public ResponseEntity<ReinstatementDto> reinstate(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID policyId,
            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ReinstatePolicyCommand command) {
        return respond(service.reinstatePolicy(tenantId, policyId, command, ifMatch,
                idempotencyKey), HttpStatus.CREATED);
    }

    /**
     * Renews into a successor term. The successor carries
     * {@code renewedFromPolicyId} back to this policy; the tenant and DIRECT
     * book are forwarded to the expert.
     */
    @PostMapping("/renew")
    public ResponseEntity<PolicyDto> renew(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestHeader(value = BookScope.BOOK_HEADER, required = false) String book,
            @PathVariable UUID policyId,
            @Valid @RequestBody RenewPolicyCommand command) {
        BookScope.requireDirectWhenPresent(book);
        return respond(service.renewPolicy(tenantId, policyId, command), HttpStatus.CREATED);
    }

    private static <T> ResponseEntity<T> respond(Mutation<T> mutation, HttpStatus freshStatus) {
        ResponseEntity.BodyBuilder builder = ResponseEntity
                .status(mutation.replayed() ? HttpStatus.OK : freshStatus)
                .header(IDEMPOTENT_REPLAYED, Boolean.toString(mutation.replayed()));
        if (mutation.etag() != null) {
            builder = builder.eTag(mutation.etag());
        }
        return builder.body(mutation.body());
    }
}
