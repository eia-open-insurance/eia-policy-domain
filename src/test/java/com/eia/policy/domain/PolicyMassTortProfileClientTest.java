package com.eia.policy.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PolicyMassTortProfileClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID PROFILE_ID = UUID.fromString("eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee");
    private static final String BASE = "http://expert";
    private static final String PROFILE = BASE + "/internal/v1/policies/" + POLICY_ID
            + "/mass-tort-profile";

    @Test
    void readsTheProfileForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROFILE + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(profileJson()));

        MassTortProfile profile = client.findMassTortProfile(TENANT, POLICY_ID);

        assertThat(profile.profileId()).isEqualTo(PROFILE_ID);
        assertThat(profile.policyId()).isEqualTo(POLICY_ID);
        assertThat(profile.tortType()).isEqualTo("TALC");
        assertThat(profile.triggerType()).isEqualTo("CONTINUOUS_TRIGGER");
        assertThat(profile.allocationMethod()).isEqualTo("ALL_SUMS");
        assertThat(profile.allocationNotes()).contains("triggered policy years");
        assertThat(profile.coverageBlockStart()).isEqualTo(LocalDate.of(1978, 1, 1));
        assertThat(profile.coverageBlockEnd()).isEqualTo(LocalDate.of(1985, 12, 31));
        assertThat(profile.status()).isEqualTo("COMPLETE");
        assertThat(profile.updatedAt()).isEqualTo(Instant.parse("2026-10-20T12:00:00Z"));
        server.verify();
    }

    @Test
    void putsTheUpdatePayloadForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROFILE + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {
                          "tortType": "TALC",
                          "triggerType": "CONTINUOUS_TRIGGER",
                          "allocationMethod": "ALL_SUMS",
                          "allocationNotes": "Allocate to all triggered policy years",
                          "coverageBlockStart": "1978-01-01",
                          "coverageBlockEnd": "1985-12-31",
                          "status": "COMPLETE"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(profileJson()));

        MassTortProfile profile = client.updateMassTortProfile(TENANT, POLICY_ID, command());

        assertThat(profile.profileId()).isEqualTo(PROFILE_ID);
        assertThat(profile.tortType()).isEqualTo("TALC");
        assertThat(profile.triggerType()).isEqualTo("CONTINUOUS_TRIGGER");
        assertThat(profile.allocationMethod()).isEqualTo("ALL_SUMS");
        assertThat(profile.status()).isEqualTo("COMPLETE");
        server.verify();
    }

    @Test
    void mapsTheExpertIdAndSpecColumnNames() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROFILE + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "id": "%s",
                                  "policyId": "%s",
                                  "direct_tort_code": "ASBESTOS",
                                  "trigger_theory": "EXPOSURE",
                                  "allocation_method": "PRO_RATA_TIME_ON_RISK",
                                  "allocation_notes": null,
                                  "coverage_block_start": "1974-01-01",
                                  "coverage_block_end": "2003-12-31",
                                  "status": "DRAFT",
                                  "updated_at": "2026-10-20T12:00:00Z"
                                }
                                """.formatted(PROFILE_ID, POLICY_ID)));

        MassTortProfile profile = client.findMassTortProfile(TENANT, POLICY_ID);

        assertThat(profile.profileId()).isEqualTo(PROFILE_ID);
        assertThat(profile.tortType()).isEqualTo("ASBESTOS");
        assertThat(profile.triggerType()).isEqualTo("EXPOSURE");
        assertThat(profile.allocationMethod()).isEqualTo("PRO_RATA_TIME_ON_RISK");
        assertThat(profile.coverageBlockStart()).isEqualTo(LocalDate.of(1974, 1, 1));
        assertThat(profile.coverageBlockEnd()).isEqualTo(LocalDate.of(2003, 12, 31));
        assertThat(profile.status()).isEqualTo("DRAFT");
        assertThat(profile.updatedAt()).isEqualTo(Instant.parse("2026-10-20T12:00:00Z"));
        server.verify();
    }

    @Test
    void preservesTheExpertValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROFILE + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/invalid-mass-tort-allocation",
                                  "title": "Invalid mass tort allocation",
                                  "status": 400,
                                  "detail": "allocationMethod CUSTOM_MATRIX requires allocation percentages that sum to 1",
                                  "code": "INVALID_MASS_TORT_ALLOCATION",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "allocationMethod", "code": "ALLOCATION_SUM_INVALID",
                                     "message": "allocations must sum to 1"}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.updateMassTortProfile(TENANT, POLICY_ID, command()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_MASS_TORT_ALLOCATION");
                    assertThat(api.getMessage()).contains("allocation percentages");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("allocationMethod");
                        assertThat(issue.code()).isEqualTo("ALLOCATION_SUM_INVALID");
                    });
                });
        server.verify();
    }

    @Test
    void preservesTheProfileNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROFILE + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/mass-tort-profile-not-found",
                                  "title": "Mass tort profile not found",
                                  "status": 404,
                                  "detail": "Policy %s has no mass tort profile",
                                  "code": "MASS_TORT_PROFILE_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(POLICY_ID)));

        assertThatThrownBy(() -> client.findMassTortProfile(TENANT, POLICY_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("MASS_TORT_PROFILE_NOT_FOUND");
                    assertThat(api.getMessage()).contains(POLICY_ID.toString());
                });
        server.verify();
    }

    @Test
    void preservesTheProfileConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROFILE + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/concurrent-modification",
                                  "title": "Profile was modified",
                                  "status": 409,
                                  "detail": "The mass tort profile changed since it was read",
                                  "code": "CONCURRENT_MODIFICATION",
                                  "category": "CONFLICT",
                                  "retryable": true,
                                  "errors": [
                                    {"field": "updatedAt", "code": "STALE_PROFILE",
                                     "message": "reload the profile and retry"}
                                  ],
                                  "meta": {"currentVersion": 3}
                                }
                                """));

        assertThatThrownBy(() -> client.updateMassTortProfile(TENANT, POLICY_ID, command()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("CONCURRENT_MODIFICATION");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.code()).isEqualTo("STALE_PROFILE"));
                    assertThat(api.meta()).containsEntry("currentVersion", 3);
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertProfileResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROFILE + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findMassTortProfile(TENANT, POLICY_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    private static UpdateMassTortProfileCommand command() {
        return new UpdateMassTortProfileCommand("TALC", "CONTINUOUS_TRIGGER", "ALL_SUMS",
                "Allocate to all triggered policy years", LocalDate.of(1978, 1, 1),
                LocalDate.of(1985, 12, 31), "COMPLETE");
    }

    /**
     * The production RestClient builder is supplied by Spring Boot and writes
     * ISO dates; a plain builder would serialize {@link LocalDate} as an array.
     */
    private static RestClient.Builder builder() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return RestClient.builder()
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(0, new MappingJackson2HttpMessageConverter(mapper));
                });
    }

    private static String profileJson() {
        return """
                {
                  "profileId": "%s",
                  "policyId": "%s",
                  "tortType": "TALC",
                  "triggerType": "CONTINUOUS_TRIGGER",
                  "allocationMethod": "ALL_SUMS",
                  "allocationNotes": "Allocate to all triggered policy years",
                  "coverageBlockStart": "1978-01-01",
                  "coverageBlockEnd": "1985-12-31",
                  "status": "COMPLETE",
                  "updatedAt": "2026-10-20T12:00:00Z"
                }
                """.formatted(PROFILE_ID, POLICY_ID);
    }
}
