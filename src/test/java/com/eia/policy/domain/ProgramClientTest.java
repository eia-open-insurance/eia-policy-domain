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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ProgramClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID PROGRAM_ID = UUID.fromString("55555555-5555-7555-8555-555555555555");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID LEAD_POLICY_ID =
            UUID.fromString("44444444-4444-7444-8444-444444444444");
    private static final String BASE = "http://expert";
    private static final String PROGRAMS = BASE + "/internal/v1/programs";
    private static final String POLICY_PROGRAM =
            BASE + "/internal/v1/policies/" + POLICY_ID + "/program";

    @Test
    void createsTheProgramForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROGRAMS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {
                          "name": "Asbestos Primary Program",
                          "programType": "MASS_TORT",
                          "effectiveFrom": "2026-01-01",
                          "effectiveTo": "2027-01-01",
                          "leadPolicyId": "%s",
                          "capacityAmount": 25000000.0000,
                          "capacityCurrency": "USD"
                        }
                        """.formatted(LEAD_POLICY_ID), JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(programJson()));

        ProgramDto program = client.createProgram(TENANT, command());

        assertThat(program.programId()).isEqualTo(PROGRAM_ID);
        assertThat(program.programNumber()).isEqualTo("PRG-2026-0000001");
        assertThat(program.name()).isEqualTo("Asbestos Primary Program");
        assertThat(program.programType()).isEqualTo("MASS_TORT");
        assertThat(program.effectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(program.effectiveTo()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(program.status()).isEqualTo("ACTIVE");
        assertThat(program.leadPolicyId()).isEqualTo(LEAD_POLICY_ID);
        assertThat(program.capacityAmount()).isEqualTo(new BigDecimal("25000000.0000"));
        assertThat(program.capacityCurrency()).isEqualTo("USD");
        assertThat(program.usedAmount()).isEqualTo(new BigDecimal("10000000.0000"));
        assertThat(program.utilizationPct()).isEqualTo(new BigDecimal("40.0000"));
        assertThat(program.policyCount()).isEqualTo(4);
        assertThat(program.createdAt()).isEqualTo(Instant.parse("2026-10-20T12:00:00Z"));
        server.verify();
    }

    @Test
    void createsTheProgramWithoutCapacityFields() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROGRAMS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {
                          "name": "Asbestos Primary Program",
                          "programType": "MASS_TORT",
                          "effectiveFrom": "2026-01-01",
                          "effectiveTo": "2027-01-01",
                          "leadPolicyId": "%s",
                          "capacityAmount": null,
                          "capacityCurrency": null
                        }
                        """.formatted(LEAD_POLICY_ID), JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(programJsonWithoutCapacity()));

        ProgramDto program = client.createProgram(TENANT,
                new CreateProgramCommand("Asbestos Primary Program", "MASS_TORT",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), LEAD_POLICY_ID,
                        null, null));

        assertThat(program.capacityAmount()).isNull();
        assertThat(program.capacityCurrency()).isNull();
        assertThat(program.usedAmount()).isNull();
        assertThat(program.utilizationPct()).isNull();
        assertThat(program.policyCount()).isNull();
        server.verify();
    }

    @Test
    void listsProgramsForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROGRAMS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                [%s]
                                """.formatted(programJson())));

        List<ProgramDto> programs = client.findPrograms(TENANT);

        assertThat(programs).singleElement().satisfies(program -> {
            assertThat(program.programId()).isEqualTo(PROGRAM_ID);
            assertThat(program.programNumber()).isEqualTo("PRG-2026-0000001");
            assertThat(program.status()).isEqualTo("ACTIVE");
        });
        server.verify();
    }

    @Test
    void getsOneProgramForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROGRAMS + "/" + PROGRAM_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(programJson()));

        ProgramDto program = client.findProgram(TENANT, PROGRAM_ID);

        assertThat(program.programId()).isEqualTo(PROGRAM_ID);
        assertThat(program.name()).isEqualTo("Asbestos Primary Program");
        server.verify();
    }

    @Test
    void mapsTheExpertIdAndSpecColumnNames() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROGRAMS + "/" + PROGRAM_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "id": "%s",
                                  "program_code": "PRG-2025-0099",
                                  "program_name": "Property Catastrophe Program",
                                  "program_type": "CAPTIVE",
                                  "effective_from": "2025-01-01",
                                  "effective_to": null,
                                  "program_status": "CLOSED",
                                  "lead_policy_id": null,
                                  "capacity_amount": "5000000.0000",
                                  "capacity_currency": "EUR",
                                  "used_amount": "1250000.0000",
                                  "utilization_pct": "25.0000",
                                  "policy_count": 3,
                                  "created_at": "2026-10-20T12:00:00Z"
                                }
                                """.formatted(PROGRAM_ID)));

        ProgramDto program = client.findProgram(TENANT, PROGRAM_ID);

        assertThat(program.programId()).isEqualTo(PROGRAM_ID);
        assertThat(program.programNumber()).isEqualTo("PRG-2025-0099");
        assertThat(program.name()).isEqualTo("Property Catastrophe Program");
        assertThat(program.programType()).isEqualTo("CAPTIVE");
        assertThat(program.effectiveFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(program.effectiveTo()).isNull();
        assertThat(program.status()).isEqualTo("CLOSED");
        assertThat(program.leadPolicyId()).isNull();
        assertThat(program.capacityAmount()).isEqualTo(new BigDecimal("5000000.0000"));
        assertThat(program.capacityCurrency()).isEqualTo("EUR");
        assertThat(program.usedAmount()).isEqualTo(new BigDecimal("1250000.0000"));
        assertThat(program.utilizationPct()).isEqualTo(new BigDecimal("25.0000"));
        assertThat(program.policyCount()).isEqualTo(3);
        assertThat(program.createdAt()).isEqualTo(Instant.parse("2026-10-20T12:00:00Z"));
        server.verify();
    }

    @Test
    void mapsAProgramWithoutCapacityMembers() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROGRAMS + "/" + PROGRAM_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(programJsonWithoutCapacity()));

        ProgramDto program = client.findProgram(TENANT, PROGRAM_ID);

        assertThat(program.programId()).isEqualTo(PROGRAM_ID);
        assertThat(program.capacityAmount()).isNull();
        assertThat(program.capacityCurrency()).isNull();
        assertThat(program.usedAmount()).isNull();
        assertThat(program.utilizationPct()).isNull();
        assertThat(program.policyCount()).isNull();
        server.verify();
    }

    @Test
    void assignsThePolicyProgramForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(POLICY_PROGRAM + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {"programId": "%s"}
                        """.formatted(PROGRAM_ID), JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"5\"")
                        .body(policyJson(PROGRAM_ID, 5)));

        Mutation<PolicyDto> mutation = client.updatePolicyProgram(TENANT, POLICY_ID, PROGRAM_ID);

        assertThat(mutation.etag()).isEqualTo("\"5\"");
        assertThat(mutation.body().programId()).isEqualTo(PROGRAM_ID);
        assertThat(mutation.body().version()).isEqualTo(5);
        server.verify();
    }

    @Test
    void clearsThePolicyProgramWithANullProgramId() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(POLICY_PROGRAM + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {"programId": null}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(policyJson(null, 6)));

        Mutation<PolicyDto> mutation = client.updatePolicyProgram(TENANT, POLICY_ID, null);

        assertThat(mutation.body().programId()).isNull();
        assertThat(mutation.body().version()).isEqualTo(6);
        server.verify();
    }

    @Test
    void preservesTheExpertProgramValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROGRAMS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/invalid-program",
                                  "title": "Invalid program",
                                  "status": 400,
                                  "detail": "programType CUSTOM is not in the program catalog",
                                  "code": "INVALID_PROGRAM_TYPE",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "programType", "code": "UNKNOWN_PROGRAM_TYPE",
                                     "message": "use a governed program type"}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.createProgram(TENANT, command()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_PROGRAM_TYPE");
                    assertThat(api.getMessage()).contains("program catalog");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("programType");
                        assertThat(issue.code()).isEqualTo("UNKNOWN_PROGRAM_TYPE");
                    });
                });
        server.verify();
    }

    @Test
    void preservesTheProgramNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROGRAMS + "/" + PROGRAM_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/program-not-found",
                                  "title": "Program not found",
                                  "status": 404,
                                  "detail": "Program %s does not exist for this tenant",
                                  "code": "PROGRAM_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(PROGRAM_ID)));

        assertThatThrownBy(() -> client.findProgram(TENANT, PROGRAM_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("PROGRAM_NOT_FOUND");
                    assertThat(api.getMessage()).contains(PROGRAM_ID.toString());
                });
        server.verify();
    }

    @Test
    void preservesTheProgramAssignmentConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(POLICY_PROGRAM + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/concurrent-modification",
                                  "title": "Policy was modified",
                                  "status": 409,
                                  "detail": "The policy changed since it was read",
                                  "code": "CONCURRENT_MODIFICATION",
                                  "category": "CONFLICT",
                                  "retryable": true,
                                  "errors": [
                                    {"field": "expectedVersion", "code": "STALE_VERSION",
                                     "message": "reload the policy and retry"}
                                  ],
                                  "meta": {"currentVersion": 6}
                                }
                                """));

        assertThatThrownBy(() -> client.updatePolicyProgram(TENANT, POLICY_ID, PROGRAM_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("CONCURRENT_MODIFICATION");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.code()).isEqualTo("STALE_VERSION"));
                    assertThat(api.meta()).containsEntry("currentVersion", 6);
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertProgramResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(PROGRAMS + "/" + PROGRAM_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findProgram(TENANT, PROGRAM_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    private static CreateProgramCommand command() {
        return new CreateProgramCommand("Asbestos Primary Program", "MASS_TORT",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), LEAD_POLICY_ID,
                new BigDecimal("25000000.0000"), "USD");
    }

    private static String programJson() {
        return """
                {
                  "programId": "%s",
                  "programNumber": "PRG-2026-0000001",
                  "name": "Asbestos Primary Program",
                  "programType": "MASS_TORT",
                  "effectiveFrom": "2026-01-01",
                  "effectiveTo": "2027-01-01",
                  "status": "ACTIVE",
                  "leadPolicyId": "%s",
                  "capacityAmount": "25000000.0000",
                  "capacityCurrency": "USD",
                  "usedAmount": "10000000.0000",
                  "utilizationPct": "40.0000",
                  "policyCount": 4,
                  "createdAt": "2026-10-20T12:00:00Z"
                }
                """.formatted(PROGRAM_ID, LEAD_POLICY_ID);
    }

    private static String programJsonWithoutCapacity() {
        return """
                {
                  "programId": "%s",
                  "programNumber": "PRG-2026-0000001",
                  "name": "Asbestos Primary Program",
                  "programType": "MASS_TORT",
                  "effectiveFrom": "2026-01-01",
                  "effectiveTo": "2027-01-01",
                  "status": "ACTIVE",
                  "leadPolicyId": "%s",
                  "createdAt": "2026-10-20T12:00:00Z"
                }
                """.formatted(PROGRAM_ID, LEAD_POLICY_ID);
    }

    private static String policyJson(UUID programId, int version) {
        String program = programId == null ? "null" : "\"" + programId + "\"";
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "policyNumber": "POL-2026-0000001",
                  "productCode": "CGL",
                  "insuredPartyId": "%s",
                  "status": "ISSUED",
                  "effectiveDate": "2026-01-01",
                  "expirationDate": "2027-01-01",
                  "version": %d,
                  "programId": %s
                }
                """.formatted(POLICY_ID, TENANT, LEAD_POLICY_ID, version, program);
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
}
