package com.eia.policy.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only, tenant-scoped queries that the public Policy list needs but the
 * Policy expert does not expose.
 *
 * <p>The expert list endpoint has no insured-party filter, so the filtered read
 * runs against the same {@code eia_policy} data store used by the global
 * search. The unfiltered list keeps proxying the expert unchanged, and the
 * filter SQL always binds both {@code tenant_id} and {@code insured_party_id}
 * before ordering and paging, matching the {@code updated_at DESC, id ASC}
 * order of the expert list.</p>
 */
@Repository
public class PolicyQueryRepository {
    private static final String SELECT = """
            SELECT id, tenant_id, policy_number, product_code, insured_party_id, status,
                   effective_date, expiration_date, premium_amount, premium_currency,
                   limit_amount, limit_currency, version, created_at, updated_at,
                   renewed_from_policy_id, program_id
              FROM policy
            """;

    private static final RowMapper<PolicyDto> MAPPER = (rs, rowNum) -> new PolicyDto(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("policy_number"),
            rs.getString("product_code"),
            rs.getObject("insured_party_id", UUID.class),
            rs.getString("status"),
            rs.getObject("effective_date", LocalDate.class),
            rs.getObject("expiration_date", LocalDate.class),
            rs.getBigDecimal("premium_amount"),
            rs.getString("premium_currency"),
            rs.getBigDecimal("limit_amount"),
            rs.getString("limit_currency"),
            rs.getInt("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getObject("renewed_from_policy_id", UUID.class),
            rs.getObject("program_id", UUID.class)
    );

    private final JdbcTemplate jdbc;

    public PolicyQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PolicyPageData findPageByInsuredParty(UUID tenantId, UUID insuredPartyId,
                                                 int page, int size) {
        long offset = (long) page * size;
        List<PolicyDto> items = jdbc.query(SELECT + """
                 WHERE tenant_id = ? AND insured_party_id = ?
                 ORDER BY updated_at DESC, id ASC
                 LIMIT ? OFFSET ?
                """, MAPPER, tenantId, insuredPartyId, size, offset);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM policy
                 WHERE tenant_id = ? AND insured_party_id = ?
                """, Long.class, tenantId, insuredPartyId);
        return new PolicyPageData(items, total == null ? 0L : total);
    }
}
