package com.eia.policy.domain;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
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

@WebMvcTest(LegalEntityController.class)
@Import(LegalEntityService.class)
class LegalEntityControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID LEGAL_ENTITY_ID =
            UUID.fromString("77777777-7777-7777-8777-777777777777");
    private static final String DIRECT = "DIRECT";
    private static final String LEGAL_ENTITIES = "/api/v1/legal-entities";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PolicyClient client;

    @Test
    void createsTheLegalEntityAndReturnsCreated() throws Exception {
        when(client.createLegalEntity(eq(TENANT), any(CreateLegalEntityCommand.class)))
                .thenReturn(legalEntity());

        mvc.perform(post(LEGAL_ENTITIES)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "ABC-INS",
                                  "name": "ABC Insurance Company",
                                  "entityType": "INSURER",
                                  "jurisdiction": "USA",
                                  "regulator": "California Department of Insurance",
                                  "licenseNumber": "CA-1234567"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", LEGAL_ENTITIES + "/" + LEGAL_ENTITY_ID))
                .andExpect(jsonPath("$.legalEntityId").value(LEGAL_ENTITY_ID.toString()))
                .andExpect(jsonPath("$.code").value("ABC-INS"))
                .andExpect(jsonPath("$.name").value("ABC Insurance Company"))
                .andExpect(jsonPath("$.entityType").value("INSURER"))
                .andExpect(jsonPath("$.jurisdiction").value("USA"))
                .andExpect(jsonPath("$.regulator")
                        .value("California Department of Insurance"))
                .andExpect(jsonPath("$.licenseNumber").value("CA-1234567"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-20T12:00:00Z"));

        ArgumentCaptor<CreateLegalEntityCommand> captor =
                ArgumentCaptor.forClass(CreateLegalEntityCommand.class);
        verify(client).createLegalEntity(eq(TENANT), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CreateLegalEntityCommand("ABC-INS",
                "ABC Insurance Company", "INSURER", "USA",
                "California Department of Insurance", "CA-1234567"));
    }

    @Test
    void listsLegalEntities() throws Exception {
        when(client.findLegalEntities(TENANT)).thenReturn(List.of(legalEntity()));

        mvc.perform(get(LEGAL_ENTITIES)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].legalEntityId").value(LEGAL_ENTITY_ID.toString()))
                .andExpect(jsonPath("$[0].code").value("ABC-INS"))
                .andExpect(jsonPath("$[0].name").value("ABC Insurance Company"))
                .andExpect(jsonPath("$[0].entityType").value("INSURER"))
                .andExpect(jsonPath("$[0].jurisdiction").value("USA"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void returnsAnEmptyLegalEntityArray() throws Exception {
        when(client.findLegalEntities(TENANT)).thenReturn(List.of());

        mvc.perform(get(LEGAL_ENTITIES).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getsOneLegalEntity() throws Exception {
        when(client.findLegalEntity(TENANT, LEGAL_ENTITY_ID)).thenReturn(legalEntity());

        mvc.perform(get(LEGAL_ENTITIES + "/{legalEntityId}", LEGAL_ENTITY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalEntityId").value(LEGAL_ENTITY_ID.toString()))
                .andExpect(jsonPath("$.code").value("ABC-INS"))
                .andExpect(jsonPath("$.name").value("ABC Insurance Company"))
                .andExpect(jsonPath("$.entityType").value("INSURER"))
                .andExpect(jsonPath("$.jurisdiction").value("USA"))
                .andExpect(jsonPath("$.regulator")
                        .value("California Department of Insurance"))
                .andExpect(jsonPath("$.licenseNumber").value("CA-1234567"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-20T12:00:00Z"));
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(client.findLegalEntity(TENANT, LEGAL_ENTITY_ID)).thenReturn(legalEntity());

        mvc.perform(get(LEGAL_ENTITIES + "/{legalEntityId}", LEGAL_ENTITY_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalEntityId").value(LEGAL_ENTITY_ID.toString()));
    }

    @Test
    void acceptsTheDirectBookCaseInsensitively() throws Exception {
        when(client.findLegalEntities(TENANT)).thenReturn(List.of(legalEntity()));

        mvc.perform(get(LEGAL_ENTITIES)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "direct"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(get(LEGAL_ENTITIES)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(client);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get(LEGAL_ENTITIES).header("X-EIA-Book", DIRECT))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(client);
    }

    @Test
    void validatesTheCreateLegalEntityBody() throws Exception {
        mvc.perform(post(LEGAL_ENTITIES)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("code", "name", "entityType", "jurisdiction")));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsAMalformedLegalEntityId() throws Exception {
        mvc.perform(get(LEGAL_ENTITIES + "/not-a-uuid")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("legalEntityId"));

        verifyNoInteractions(client);
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(client.createLegalEntity(eq(TENANT), any(CreateLegalEntityCommand.class)))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ENTITY_TYPE",
                        "entityType CUSTOM is not in the legal entity catalog",
                        List.of(new ApiException.FieldIssue("entityType", "UNKNOWN_ENTITY_TYPE",
                                "use a governed legal entity type")),
                        Map.of()));

        mvc.perform(post(LEGAL_ENTITIES)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "ABC-INS",
                                  "name": "ABC Insurance Company",
                                  "entityType": "CUSTOM",
                                  "jurisdiction": "USA"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_ENTITY_TYPE"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("entityType"))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_ENTITY_TYPE"));
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(client.findLegalEntity(TENANT, LEGAL_ENTITY_ID)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, "LEGAL_ENTITY_NOT_FOUND",
                "Legal entity " + LEGAL_ENTITY_ID + " does not exist for this tenant"));

        mvc.perform(get(LEGAL_ENTITIES + "/{legalEntityId}", LEGAL_ENTITY_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("LEGAL_ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(client.createLegalEntity(eq(TENANT), any(CreateLegalEntityCommand.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "DUPLICATE_LEGAL_ENTITY_CODE",
                        "Entity code ABC-INS is already in use for this tenant",
                        List.of(new ApiException.FieldIssue("code", "DUPLICATE_CODE",
                                "choose a unique entity code")),
                        Map.of()));

        mvc.perform(post(LEGAL_ENTITIES)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "ABC-INS",
                                  "name": "ABC Insurance Company",
                                  "entityType": "INSURER",
                                  "jurisdiction": "USA"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DUPLICATE_LEGAL_ENTITY_CODE"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("code"));
    }

    private static LegalEntityDto legalEntity() {
        return new LegalEntityDto(LEGAL_ENTITY_ID, "ABC-INS", "ABC Insurance Company", "INSURER",
                "USA", "California Department of Insurance", "CA-1234567", "ACTIVE",
                Instant.parse("2026-10-20T12:00:00Z"));
    }
}
