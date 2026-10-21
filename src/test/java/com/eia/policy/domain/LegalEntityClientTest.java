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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class LegalEntityClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID LEGAL_ENTITY_ID =
            UUID.fromString("77777777-7777-7777-8777-777777777777");
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final String BASE = "http://expert";
    private static final String LEGAL_ENTITIES = BASE + "/internal/v1/legal-entities";
    private static final String POLICY_LEGAL_ENTITY =
            BASE + "/internal/v1/policies/" + POLICY_ID + "/legal-entity";

    @Test
    void createsTheLegalEntityForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LEGAL_ENTITIES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {
                          "code": "ABC-INS",
                          "name": "ABC Insurance Company",
                          "entityType": "INSURER",
                          "jurisdiction": "USA",
                          "regulator": "California Department of Insurance",
                          "licenseNumber": "CA-1234567"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(legalEntityJson()));

        LegalEntityDto legalEntity = client.createLegalEntity(TENANT, command());

        assertThat(legalEntity.legalEntityId()).isEqualTo(LEGAL_ENTITY_ID);
        assertThat(legalEntity.code()).isEqualTo("ABC-INS");
        assertThat(legalEntity.name()).isEqualTo("ABC Insurance Company");
        assertThat(legalEntity.entityType()).isEqualTo("INSURER");
        assertThat(legalEntity.jurisdiction()).isEqualTo("USA");
        assertThat(legalEntity.regulator()).isEqualTo("California Department of Insurance");
        assertThat(legalEntity.licenseNumber()).isEqualTo("CA-1234567");
        assertThat(legalEntity.status()).isEqualTo("ACTIVE");
        assertThat(legalEntity.createdAt()).isEqualTo(Instant.parse("2026-10-20T12:00:00Z"));
        server.verify();
    }

    @Test
    void listsLegalEntitiesForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LEGAL_ENTITIES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                [%s]
                                """.formatted(legalEntityJson())));

        List<LegalEntityDto> legalEntities = client.findLegalEntities(TENANT);

        assertThat(legalEntities).singleElement().satisfies(legalEntity -> {
            assertThat(legalEntity.legalEntityId()).isEqualTo(LEGAL_ENTITY_ID);
            assertThat(legalEntity.code()).isEqualTo("ABC-INS");
            assertThat(legalEntity.entityType()).isEqualTo("INSURER");
            assertThat(legalEntity.status()).isEqualTo("ACTIVE");
        });
        server.verify();
    }

    @Test
    void getsOneLegalEntityForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LEGAL_ENTITIES + "/" + LEGAL_ENTITY_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(legalEntityJson()));

        LegalEntityDto legalEntity = client.findLegalEntity(TENANT, LEGAL_ENTITY_ID);

        assertThat(legalEntity.legalEntityId()).isEqualTo(LEGAL_ENTITY_ID);
        assertThat(legalEntity.name()).isEqualTo("ABC Insurance Company");
        assertThat(legalEntity.licenseNumber()).isEqualTo("CA-1234567");
        server.verify();
    }

    @Test
    void mapsTheExpertIdAndSpecColumnNames() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LEGAL_ENTITIES + "/" + LEGAL_ENTITY_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "id": "%s",
                                  "entity_code": "ABC-HLD",
                                  "legal_name": "ABC Holdings Corporation",
                                  "entity_type": "HOLDING",
                                  "domicile_country": "USA",
                                  "regulator": null,
                                  "license_number": null,
                                  "status": "LIQUIDATING",
                                  "created_at": "2026-10-20T12:00:00Z"
                                }
                                """.formatted(LEGAL_ENTITY_ID)));

        LegalEntityDto legalEntity = client.findLegalEntity(TENANT, LEGAL_ENTITY_ID);

        assertThat(legalEntity.legalEntityId()).isEqualTo(LEGAL_ENTITY_ID);
        assertThat(legalEntity.code()).isEqualTo("ABC-HLD");
        assertThat(legalEntity.name()).isEqualTo("ABC Holdings Corporation");
        assertThat(legalEntity.entityType()).isEqualTo("HOLDING");
        assertThat(legalEntity.jurisdiction()).isEqualTo("USA");
        assertThat(legalEntity.regulator()).isNull();
        assertThat(legalEntity.licenseNumber()).isNull();
        assertThat(legalEntity.status()).isEqualTo("LIQUIDATING");
        assertThat(legalEntity.createdAt()).isEqualTo(Instant.parse("2026-10-20T12:00:00Z"));
        server.verify();
    }

    @Test
    void assignsThePolicyLegalEntityForwardingTenantAndDirectBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(POLICY_LEGAL_ENTITY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {"legalEntityId": "%s"}
                        """.formatted(LEGAL_ENTITY_ID), JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"7\"")
                        .body(policyJson(LEGAL_ENTITY_ID, 7)));

        Mutation<PolicyDto> mutation =
                client.updatePolicyLegalEntity(TENANT, POLICY_ID, LEGAL_ENTITY_ID);

        assertThat(mutation.etag()).isEqualTo("\"7\"");
        assertThat(mutation.body().legalEntityId()).isEqualTo(LEGAL_ENTITY_ID);
        assertThat(mutation.body().version()).isEqualTo(7);
        server.verify();
    }

    @Test
    void preservesTheExpertValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LEGAL_ENTITIES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/invalid-legal-entity",
                                  "title": "Invalid legal entity",
                                  "status": 400,
                                  "detail": "entityType CUSTOM is not in the legal entity catalog",
                                  "code": "INVALID_ENTITY_TYPE",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "entityType", "code": "UNKNOWN_ENTITY_TYPE",
                                     "message": "use a governed legal entity type"}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.createLegalEntity(TENANT, command()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_ENTITY_TYPE");
                    assertThat(api.getMessage()).contains("legal entity catalog");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("entityType");
                        assertThat(issue.code()).isEqualTo("UNKNOWN_ENTITY_TYPE");
                    });
                });
        server.verify();
    }

    @Test
    void preservesTheLegalEntityNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LEGAL_ENTITIES + "/" + LEGAL_ENTITY_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/legal-entity-not-found",
                                  "title": "Legal entity not found",
                                  "status": 404,
                                  "detail": "Legal entity %s does not exist for this tenant",
                                  "code": "LEGAL_ENTITY_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(LEGAL_ENTITY_ID)));

        assertThatThrownBy(() -> client.findLegalEntity(TENANT, LEGAL_ENTITY_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("LEGAL_ENTITY_NOT_FOUND");
                    assertThat(api.getMessage()).contains(LEGAL_ENTITY_ID.toString());
                });
        server.verify();
    }

    @Test
    void preservesTheDuplicateCodeConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LEGAL_ENTITIES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/duplicate-legal-entity-code",
                                  "title": "Duplicate legal entity code",
                                  "status": 409,
                                  "detail": "Entity code ABC-INS is already in use for this tenant",
                                  "code": "DUPLICATE_LEGAL_ENTITY_CODE",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "code", "code": "DUPLICATE_CODE",
                                     "message": "choose a unique entity code"}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.createLegalEntity(TENANT, command()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("DUPLICATE_LEGAL_ENTITY_CODE");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.code()).isEqualTo("DUPLICATE_CODE"));
                });
        server.verify();
    }

    @Test
    void preservesThePolicyAssignmentConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(POLICY_LEGAL_ENTITY + "?tenantId=" + TENANT))
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
                                  "meta": {"currentVersion": 8}
                                }
                                """));

        assertThatThrownBy(() ->
                client.updatePolicyLegalEntity(TENANT, POLICY_ID, LEGAL_ENTITY_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("CONCURRENT_MODIFICATION");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.code()).isEqualTo("STALE_VERSION"));
                    assertThat(api.meta()).containsEntry("currentVersion", 8);
                });
        server.verify();
    }

    @Test
    void preservesThePolicyAssignmentNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(POLICY_LEGAL_ENTITY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/policy-not-found",
                                  "title": "Policy not found",
                                  "status": 404,
                                  "detail": "Policy %s does not exist for this tenant",
                                  "code": "POLICY_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false
                                }
                                """.formatted(POLICY_ID)));

        assertThatThrownBy(() ->
                client.updatePolicyLegalEntity(TENANT, POLICY_ID, LEGAL_ENTITY_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("POLICY_NOT_FOUND");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertLegalEntityResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LEGAL_ENTITIES + "/" + LEGAL_ENTITY_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findLegalEntity(TENANT, LEGAL_ENTITY_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    private static CreateLegalEntityCommand command() {
        return new CreateLegalEntityCommand("ABC-INS", "ABC Insurance Company", "INSURER", "USA",
                "California Department of Insurance", "CA-1234567");
    }

    private static String legalEntityJson() {
        return """
                {
                  "legalEntityId": "%s",
                  "code": "ABC-INS",
                  "name": "ABC Insurance Company",
                  "entityType": "INSURER",
                  "jurisdiction": "USA",
                  "regulator": "California Department of Insurance",
                  "licenseNumber": "CA-1234567",
                  "status": "ACTIVE",
                  "createdAt": "2026-10-20T12:00:00Z"
                }
                """.formatted(LEGAL_ENTITY_ID);
    }

    private static String policyJson(UUID legalEntityId, int version) {
        String issuer = legalEntityId == null ? "null" : "\"" + legalEntityId + "\"";
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "policyNumber": "POL-2026-0000001",
                  "productCode": "CGL",
                  "insuredPartyId": "22222222-2222-7222-8222-222222222222",
                  "status": "ISSUED",
                  "effectiveDate": "2026-01-01",
                  "expirationDate": "2027-01-01",
                  "version": %d,
                  "legalEntityId": %s
                }
                """.formatted(POLICY_ID, TENANT, version, issuer);
    }

    /**
     * The production RestClient builder is supplied by Spring Boot and writes
     * ISO dates; a plain builder would serialize {@link java.time.LocalDate} as
     * an array.
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
