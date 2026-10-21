package com.eia.policy.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the insured-party filter SQL against H2 in PostgreSQL mode:
 * matching, no match, tenant isolation, stable ordering, and paging.
 */
class PolicyQueryRepositoryTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID PARTY = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID OTHER_PARTY = UUID.fromString("44444444-4444-7444-8444-444444444444");
    private static final Instant BASE = Instant.parse("2026-10-01T12:00:00Z");

    private JdbcTemplate jdbc;
    private PolicyQueryRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:policy_query_" + UUID.randomUUID().toString().replace("-", "")
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE policy (
                    id UUID PRIMARY KEY,
                    tenant_id UUID NOT NULL,
                    policy_number VARCHAR(32) NOT NULL,
                    product_code VARCHAR(32) NOT NULL,
                    insured_party_id UUID NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    effective_date DATE NOT NULL,
                    expiration_date DATE NOT NULL,
                    premium_amount NUMERIC(19,4) NOT NULL,
                    premium_currency CHAR(3) NOT NULL,
                    limit_amount NUMERIC(19,4) NOT NULL,
                    limit_currency CHAR(3) NOT NULL,
                    renewed_from_policy_id UUID,
                    program_id UUID,
                    legal_entity_id UUID,
                    version INTEGER NOT NULL DEFAULT 0,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        repository = new PolicyQueryRepository(jdbc);
    }

    @Test
    void returnsOnlyPoliciesForTheTenantAndInsuredParty() {
        policy(1, TENANT, PARTY, "DRAFT", BASE);
        policy(2, TENANT, OTHER_PARTY, "DRAFT", BASE.plus(1, ChronoUnit.MINUTES));
        policy(3, OTHER_TENANT, PARTY, "DRAFT", BASE.plus(2, ChronoUnit.MINUTES));

        PolicyPageData page = repository.findPageByInsuredParty(TENANT, PARTY, 0, 20);

        assertThat(ids(page.items())).containsExactly(id(1));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(policy -> {
            assertThat(policy.tenantId()).isEqualTo(TENANT);
            assertThat(policy.insuredPartyId()).isEqualTo(PARTY);
            assertThat(policy.policyNumber()).isEqualTo("POL-2026-0000001");
            assertThat(policy.productCode()).isEqualTo("CGL");
            assertThat(policy.status()).isEqualTo("DRAFT");
        });
    }

    @Test
    void mapsRenewalLineageFromThePolicyRow() {
        UUID predecessor = UUID.fromString("99999999-9999-7999-8999-999999999999");
        policy(1, TENANT, PARTY, "DRAFT", BASE);
        jdbc.update("UPDATE policy SET renewed_from_policy_id = ? WHERE id = ?",
                predecessor, id(1));

        PolicyPageData page = repository.findPageByInsuredParty(TENANT, PARTY, 0, 20);

        assertThat(page.items()).singleElement().satisfies(policy ->
                assertThat(policy.renewedFromPolicyId()).isEqualTo(predecessor));
    }

    @Test
    void mapsTheProgramLinkFromThePolicyRow() {
        UUID programId = UUID.fromString("55555555-5555-7555-8555-555555555555");
        policy(1, TENANT, PARTY, "DRAFT", BASE);
        jdbc.update("UPDATE policy SET program_id = ? WHERE id = ?", programId, id(1));

        PolicyPageData page = repository.findPageByInsuredParty(TENANT, PARTY, 0, 20);

        assertThat(page.items()).singleElement().satisfies(policy ->
                assertThat(policy.programId()).isEqualTo(programId));
    }

    @Test
    void mapsTheIssuingLegalEntityFromThePolicyRow() {
        UUID legalEntityId = UUID.fromString("66666666-6666-7666-8666-666666666666");
        policy(1, TENANT, PARTY, "DRAFT", BASE);
        jdbc.update("UPDATE policy SET legal_entity_id = ? WHERE id = ?", legalEntityId, id(1));

        PolicyPageData page = repository.findPageByInsuredParty(TENANT, PARTY, 0, 20);

        assertThat(page.items()).singleElement().satisfies(policy ->
                assertThat(policy.legalEntityId()).isEqualTo(legalEntityId));
    }

    @Test
    void returnsAnEmptyPageWhenNoPolicyMatches() {        policy(1, TENANT, OTHER_PARTY, "DRAFT", BASE);

        PolicyPageData page = repository.findPageByInsuredParty(TENANT, PARTY, 0, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    void pagesFilteredPoliciesWithAStableOrderAndTotal() {
        for (int i = 1; i <= 7; i++) {
            policy(i, TENANT, PARTY, "DRAFT", BASE.plus(i, ChronoUnit.MINUTES));
        }

        PolicyPageData first = repository.findPageByInsuredParty(TENANT, PARTY, 0, 3);
        PolicyPageData last = repository.findPageByInsuredParty(TENANT, PARTY, 2, 3);

        assertThat(ids(first.items())).containsExactly(id(7), id(6), id(5));
        assertThat(first.total()).isEqualTo(7);
        assertThat(ids(last.items())).containsExactly(id(1));
        assertThat(last.total()).isEqualTo(7);
    }

    @Test
    void ordersByUpdatedAtDescendingWithIdAsTheTieBreaker() {
        policy(2, TENANT, PARTY, "QUOTED", BASE);
        policy(1, TENANT, PARTY, "DRAFT", BASE);
        policy(3, TENANT, PARTY, "BOUND", BASE.plus(1, ChronoUnit.MINUTES));

        PolicyPageData page = repository.findPageByInsuredParty(TENANT, PARTY, 0, 20);

        assertThat(ids(page.items())).containsExactly(id(3), id(1), id(2));
    }

    private void policy(int sequence, UUID tenant, UUID insuredPartyId, String status,
                        Instant updatedAt) {
        jdbc.update("""
                INSERT INTO policy (id, tenant_id, policy_number, product_code, insured_party_id,
                                    status, effective_date, expiration_date, premium_amount,
                                    premium_currency, limit_amount, limit_currency, version,
                                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, DATE '2026-01-01', DATE '2027-01-01',
                        125000.0000, 'USD', 2000000.0000, 'USD', 0, ?, ?)
                """, id(sequence), tenant, String.format("POL-2026-%07d", sequence), "CGL",
                insuredPartyId, status, Timestamp.from(updatedAt.minusSeconds(60)),
                Timestamp.from(updatedAt));
    }

    private static List<UUID> ids(List<PolicyDto> rows) {
        return rows.stream().map(PolicyDto::id).toList();
    }

    private static UUID id(int sequence) {
        return UUID.fromString(String.format("00000000-0000-7000-8000-%012d", sequence));
    }
}
