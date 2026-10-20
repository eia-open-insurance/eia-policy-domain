package com.eia.policy.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Maps tenant-scoped Policy rows to the common global-search envelope.
 */
@Service
public class PolicySearchService {
    static final String TYPE = "POLICY";

    private final PolicySearchRepository repository;

    public PolicySearchService(PolicySearchRepository repository) {
        this.repository = repository;
    }

    public SearchResponse search(UUID tenantId, String q, Integer limit) {
        SearchQuery query = SearchQuery.of(q, limit);
        List<SearchItem> items = repository.search(tenantId, query.term(), query.limit()).stream()
                .map(PolicySearchService::toItem)
                .toList();
        return new SearchResponse(query.term(), query.limit(), items);
    }

    private static SearchItem toItem(PolicySearchRow row) {
        return new SearchItem(
                row.id(),
                TYPE,
                row.policyNumber(),
                row.productCode() + " \u00B7 " + row.status(),
                row.status(),
                "/policy?record=" + row.id()
        );
    }
}
