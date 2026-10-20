package com.eia.policy.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only, tenant-scoped queries behind the Policy global search.
 *
 * <p>Matching is a case-insensitive contains over {@code policy_number} and
 * {@code product_code}. A blank term returns the most recently updated
 * policies. The policy table is DIRECT-only, so the book wall is enforced at
 * the API boundary before this query runs.</p>
 */
@Repository
public class PolicySearchRepository {
    private static final String SELECT = """
            SELECT id, policy_number, product_code, status
              FROM policy
            """;

    private static final RowMapper<PolicySearchRow> MAPPER = (rs, rowNum) -> new PolicySearchRow(
            rs.getObject("id", UUID.class),
            rs.getString("policy_number"),
            rs.getString("product_code"),
            rs.getString("status")
    );

    private final JdbcTemplate jdbc;

    public PolicySearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PolicySearchRow> search(UUID tenantId, String term, int limit) {
        if (term == null || term.isBlank()) {
            return jdbc.query(SELECT + """
                     WHERE tenant_id = ?
                     ORDER BY updated_at DESC, id ASC
                     LIMIT ?
                    """, MAPPER, tenantId, limit);
        }

        String pattern = containsPattern(term);
        return jdbc.query(SELECT + """
                 WHERE tenant_id = ?
                   AND (LOWER(policy_number) LIKE ? ESCAPE '!'
                        OR LOWER(product_code) LIKE ? ESCAPE '!')
                 ORDER BY updated_at DESC, id ASC
                 LIMIT ?
                """, MAPPER, tenantId, pattern, pattern, limit);
    }

    /**
     * Builds a contains pattern with {@code !} as the escape character so a
     * caller-supplied {@code %}, {@code _}, or {@code !} is matched literally.
     */
    static String containsPattern(String term) {
        String escaped = term.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
