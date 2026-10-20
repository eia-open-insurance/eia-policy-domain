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
import java.time.LocalDate;
import java.util.List;
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

@WebMvcTest(ProgramController.class)
@Import(ProgramService.class)
class ProgramControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID PROGRAM_ID = UUID.fromString("55555555-5555-7555-8555-555555555555");
    private static final UUID LEAD_POLICY_ID =
            UUID.fromString("44444444-4444-7444-8444-444444444444");
    private static final String DIRECT = "DIRECT";
    private static final String PROGRAMS = "/api/v1/programs";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PolicyClient client;

    @Test
    void createsTheProgramAndReturnsCreated() throws Exception {
        when(client.createProgram(eq(TENANT), any(CreateProgramCommand.class)))
                .thenReturn(program());

        mvc.perform(post(PROGRAMS)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Asbestos Primary Program",
                                  "programType": "MASS_TORT",
                                  "effectiveFrom": "2026-01-01",
                                  "effectiveTo": "2027-01-01",
                                  "leadPolicyId": "%s"
                                }
                                """.formatted(LEAD_POLICY_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", PROGRAMS + "/" + PROGRAM_ID))
                .andExpect(jsonPath("$.programId").value(PROGRAM_ID.toString()))
                .andExpect(jsonPath("$.programNumber").value("PRG-2026-0000001"))
                .andExpect(jsonPath("$.name").value("Asbestos Primary Program"))
                .andExpect(jsonPath("$.programType").value("MASS_TORT"))
                .andExpect(jsonPath("$.effectiveFrom").value("2026-01-01"))
                .andExpect(jsonPath("$.effectiveTo").value("2027-01-01"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.leadPolicyId").value(LEAD_POLICY_ID.toString()))
                .andExpect(jsonPath("$.createdAt").value("2026-10-20T12:00:00Z"));

        ArgumentCaptor<CreateProgramCommand> captor =
                ArgumentCaptor.forClass(CreateProgramCommand.class);
        verify(client).createProgram(eq(TENANT), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CreateProgramCommand(
                "Asbestos Primary Program", "MASS_TORT", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), LEAD_POLICY_ID));
    }

    @Test
    void listsPrograms() throws Exception {
        when(client.findPrograms(TENANT)).thenReturn(List.of(program()));

        mvc.perform(get(PROGRAMS)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].programId").value(PROGRAM_ID.toString()))
                .andExpect(jsonPath("$[0].programNumber").value("PRG-2026-0000001"))
                .andExpect(jsonPath("$[0].name").value("Asbestos Primary Program"))
                .andExpect(jsonPath("$[0].programType").value("MASS_TORT"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void returnsAnEmptyProgramArray() throws Exception {
        when(client.findPrograms(TENANT)).thenReturn(List.of());

        mvc.perform(get(PROGRAMS).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getsOneProgram() throws Exception {
        when(client.findProgram(TENANT, PROGRAM_ID)).thenReturn(program());

        mvc.perform(get(PROGRAMS + "/{programId}", PROGRAM_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").value(PROGRAM_ID.toString()))
                .andExpect(jsonPath("$.programNumber").value("PRG-2026-0000001"))
                .andExpect(jsonPath("$.name").value("Asbestos Primary Program"))
                .andExpect(jsonPath("$.programType").value("MASS_TORT"))
                .andExpect(jsonPath("$.effectiveFrom").value("2026-01-01"))
                .andExpect(jsonPath("$.effectiveTo").value("2027-01-01"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.leadPolicyId").value(LEAD_POLICY_ID.toString()));
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(client.findProgram(TENANT, PROGRAM_ID)).thenReturn(program());

        mvc.perform(get(PROGRAMS + "/{programId}", PROGRAM_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").value(PROGRAM_ID.toString()));
    }

    @Test
    void acceptsTheDirectBookCaseInsensitively() throws Exception {
        when(client.findPrograms(TENANT)).thenReturn(List.of(program()));

        mvc.perform(get(PROGRAMS)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "direct"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(get(PROGRAMS)
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
        mvc.perform(get(PROGRAMS).header("X-EIA-Book", DIRECT))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(client);
    }

    @Test
    void validatesTheCreateProgramBody() throws Exception {
        mvc.perform(post(PROGRAMS)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("name", "programType", "effectiveFrom", "effectiveTo")));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsAnEffectiveToBeforeEffectiveFrom() throws Exception {
        mvc.perform(post(PROGRAMS)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Asbestos Primary Program",
                                  "programType": "MASS_TORT",
                                  "effectiveFrom": "2027-01-01",
                                  "effectiveTo": "2026-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("effectiveTo"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_DATE_RANGE"));

        verifyNoInteractions(client);
    }

    @Test
    void preservesTheExpertProgramNotFoundProblem() throws Exception {
        when(client.findProgram(TENANT, PROGRAM_ID)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, "PROGRAM_NOT_FOUND",
                "Program " + PROGRAM_ID + " does not exist for this tenant"));

        mvc.perform(get(PROGRAMS + "/{programId}", PROGRAM_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PROGRAM_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));
    }

    private static ProgramDto program() {
        return new ProgramDto(PROGRAM_ID, "PRG-2026-0000001", "Asbestos Primary Program",
                "MASS_TORT", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "ACTIVE",
                LEAD_POLICY_ID, Instant.parse("2026-10-20T12:00:00Z"));
    }
}
