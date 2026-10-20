package com.eia.policy.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyContractController.class)
class PolicyContractControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID VERSION_ID = UUID.fromString("44444444-4444-7444-8444-444444444444");
    private static final UUID COVERAGE_ID = UUID.fromString("77777777-7777-7777-8777-777777777777");
    private static final UUID LIMIT_ID = UUID.fromString("88888888-8888-7888-8888-888888888888");
    private static final UUID ENDORSEMENT_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID INSURED_ID = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final UUID LOCATION_ID = UUID.fromString("dddddddd-dddd-7ddd-8ddd-dddddddddddd");
    private static final UUID CANCELLATION_ID = UUID.fromString("eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee");
    private static final UUID REINSTATEMENT_ID = UUID.fromString("ffffffff-ffff-7fff-8fff-ffffffffffff");
    private static final UUID SUCCESSOR_ID = UUID.fromString("55555555-5555-7555-8555-555555555555");
    private static final UUID PARTY_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2027, 1, 1);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PolicyContractService service;

    @Test
    void listsVersionsWithDomainLinks() throws Exception {
        when(service.listVersions(TENANT, POLICY_ID, 0, 20)).thenReturn(new ItemPage<>(
                List.of(version()), 0, 20, 2, new ItemPage.Links(
                new ItemPage.Link("/api/v1/policies/" + POLICY_ID + "/versions?page=0&size=20"),
                new ItemPage.Link("/api/v1/policies/" + POLICY_ID + "/versions?page=1&size=20"),
                null)));

        mvc.perform(get("/api/v1/policies/{policyId}/versions", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].versionNumber").value(3))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$._links.self.href").value(
                        "/api/v1/policies/" + POLICY_ID + "/versions?page=0&size=20"))
                .andExpect(jsonPath("$._links.next.href").exists());
    }

    @Test
    void returnsTheCurrentVersion() throws Exception {
        when(service.currentVersion(TENANT, POLICY_ID))
                .thenReturn(new CurrentVersionDto(version(), true, true));

        mvc.perform(get("/api/v1/policies/{policyId}/versions/current", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashVerified").value(true))
                .andExpect(jsonPath("$.version.changeType").value("ENDORSEMENT"));
    }

    @Test
    void returnsOneVersion() throws Exception {
        when(service.version(TENANT, POLICY_ID, VERSION_ID)).thenReturn(version());

        mvc.perform(get("/api/v1/policies/{policyId}/versions/{versionId}", POLICY_ID, VERSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(VERSION_ID.toString()));
    }

    @Test
    void createsACoverageWithEtagAndIdempotencyHeaders() throws Exception {
        when(service.createCoverage(eq(TENANT), eq(POLICY_ID), any(CreateCoverageCommand.class),
                eq("\"4\""), eq("key-1")))
                .thenReturn(new Mutation<>(coverageView(), "\"5\"", false));

        mvc.perform(post("/api/v1/policies/{policyId}/coverages", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("If-Match", "\"4\"")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(coverageCommand())))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.coverage.coverageCode").value("GL_PRODUCTS"))
                .andExpect(jsonPath("$.limit.aggregateAmount").value("5000000.0000"));
    }

    @Test
    void replaysACoverageCreateWithOkStatus() throws Exception {
        when(service.createCoverage(eq(TENANT), eq(POLICY_ID), any(CreateCoverageCommand.class),
                eq(null), eq("key-1"))).thenReturn(new Mutation<>(coverageView(), "\"5\"", true));

        mvc.perform(post("/api/v1/policies/{policyId}/coverages", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(coverageCommand())))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void listsLimitsAndAggregateState() throws Exception {
        when(service.listLimits(TENANT, POLICY_ID, 0, 20)).thenReturn(
                new ItemPage<>(List.of(limit()), 0, 20, 1, null));
        when(service.aggregateState(TENANT, POLICY_ID, 0, 20)).thenReturn(
                new ItemPage<>(List.of(state()), 0, 20, 1, null));

        mvc.perform(get("/api/v1/policies/{policyId}/limits", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].perOccurrenceAmount").value("2000000.0000"));
        mvc.perform(get("/api/v1/policies/{policyId}/aggregate-state", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].remainingAmount").value("4700000.0000"));
    }

    @Test
    void appliesAnErosionFact() throws Exception {
        when(service.applyErosion(eq(TENANT), eq(POLICY_ID), any(ErosionCommand.class), eq("key-2")))
                .thenReturn(new Mutation<>(state(), "\"6\"", false));

        mvc.perform(post("/api/v1/policies/{policyId}/erosion", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("Idempotency-Key", "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(erosionCommand())))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"6\""))
                .andExpect(jsonPath("$.remainingAmount").value("4700000.0000"));
    }

    @Test
    void listsAndCreatesEndorsements() throws Exception {
        when(service.listEndorsements(TENANT, POLICY_ID, 0, 20)).thenReturn(
                new ItemPage<>(List.of(endorsement()), 0, 20, 1, null));
        when(service.createEndorsement(eq(TENANT), eq(POLICY_ID),
                any(CreateEndorsementCommand.class), eq("\"4\""), eq("key-3")))
                .thenReturn(new Mutation<>(endorsement(), "\"5\"", false));

        mvc.perform(get("/api/v1/policies/{policyId}/endorsements", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].endorsementNumber").value("END-0002"));
        mvc.perform(post("/api/v1/policies/{policyId}/endorsements", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("If-Match", "\"4\"")
                        .header("Idempotency-Key", "key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(endorsementCommand())))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(jsonPath("$.policyVersionId").value(VERSION_ID.toString()));
    }

    @Test
    void listsAndCreatesInsureds() throws Exception {
        when(service.listInsureds(TENANT, POLICY_ID, 0, 20)).thenReturn(
                new ItemPage<>(List.of(insured()), 0, 20, 1, null));
        when(service.createInsured(eq(TENANT), eq(POLICY_ID), any(CreateInsuredCommand.class),
                eq("\"0\""), eq(null))).thenReturn(new Mutation<>(insured(), "\"1\"", false));

        mvc.perform(get("/api/v1/policies/{policyId}/insureds", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].insuredRole").value("NAMED_INSURED"));
        mvc.perform(post("/api/v1/policies/{policyId}/insureds", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(insuredCommand())))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"1\""));
    }

    @Test
    void listsAndCreatesLocations() throws Exception {
        when(service.listLocations(TENANT, POLICY_ID, 0, 20)).thenReturn(
                new ItemPage<>(List.of(location()), 0, 20, 1, null));
        when(service.createLocation(eq(TENANT), eq(POLICY_ID), any(CreateLocationCommand.class),
                eq(null), eq("key-4"))).thenReturn(new Mutation<>(location(), "\"2\"", false));

        mvc.perform(get("/api/v1/policies/{policyId}/locations", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].stateRegion").value("US-OH"));
        mvc.perform(post("/api/v1/policies/{policyId}/locations", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("Idempotency-Key", "key-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locationCommand())))
                .andExpect(status().isCreated());
    }

    @Test
    void cancelsAPolicyAndListsCancellations() throws Exception {
        when(service.listCancellations(TENANT, POLICY_ID, 0, 20)).thenReturn(
                new ItemPage<>(List.of(cancellation()), 0, 20, 1, null));
        when(service.cancelPolicy(eq(TENANT), eq(POLICY_ID), any(CancelPolicyCommand.class),
                eq("\"4\""), eq("key-5"))).thenReturn(new Mutation<>(cancellation(), "\"5\"", false));

        mvc.perform(get("/api/v1/policies/{policyId}/cancellations", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].returnPremiumAmount").value("60164.4000"));
        mvc.perform(post("/api/v1/policies/{policyId}/cancel", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("If-Match", "\"4\"")
                        .header("Idempotency-Key", "key-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelCommand())))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(jsonPath("$.proRataPct").value("0.501370"));
    }

    @Test
    void reinstatesAPolicyAndListsReinstatements() throws Exception {
        when(service.listReinstatements(TENANT, POLICY_ID, 0, 20)).thenReturn(
                new ItemPage<>(List.of(reinstatement()), 0, 20, 1, null));
        when(service.reinstatePolicy(eq(TENANT), eq(POLICY_ID),
                any(ReinstatePolicyCommand.class), eq("\"5\""), eq(null)))
                .thenReturn(new Mutation<>(reinstatement(), "\"6\"", false));

        mvc.perform(get("/api/v1/policies/{policyId}/reinstatements", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].premiumEvidenceRef").value("RCPT-2026-0001"));
        mvc.perform(post("/api/v1/policies/{policyId}/reinstate", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("If-Match", "\"5\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reinstateCommand())))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"6\""))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void renewsAPolicyAndReturnsTheSuccessor() throws Exception {
        when(service.renewPolicy(eq(TENANT), eq(POLICY_ID), any(RenewPolicyCommand.class)))
                .thenReturn(new Mutation<>(renewedPolicy(), "\"0\"", false));

        mvc.perform(post("/api/v1/policies/{policyId}/renew", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(renewCommand())))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.id").value(SUCCESSOR_ID.toString()))
                .andExpect(jsonPath("$.renewedFromPolicyId").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.policyNumber").value("POL-2026-0000002"))
                .andExpect(jsonPath("$.effectiveDate").value("2027-01-01"))
                .andExpect(jsonPath("$.expirationDate").value("2028-01-01"))
                .andExpect(jsonPath("$.premiumAmount").value("130000.0000"))
                .andExpect(jsonPath("$.limitAmount").value("2500000.0000"));

        verify(service).renewPolicy(eq(TENANT), eq(POLICY_ID),
                any(RenewPolicyCommand.class));
    }

    @Test
    void validatesTheRenewalBody() throws Exception {
        mvc.perform(post("/api/v1/policies/{policyId}/renew", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        org.hamcrest.Matchers.hasItems("effectiveDate", "expirationDate")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsANonDirectBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(post("/api/v1/policies/{policyId}/renew", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(renewCommand())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertRenewalConflictProblem() throws Exception {
        when(service.renewPolicy(eq(TENANT), eq(POLICY_ID), any(RenewPolicyCommand.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "RENEWAL_NOT_ALLOWED",
                        "A cancelled policy cannot be renewed.",
                        List.of(new ApiException.FieldIssue("status", "RENEWAL_NOT_ALLOWED",
                                "Policy status CANCELLED is not renewable.")),
                        Map.of("sourceStatus", "CANCELLED")));

        mvc.perform(post("/api/v1/policies/{policyId}/renew", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(renewCommand())))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RENEWAL_NOT_ALLOWED"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.meta.sourceStatus").value("CANCELLED"));
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get("/api/v1/policies/{policyId}/versions", POLICY_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));
    }

    @Test
    void preservesTheExpertApprovalProblem() throws Exception {
        when(service.createEndorsement(eq(TENANT), eq(POLICY_ID),
                any(CreateEndorsementCommand.class), eq(null), eq(null)))
                .thenThrow(new ApiException(HttpStatus.PRECONDITION_REQUIRED, "APPROVAL_REQUIRED",
                        "An endorsement backdated before policy inception requires approval.",
                        List.of(new ApiException.FieldIssue("approvalRef", "APPROVAL_REQUIRED",
                                "requires approval"))));

        mvc.perform(post("/api/v1/policies/{policyId}/endorsements", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(endorsementCommand())))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.category").value("APPROVAL_REQUIRED"));
    }

    @Test
    void validatesTheCoverageCommandBody() throws Exception {
        mvc.perform(post("/api/v1/policies/{policyId}/coverages", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"coverageCode":"GL_PRODUCTS","coverageName":"Products",
                                 "coveragePart":"A","occurrenceBasis":"OCCURRENCE",
                                 "effectiveFrom":"2026-01-01","effectiveTo":"2027-01-01",
                                 "limit":{"perOccurrenceAmount":0,"currency":"USD"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("limit.perOccurrenceAmount"));
    }

    @Test
    void forwardsTheConcurrencyAndIdempotencyHeaders() throws Exception {
        when(service.createCoverage(eq(TENANT), eq(POLICY_ID), any(CreateCoverageCommand.class),
                eq("\"4\""), eq("key-1"))).thenReturn(new Mutation<>(coverageView(), "\"5\"", false));

        mvc.perform(post("/api/v1/policies/{policyId}/coverages", POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("If-Match", "\"4\"")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(coverageCommand())))
                .andExpect(status().isCreated());

        verify(service).createCoverage(eq(TENANT), eq(POLICY_ID),
                any(CreateCoverageCommand.class), eq("\"4\""), eq("key-1"));
    }

    private static CreateCoverageCommand coverageCommand() {
        return new CreateCoverageCommand("GL_PRODUCTS", "Products liability", "A", "OCCURRENCE",
                FROM, TO, new BigDecimal("0.10"),
                new CreateCoverageCommand.LimitCommand(new BigDecimal("2000000"),
                        new BigDecimal("5000000"), null, new BigDecimal("1000000"),
                        new BigDecimal("5000"), new BigDecimal("0.10"), "USD", null));
    }

    private static ErosionCommand erosionCommand() {
        return new ErosionCommand(LIMIT_ID, UUID.randomUUID(), UUID.randomUUID(), 1L,
                new BigDecimal("250000"), new BigDecimal("50000"), BigDecimal.ZERO,
                Instant.parse("2026-10-19T12:00:00Z"));
    }

    private static CreateEndorsementCommand endorsementCommand() {
        return new CreateEndorsementCommand(null, "LIMIT", LocalDate.of(2026, 6, 1),
                "Increase the general aggregate", new BigDecimal("5000.00"), "USD", "BROADEN",
                List.of(), null, null, null, null, null);
    }

    private static CreateInsuredCommand insuredCommand() {
        return new CreateInsuredCommand(UUID.randomUUID(), "NAMED_INSURED",
                "Acme Chemical Corporation", null, null, FROM, TO);
    }

    private static CreateLocationCommand locationCommand() {
        return new CreateLocationCommand("LOC-001", "1200 Industrial Way", null, "Akron", "US-OH",
                "44311", "USA", FROM, TO);
    }

    private static CancelPolicyCommand cancelCommand() {
        return new CancelPolicyCommand("PRO_RATA", "Insured sold the business",
                LocalDate.of(2026, 7, 2), 0, null, null);
    }

    private static ReinstatePolicyCommand reinstateCommand() {
        return new ReinstatePolicyCommand(LocalDate.of(2026, 8, 1), "Premium received",
                UUID.randomUUID(), UUID.randomUUID(), "RCPT-2026-0001",
                new BigDecimal("1500.00"), "USD");
    }

    private static PolicyVersionDto version() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        return new PolicyVersionDto(VERSION_ID, TENANT, POLICY_ID, 3, "POL-2026-0000001-03",
                "ENDORSEMENT", "ISSUED", LocalDate.of(2026, 6, 1), null, now, null, null, null,
                new BigDecimal("5000.0000"), "USD", "Endorsement", null, "hash", now);
    }

    private static CoverageViewDto coverageView() {
        return new CoverageViewDto(coverage(), limit(), state());
    }

    private static CoverageDto coverage() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new CoverageDto(COVERAGE_ID, TENANT, POLICY_ID, "GL_PRODUCTS",
                "Products liability", "A", "OCCURRENCE", new BigDecimal("0.100000"), FROM, TO,
                "ACTIVE", 1, now);
    }

    private static LimitDto limit() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new LimitDto(LIMIT_ID, TENANT, POLICY_ID, COVERAGE_ID,
                new BigDecimal("2000000.0000"), new BigDecimal("5000000.0000"), "PER_COVERAGE",
                new BigDecimal("1000000.0000"), new BigDecimal("5000.0000"),
                new BigDecimal("0.100000"), "USD", "PAID_PLUS_RESERVED", FROM, TO, 1, now);
    }

    private static LimitStateDto state() {
        Instant now = Instant.parse("2026-10-19T12:00:00Z");
        return new LimitStateDto(UUID.randomUUID(), TENANT, POLICY_ID, LIMIT_ID, now,
                new BigDecimal("5000000.0000"), new BigDecimal("250000.0000"),
                new BigDecimal("50000.0000"), new BigDecimal("300000.0000"),
                BigDecimal.ZERO.setScale(4), new BigDecimal("4700000.0000"),
                new BigDecimal("0.060000"), BigDecimal.ZERO.setScale(4), 1L, now, "CURRENT", 1);
    }

    private static EndorsementDto endorsement() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        return new EndorsementDto(ENDORSEMENT_ID, TENANT, POLICY_ID, VERSION_ID, "END-0002", 2,
                null, null, "LIMIT", LocalDate.of(2026, 6, 1), "Increase the general aggregate",
                new BigDecimal("5000.0000"), "USD", "BROADEN", List.of(), false, null, null, null,
                "ISSUED", now, now);
    }

    private static InsuredDto insured() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new InsuredDto(INSURED_ID, TENANT, POLICY_ID, UUID.randomUUID(), "NAMED_INSURED",
                "Acme Chemical Corporation", "CERT-1", null, FROM, TO, "ACTIVE", now);
    }

    private static LocationDto location() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new LocationDto(LOCATION_ID, TENANT, POLICY_ID, "LOC-001", "1200 Industrial Way",
                null, "Akron", "US-OH", "44311", "USA", FROM, TO, "ACTIVE", now);
    }

    private static CancellationDto cancellation() {
        Instant now = Instant.parse("2026-07-02T00:00:00Z");
        return new CancellationDto(CANCELLATION_ID, TENANT, POLICY_ID, VERSION_ID, "PRO_RATA",
                "Insured sold the business", LocalDate.of(2026, 7, 2), 0, now, 365, 183,
                new BigDecimal("0.501370"), new BigDecimal("59835.6000"),
                new BigDecimal("60164.4000"), "USD", null, null, "ACTIVE", now);
    }

    private static ReinstatementDto reinstatement() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        return new ReinstatementDto(REINSTATEMENT_ID, TENANT, POLICY_ID, CANCELLATION_ID,
                VERSION_ID, LocalDate.of(2026, 8, 1), "Premium received", UUID.randomUUID(),
                UUID.randomUUID(), "RCPT-2026-0001", new BigDecimal("1500.0000"), "USD",
                "APPROVED", now);
    }

    private static RenewPolicyCommand renewCommand() {
        return new RenewPolicyCommand(LocalDate.of(2027, 1, 1), LocalDate.of(2028, 1, 1),
                new BigDecimal("130000.0000"), "USD", new BigDecimal("2500000.0000"), "USD");
    }

    private static PolicyDto renewedPolicy() {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        return new PolicyDto(SUCCESSOR_ID, TENANT, "POL-2026-0000002", "CGL", PARTY_ID,
                "DRAFT", LocalDate.of(2027, 1, 1), LocalDate.of(2028, 1, 1),
                new BigDecimal("130000.0000"), "USD", new BigDecimal("2500000.0000"), "USD",
                0, now, now, POLICY_ID);
    }
}
