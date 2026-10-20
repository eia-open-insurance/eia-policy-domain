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
 * Exercises the real SQL against H2 in PostgreSQL mode: matching, ordering,
 * blank-term behavior, the limit, and tenant isolation.
 */
class PolicySearchRepositoryTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final Instant BASE = Instant.parse("2026-10-01T12:00:00Z");

    private JdbcTemplate jdbc;
    private PolicySearchRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:policy_search_" + UUID.randomUUID().toString().replace("-", "")
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
                    status VARCHAR(32) NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        repository = new PolicySearchRepository(jdbc);
    }

    @Test
    void matchesPolicyNumberAndProductCodeCaseInsensitively() {
        policy(1, TENANT, "POL-2026-0000001", "CGL", "DRAFT", BASE);
        policy(2, TENANT, "POL-2026-0000002", "PROPERTY", "QUOTED", BASE.plus(1, ChronoUnit.MINUTES));
        policy(3, TENANT, "POL-2026-0000099", "AUTO", "BOUND", BASE.plus(2, ChronoUnit.MINUTES));

        assertThat(ids(repository.search(TENANT, "cgl", 10))).containsExactly(id(1));
        assertThat(ids(repository.search(TENANT, "PROPERTY", 10))).containsExactly(id(2));
        assertThat(ids(repository.search(TENANT, "2026-0000001", 10))).containsExactly(id(1));
        assertThat(ids(repository.search(TENANT, "uto", 10))).containsExactly(id(3));
    }

    @Test
    void blankTermReturnsLatestRecordsByUpdatedAtDescending() {
        for (int i = 1; i <= 12; i++) {
            policy(i, TENANT, String.format("POL-2026-%07d", i), "CGL", "DRAFT",
                    BASE.plus(i, ChronoUnit.MINUTES));
        }

        List<PolicySearchRow> latestTen = repository.search(TENANT, "   ", 10);

        assertThat(ids(latestTen)).containsExactly(
                id(12), id(11), id(10), id(9), id(8), id(7), id(6), id(5), id(4), id(3));
        assertThat(repository.search(TENANT, "", 25)).hasSize(12);
    }

    @Test
    void appliesTheLimit() {
        for (int i = 1; i <= 8; i++) {
            policy(i, TENANT, String.format("POL-2026-%07d", i), "CGL", "DRAFT",
                    BASE.plus(i, ChronoUnit.MINUTES));
        }

        assertThat(repository.search(TENANT, "cgl", 3)).hasSize(3);
    }

    @Test
    void keepsResultsTenantScoped() {
        policy(1, TENANT, "POL-SHARED-1", "CGL", "DRAFT", BASE);
        policy(2, OTHER_TENANT, "POL-SHARED-2", "CGL", "DRAFT",
                BASE.plus(1, ChronoUnit.MINUTES));

        assertThat(ids(repository.search(TENANT, "shared", 10))).containsExactly(id(1));
        assertThat(ids(repository.search(TENANT, "", 10))).containsExactly(id(1));
        assertThat(ids(repository.search(OTHER_TENANT, "shared", 10))).containsExactly(id(2));
    }

    @Test
    void treatsSqlWildcardCharactersLiterally() {
        policy(1, TENANT, "POL-2026-0000001", "A_B", "DRAFT", BASE);
        policy(2, TENANT, "POL-2026-0000002", "AXB", "DRAFT", BASE.plus(1, ChronoUnit.MINUTES));

        assertThat(ids(repository.search(TENANT, "a_b", 10))).containsExactly(id(1));
        assertThat(ids(repository.search(TENANT, "%", 10))).isEmpty();
    }

    private void policy(int sequence, UUID tenant, String number, String productCode,
                        String status, Instant updatedAt) {
        jdbc.update("""
                INSERT INTO policy (id, tenant_id, policy_number, product_code, status, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id(sequence), tenant, number, productCode, status, Timestamp.from(updatedAt));
    }

    private static List<UUID> ids(List<PolicySearchRow> rows) {
        return rows.stream().map(PolicySearchRow::id).toList();
    }

    private static UUID id(int sequence) {
        return UUID.fromString(String.format("00000000-0000-7000-8000-%012d", sequence));
    }
}
