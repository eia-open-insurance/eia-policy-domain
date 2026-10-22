package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyRatingController.class)
class PolicyRatingControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID WORKSHEET_ID = UUID.fromString("dddddddd-dddd-7ddd-8ddd-dddddddddddd");
    private static final UUID CREATED_BY = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final String DIRECT = "DIRECT";
    private static final String RATING = "/api/v1/policies/{policyId}/rating";
    private static final RatePolicyCommand COMMAND = new RatePolicyCommand("RATE_TIMES_EXPOSURE",
            new BigDecimal("0.0125"), new BigDecimal("250000.0000"),
            new BigDecimal("1.250000"), true);
    private static final String BODY = """
            {
              "ratingMethod": "RATE_TIMES_EXPOSURE",
              "baseRate": 0.0125,
              "exposureAmount": 250000.0000,
              "factor": 1.250000,
              "apply": true
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PolicyRatingService service;

    @Test
    void ratesThePolicyAndReturnsTheWorksheetAndAppliedPolicy() throws Exception {
        when(service.rate(TENANT, POLICY_ID, COMMAND, "\"4\"", "rating-key-1"))
                .thenReturn(new Mutation<>(new PolicyRatingResult(worksheet(), policy()),
                        "\"5\"", false));

        mvc.perform(ratingRequest()
                        .header("If-Match", "\"4\"")
                        .header("Idempotency-Key", "rating-key-1"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.worksheet.id").value(WORKSHEET_ID.toString()))
                .andExpect(jsonPath("$.worksheet.tenantId").value(TENANT.toString()))
                .andExpect(jsonPath("$.worksheet.policyId").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.worksheet.ratingMethod").value("RATE_TIMES_EXPOSURE"))
                .andExpect(jsonPath("$.worksheet.baseRate").value("0.012500"))
                .andExpect(jsonPath("$.worksheet.exposureAmount").value("250000.0000"))
                .andExpect(jsonPath("$.worksheet.factor").value("1.250000"))
                .andExpect(jsonPath("$.worksheet.computedPremium").value("3906.2500"))
                .andExpect(jsonPath("$.worksheet.currency").value("USD"))
                .andExpect(jsonPath("$.worksheet.calcHash").value("a".repeat(64)))
                .andExpect(jsonPath("$.worksheet.status").value("CALCULATED"))
                .andExpect(jsonPath("$.worksheet.createdAt").value("2026-10-22T09:00:00Z"))
                .andExpect(jsonPath("$.worksheet.createdBy").value(CREATED_BY.toString()))
                .andExpect(jsonPath("$.policy.id").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.policy.policyNumber").value("GL-2026-0041827"))
                .andExpect(jsonPath("$.policy.premiumAmount").value("3906.2500"))
                .andExpect(jsonPath("$.policy.version").value(5));

        verify(service).rate(TENANT, POLICY_ID, COMMAND, "\"4\"", "rating-key-1");
    }

    @Test
    void returnsTheWorksheetAloneForACalculationOnlyRating() throws Exception {
        when(service.rate(TENANT, POLICY_ID, COMMAND, null, null))
                .thenReturn(new Mutation<>(new PolicyRatingResult(worksheet(), null),
                        null, false));

        mvc.perform(ratingRequest())
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(jsonPath("$.worksheet.id").value(WORKSHEET_ID.toString()))
                .andExpect(jsonPath("$.policy").doesNotExist());

        verify(service).rate(TENANT, POLICY_ID, COMMAND, null, null);
    }

    @Test
    void returnsOkWhenTheExpertReplaysTheRating() throws Exception {
        when(service.rate(TENANT, POLICY_ID, COMMAND, null, "rating-key-1"))
                .thenReturn(new Mutation<>(new PolicyRatingResult(worksheet(), policy()),
                        "\"5\"", true));

        mvc.perform(ratingRequest().header("Idempotency-Key", "rating-key-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.worksheet.id").value(WORKSHEET_ID.toString()));

        verify(service).rate(TENANT, POLICY_ID, COMMAND, null, "rating-key-1");
    }

    @Test
    void derivesTheETagFromTheAppliedPolicyWhenTheExpertOmitsIt() throws Exception {
        when(service.rate(TENANT, POLICY_ID, COMMAND, null, null))
                .thenReturn(new Mutation<>(new PolicyRatingResult(worksheet(), policy()),
                        null, false));

        mvc.perform(ratingRequest())
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.policy.version").value(5));
    }

    @Test
    void returnsTheLatestWorksheet() throws Exception {
        when(service.latest(TENANT, POLICY_ID)).thenReturn(worksheet());

        mvc.perform(get(RATING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(WORKSHEET_ID.toString()))
                .andExpect(jsonPath("$.policyId").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.ratingMethod").value("RATE_TIMES_EXPOSURE"))
                .andExpect(jsonPath("$.baseRate").value("0.012500"))
                .andExpect(jsonPath("$.exposureAmount").value("250000.0000"))
                .andExpect(jsonPath("$.factor").value("1.250000"))
                .andExpect(jsonPath("$.computedPremium").value("3906.2500"))
                .andExpect(jsonPath("$.calcHash").value("a".repeat(64)))
                .andExpect(jsonPath("$.status").value("CALCULATED"));

        verify(service).latest(TENANT, POLICY_ID);
    }

    @Test
    void acceptsTheDocumentedDefaults() throws Exception {
        RatePolicyCommand command = new RatePolicyCommand("FLAT", new BigDecimal("1250.00"),
                null, BigDecimal.ONE, false);
        when(service.rate(TENANT, POLICY_ID, command, null, null))
                .thenReturn(new Mutation<>(new PolicyRatingResult(worksheet(), null),
                        null, false));

        mvc.perform(post(RATING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ratingMethod": "FLAT", "baseRate": 1250.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.worksheet.id").value(WORKSHEET_ID.toString()));

        verify(service).rate(TENANT, POLICY_ID, command, null, null);
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(service.latest(TENANT, POLICY_ID)).thenReturn(worksheet());

        mvc.perform(get(RATING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(WORKSHEET_ID.toString()));
    }

    @Test
    void acceptsTheDirectBookCaseInsensitively() throws Exception {
        when(service.rate(TENANT, POLICY_ID, COMMAND, null, null))
                .thenReturn(new Mutation<>(new PolicyRatingResult(worksheet(), null),
                        null, false));

        mvc.perform(post(RATING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "direct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsAnyOtherBookOnPostBeforeCallingTheExpert() throws Exception {
        mvc.perform(ratingRequest().header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnyOtherBookOnGetBeforeCallingTheExpert() throws Exception {
        mvc.perform(get(RATING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"));

        verifyNoInteractions(service);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(post(RATING, POLICY_ID)
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(service);
    }

    @Test
    void validatesTheRatingBody() throws Exception {
        mvc.perform(post(RATING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("baseRate")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsANegativeBaseRate() throws Exception {
        mvc.perform(post(RATING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ratingMethod": "FLAT", "baseRate": -1.0000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("baseRate")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsANegativeFactor() throws Exception {
        mvc.perform(post(RATING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ratingMethod": "FLAT", "baseRate": 10.0000, "factor": -1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("factor")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnExposureWithTooManyFractionDigits() throws Exception {
        mvc.perform(post(RATING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ratingMethod": "RATE_TIMES_EXPOSURE", "baseRate": 10.0000,
                                 "exposureAmount": 1.12345}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("exposureAmount")));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertNotRateableConflict() throws Exception {
        when(service.rate(TENANT, POLICY_ID, COMMAND, null, null))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "POLICY_NOT_RATEABLE",
                        "Policy GL-2026-0041827 in status IN_FORCE cannot be rated; only "
                                + "DRAFT or QUOTED policies are rateable.",
                        List.of(new ApiException.FieldIssue("status", "POLICY_NOT_RATEABLE",
                                "only DRAFT or QUOTED policies are rateable")),
                        Map.of("policyId", POLICY_ID.toString(), "sourceStatus", "IN_FORCE",
                                "allowedStatuses", List.of("DRAFT", "QUOTED"))));

        mvc.perform(ratingRequest())
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("POLICY_NOT_RATEABLE"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.meta.sourceStatus").value("IN_FORCE"))
                .andExpect(jsonPath("$.meta.allowedStatuses[*]", hasItems("DRAFT", "QUOTED")));
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(service.rate(TENANT, POLICY_ID, COMMAND, null, null))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "RATING_EXPOSURE_INVALID",
                        "exposureAmount must be greater than zero for RATE_TIMES_EXPOSURE.",
                        List.of(new ApiException.FieldIssue("exposureAmount",
                                "RATING_EXPOSURE_INVALID",
                                "exposureAmount must be greater than zero for "
                                        + "RATE_TIMES_EXPOSURE."))));

        mvc.perform(ratingRequest())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATING_EXPOSURE_INVALID"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("exposureAmount"));
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.rate(TENANT, POLICY_ID, COMMAND, null, null))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND",
                        "Policy not found: " + POLICY_ID,
                        List.of(new ApiException.FieldIssue("policyId", "POLICY_NOT_FOUND",
                                "Policy not found: " + POLICY_ID))));

        mvc.perform(ratingRequest())
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));
    }

    @Test
    void preservesTheMissingWorksheetProblem() throws Exception {
        when(service.latest(TENANT, POLICY_ID))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "RATING_WORKSHEET_NOT_FOUND",
                        "No rating worksheet exists for policy: " + POLICY_ID));

        mvc.perform(get(RATING, POLICY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATING_WORKSHEET_NOT_FOUND"));
    }

    private static MockHttpServletRequestBuilder ratingRequest() {
        return post(RATING, POLICY_ID)
                .header("X-EIA-Tenant", TENANT.toString())
                .header("X-EIA-Book", DIRECT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
    }

    private static RatingWorksheetDto worksheet() {
        return new RatingWorksheetDto(WORKSHEET_ID, TENANT, POLICY_ID,
                "RATE_TIMES_EXPOSURE", new BigDecimal("0.012500"),
                new BigDecimal("250000.0000"), new BigDecimal("1.250000"),
                new BigDecimal("3906.2500"), "USD", "a".repeat(64), "CALCULATED",
                Instant.parse("2026-10-22T09:00:00Z"), CREATED_BY);
    }

    private static PolicyDto policy() {
        return new PolicyDto(POLICY_ID, TENANT, "GL-2026-0041827", "GL",
                UUID.fromString("22222222-2222-7222-8222-222222222222"), "DRAFT",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                new BigDecimal("3906.2500"), "USD", new BigDecimal("1000000.0000"), "USD",
                5, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-10-22T09:00:00Z"), null, null, null);
    }
}
