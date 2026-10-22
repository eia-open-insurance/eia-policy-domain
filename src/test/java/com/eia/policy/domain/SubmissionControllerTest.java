package com.eia.policy.domain;

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

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubmissionController.class)
class SubmissionControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID SUBMISSION_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPLICANT_ID =
            UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final String DIRECT = "DIRECT";
    private static final String BASE = "/api/v1/policies/submissions";
    private static final String DETAIL = BASE + "/{submissionId}";
    private static final String STATUS = BASE + "/{submissionId}/status";

    private static final CreateSubmissionCommand COMMAND = new CreateSubmissionCommand(
            APPLICANT_ID, "CGL_PRODUCTS", LocalDate.of(2026, 1, 1),
            new BigDecimal("1000000.0000"), "USD", List.of("ASBESTOS", "PFAS"),
            "broker intake");
    private static final UpdateSubmissionStatusCommand STATUS_COMMAND =
            new UpdateSubmissionStatusCommand("QUOTED", "ready to quote");
    private static final String CREATE_BODY = """
            {
              "applicantPartyId": "%s",
              "productCode": "CGL_PRODUCTS",
              "requestedEffectiveDate": "2026-01-01",
              "requestedLimitAmount": 1000000.0000,
              "requestedLimitCurrency": "USD",
              "tortCodes": ["ASBESTOS", "PFAS"],
              "notes": "broker intake"
            }
            """.formatted(APPLICANT_ID);
    private static final String STATUS_BODY = """
            {"status": "QUOTED", "reason": "ready to quote"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SubmissionService service;

    @Test
    void listsTheTenantSubmissionsAsAnArray() throws Exception {
        when(service.list(TENANT)).thenReturn(List.of(submission(2)));

        mvc.perform(get(BASE)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].submissionId").value(SUBMISSION_ID.toString()))
                .andExpect(jsonPath("$[0].submissionNumber").value("SUB-2026-0000001"))
                .andExpect(jsonPath("$[0].tenantId").value(TENANT.toString()))
                .andExpect(jsonPath("$[0].applicantPartyId").value(APPLICANT_ID.toString()))
                .andExpect(jsonPath("$[0].productCode").value("CGL_PRODUCTS"))
                .andExpect(jsonPath("$[0].requestedEffectiveDate").value("2026-01-01"))
                .andExpect(jsonPath("$[0].requestedLimitAmount").isNumber())
                .andExpect(jsonPath("$[0].requestedLimitCurrency").value("USD"))
                .andExpect(jsonPath("$[0].tortCodes[0]").value("ASBESTOS"))
                .andExpect(jsonPath("$[0].notes").value("broker intake"))
                .andExpect(jsonPath("$[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].updatedAt").exists())
                .andExpect(jsonPath("$[0].productId").doesNotExist())
                .andExpect(jsonPath("$[0].requestedLimits").doesNotExist())
                .andExpect(jsonPath("$[0].owner").doesNotExist());

        verify(service).list(TENANT);
    }

    @Test
    void readsOneSubmissionAndDerivesTheEtagFromItsVersion() throws Exception {
        when(service.get(TENANT, SUBMISSION_ID)).thenReturn(submission(3));

        mvc.perform(get(DETAIL, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.submissionId").value(SUBMISSION_ID.toString()))
                .andExpect(jsonPath("$.submissionNumber").value("SUB-2026-0000001"))
                .andExpect(jsonPath("$.tenantId").value(TENANT.toString()))
                .andExpect(jsonPath("$.productCode").value("CGL_PRODUCTS"))
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verify(service).get(TENANT, SUBMISSION_ID);
    }

    @Test
    void omitsTheEtagWhenTheExpertSuppliesNoVersion() throws Exception {
        when(service.get(TENANT, SUBMISSION_ID)).thenReturn(submission(null));

        mvc.perform(get(DETAIL, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void createsTheSubmissionAndReturnsCreated() throws Exception {
        when(service.create(TENANT, COMMAND, "sub-key"))
                .thenReturn(new Mutation<>(submission(2), "\"2\"", false));

        mvc.perform(post(BASE)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .header("Idempotency-Key", "sub-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(header().string("Location",
                        "/api/v1/policies/submissions/" + SUBMISSION_ID))
                .andExpect(jsonPath("$.submissionId").value(SUBMISSION_ID.toString()))
                .andExpect(jsonPath("$.submissionNumber").value("SUB-2026-0000001"))
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verify(service).create(TENANT, COMMAND, "sub-key");
    }

    @Test
    void returnsOkAndTheReplayMarkerForAnIdempotentReplay() throws Exception {
        when(service.create(TENANT, COMMAND, "sub-key"))
                .thenReturn(new Mutation<>(submission(2), "\"2\"", true));

        mvc.perform(post(BASE)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .header("Idempotency-Key", "sub-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.submissionId").value(SUBMISSION_ID.toString()));
    }

    @Test
    void derivesTheEtagFromTheReturnedSubmissionWhenTheExpertOmitsIt() throws Exception {
        when(service.create(TENANT, COMMAND, null))
                .thenReturn(new Mutation<>(submission(4), null, false));

        mvc.perform(post(BASE)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"4\""));
    }

    @Test
    void updatesTheSubmissionStatus() throws Exception {
        when(service.updateStatus(TENANT, SUBMISSION_ID, STATUS_COMMAND, "\"2\"", "status-key"))
                .thenReturn(new Mutation<>(submission("QUOTED", 3), "\"3\"", false));

        mvc.perform(patch(STATUS, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .header("If-Match", "\"2\"")
                        .header("Idempotency-Key", "status-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATUS_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.submissionId").value(SUBMISSION_ID.toString()))
                .andExpect(jsonPath("$.status").value("QUOTED"));

        verify(service).updateStatus(TENANT, SUBMISSION_ID, STATUS_COMMAND, "\"2\"",
                "status-key");
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(service.list(TENANT)).thenReturn(List.of(submission(2)));

        mvc.perform(get(BASE).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].submissionId").value(SUBMISSION_ID.toString()));
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(get(BASE)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(service);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(post(BASE)
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(service);
    }

    @Test
    void validatesTheCreateBody() throws Exception {
        mvc.perform(post(BASE)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("applicantPartyId", "productCode", "requestedEffectiveDate",
                                "requestedLimitAmount", "requestedLimitCurrency")));

        verifyNoInteractions(service);
    }

    @Test
    void appliesTheExpertCreateCaps() throws Exception {
        mvc.perform(post(BASE)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicantPartyId": "%s",
                                  "productCode": "CGL",
                                  "requestedEffectiveDate": "2026-01-01",
                                  "requestedLimitAmount": -1,
                                  "requestedLimitCurrency": "US"
                                }
                                """.formatted(APPLICANT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("requestedLimitAmount", "requestedLimitCurrency")));

        verifyNoInteractions(service);
    }

    @Test
    void validatesTheStatusBody() throws Exception {
        mvc.perform(patch(STATUS, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("status")));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(service.create(eq(TENANT), any(CreateSubmissionCommand.class), eq("sub-key")))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUBMISSION",
                        "applicantPartyId must identify a party in the tenant",
                        List.of(new ApiException.FieldIssue("applicantPartyId",
                                "INVALID_PARTY",
                                "applicantPartyId must identify a party in the tenant"))));

        mvc.perform(post(BASE)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .header("Idempotency-Key", "sub-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_SUBMISSION"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("applicantPartyId"));
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.get(TENANT, SUBMISSION_ID))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                        "Submission not found: " + SUBMISSION_ID,
                        List.of(new ApiException.FieldIssue("submissionId",
                                "SUBMISSION_NOT_FOUND", "Submission not found"))));

        mvc.perform(get(DETAIL, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));
    }

    @Test
    void preservesTheExpertStatusConflictProblem() throws Exception {
        when(service.updateStatus(eq(TENANT), eq(SUBMISSION_ID), any(), eq("\"2\""),
                eq("status-key")))
                .thenThrow(new ApiException(HttpStatus.CONFLICT,
                        "INVALID_SUBMISSION_TRANSITION",
                        "A submission in RECEIVED cannot move to QUOTED",
                        List.of(new ApiException.FieldIssue("status",
                                "INVALID_SUBMISSION_TRANSITION",
                                "run clearance and rating before quoting")),
                        Map.of("sourceStatus", "RECEIVED", "targetStatus", "QUOTED")));

        mvc.perform(patch(STATUS, SUBMISSION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .header("If-Match", "\"2\"")
                        .header("Idempotency-Key", "status-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATUS_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_SUBMISSION_TRANSITION"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.meta.sourceStatus").value("RECEIVED"));
    }

    private static SubmissionDto submission(Integer version) {
        return submission("RECEIVED", version);
    }

    private static SubmissionDto submission(String status, Integer version) {
        return new SubmissionDto(SUBMISSION_ID, "SUB-2026-0000001", TENANT, APPLICANT_ID,
                "CGL_PRODUCTS", LocalDate.of(2026, 1, 1),
                new BigDecimal("1000000.0000"), "USD", List.of("ASBESTOS", "PFAS"),
                "broker intake", status, version,
                Instant.parse("2026-10-22T09:00:00Z"),
                Instant.parse("2026-10-22T10:00:00Z"));
    }
}
