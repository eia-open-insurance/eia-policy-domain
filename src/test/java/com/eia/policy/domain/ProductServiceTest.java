package com.eia.policy.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID PRODUCT_ID =
            UUID.fromString("55555555-5555-7555-8555-555555555555");

    private final PolicyClient client = mock(PolicyClient.class);
    private final ProductService service = new ProductService(client);

    @Test
    void delegatesTheListWithTrimmedFilters() {
        List<ProductDto> products = List.of(product());
        when(client.findProducts(TENANT, "liability", "ACTIVE")).thenReturn(products);

        assertThat(service.list(TENANT, "  liability  ", " ACTIVE "))
                .isSameAs(products);
        verify(client).findProducts(TENANT, "liability", "ACTIVE");
    }

    @Test
    void treatsBlankFiltersAsAbsent() {
        when(client.findProducts(TENANT, null, null)).thenReturn(List.of());

        assertThat(service.list(TENANT, "  ", "")).isEmpty();
        verify(client).findProducts(TENANT, null, null);
    }

    @Test
    void delegatesTheGetCreateAndStatusCommands() {
        Mutation<ProductDto> mutation = new Mutation<>(product(), "\"0\"", false);
        CreateProductCommand create = new CreateProductCommand(
                "CGL", "Commercial General Liability", "CGL", "US");
        UpdateProductStatusCommand status = new UpdateProductStatusCommand("RETIRED");

        when(client.findProduct(TENANT, PRODUCT_ID)).thenReturn(mutation);
        when(client.createProduct(TENANT, create)).thenReturn(mutation);
        when(client.updateProductStatus(TENANT, PRODUCT_ID, status)).thenReturn(mutation);

        assertThat(service.get(TENANT, PRODUCT_ID)).isSameAs(mutation);
        assertThat(service.create(TENANT, create)).isSameAs(mutation);
        assertThat(service.updateStatus(TENANT, PRODUCT_ID, status)).isSameAs(mutation);
        verify(client).findProduct(TENANT, PRODUCT_ID);
        verify(client).createProduct(TENANT, create);
        verify(client).updateProductStatus(TENANT, PRODUCT_ID, status);
    }

    private static ProductDto product() {
        Instant created = Instant.parse("2026-10-21T12:00:00Z");
        return new ProductDto(PRODUCT_ID, "CGL", "Commercial General Liability", "CGL",
                "US", "ACTIVE", 0, created, created);
    }
}
