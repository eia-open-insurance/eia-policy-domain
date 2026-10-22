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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(ProductService.class)
class ProductControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID PRODUCT_ID =
            UUID.fromString("55555555-5555-7555-8555-555555555555");
    private static final String DIRECT = "DIRECT";
    private static final String PRODUCTS = "/api/v1/products";
    private static final String CREATE_BODY = """
            {
              "productCode": "CGL",
              "productName": "Commercial General Liability",
              "productLine": "CGL",
              "jurisdiction": "US"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PolicyClient client;

    @Test
    void listsProductsWithSearchAndStatusFilters() throws Exception {
        when(client.findProducts(TENANT, "liability", "ACTIVE"))
                .thenReturn(List.of(product("ACTIVE", 0)));

        mvc.perform(get(PRODUCTS)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .param("q", "liability")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$[0].productCode").value("CGL"))
                .andExpect(jsonPath("$[0].productName")
                        .value("Commercial General Liability"))
                .andExpect(jsonPath("$[0].productLine").value("CGL"))
                .andExpect(jsonPath("$[0].jurisdiction").value("US"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[0].tenantId").doesNotExist());

        verify(client).findProducts(TENANT, "liability", "ACTIVE");
    }

    @Test
    void returnsAnEmptyProductArray() throws Exception {
        when(client.findProducts(TENANT, null, null)).thenReturn(List.of());

        mvc.perform(get(PRODUCTS).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchIsAnAliasForTheSameCatalogRead() throws Exception {
        when(client.findProducts(TENANT, "talc", "ACTIVE"))
                .thenReturn(List.of(product("ACTIVE", 0)));

        mvc.perform(get(PRODUCTS + "/search")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .param("q", "talc")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$[0].productCode").value("CGL"));
    }

    @Test
    void getsOneProductWithItsVersionEtag() throws Exception {
        when(client.findProduct(TENANT, PRODUCT_ID))
                .thenReturn(new Mutation<>(product("ACTIVE", 3), "\"3\"", false));

        mvc.perform(get(PRODUCTS + "/{productId}", PRODUCT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.productCode").value("CGL"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void fallsBackToTheProjectionVersionWhenTheExpertOmitsTheEtag() throws Exception {
        when(client.findProduct(TENANT, PRODUCT_ID))
                .thenReturn(new Mutation<>(product("RETIRED", 4), null, false));

        mvc.perform(get(PRODUCTS + "/{productId}", PRODUCT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""))
                .andExpect(jsonPath("$.status").value("RETIRED"));
    }

    @Test
    void createsTheProductAndReturnsCreatedWithLocationAndEtag() throws Exception {
        when(client.createProduct(eq(TENANT), any(CreateProductCommand.class)))
                .thenReturn(new Mutation<>(product("ACTIVE", 0), "\"0\"", false));

        mvc.perform(post(PRODUCTS)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", PRODUCTS + "/" + PRODUCT_ID))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.productCode").value("CGL"))
                .andExpect(jsonPath("$.productName")
                        .value("Commercial General Liability"))
                .andExpect(jsonPath("$.productLine").value("CGL"))
                .andExpect(jsonPath("$.jurisdiction").value("US"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0));

        ArgumentCaptor<CreateProductCommand> captor =
                ArgumentCaptor.forClass(CreateProductCommand.class);
        verify(client).createProduct(eq(TENANT), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CreateProductCommand(
                "CGL", "Commercial General Liability", "CGL", "US"));
    }

    @Test
    void acceptsTheShortCreateAliases() throws Exception {
        when(client.createProduct(eq(TENANT), any(CreateProductCommand.class)))
                .thenReturn(new Mutation<>(product("ACTIVE", 0), "\"0\"", false));

        mvc.perform(post(PRODUCTS)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "UMBRELLA", "name": "Umbrella",
                                 "line": "UMBRELLA", "jurisdiction": "US"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateProductCommand> captor =
                ArgumentCaptor.forClass(CreateProductCommand.class);
        verify(client).createProduct(eq(TENANT), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CreateProductCommand(
                "UMBRELLA", "Umbrella", "UMBRELLA", "US"));
    }

    @Test
    void retiresAProductAndReturnsTheNewVersionEtag() throws Exception {
        when(client.updateProductStatus(eq(TENANT), eq(PRODUCT_ID),
                any(UpdateProductStatusCommand.class)))
                .thenReturn(new Mutation<>(product("RETIRED", 1), "\"1\"", false));

        mvc.perform(patch(PRODUCTS + "/{productId}/status", PRODUCT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RETIRED\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.status").value("RETIRED"))
                .andExpect(jsonPath("$.version").value(1));

        ArgumentCaptor<UpdateProductStatusCommand> captor =
                ArgumentCaptor.forClass(UpdateProductStatusCommand.class);
        verify(client).updateProductStatus(eq(TENANT), eq(PRODUCT_ID), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new UpdateProductStatusCommand("RETIRED"));
    }

    @Test
    void validatesTheCreateProductBody() throws Exception {
        mvc.perform(post(PRODUCTS)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("productCode", "productName", "productLine",
                                "jurisdiction")));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsABlankStatusBeforeTheExpertCall() throws Exception {
        mvc.perform(patch(PRODUCTS + "/{productId}/status", PRODUCT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("status"));

        verifyNoInteractions(client);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get(PRODUCTS).header("X-EIA-Book", DIRECT))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"))
                .andExpect(jsonPath("$.errors[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsAnyOtherBookBeforeCallingTheExpert() throws Exception {
        mvc.perform(get(PRODUCTS)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"));

        verifyNoInteractions(client);
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(client.findProduct(TENANT, PRODUCT_ID))
                .thenReturn(new Mutation<>(product("ACTIVE", 0), "\"0\"", false));

        mvc.perform(get(PRODUCTS + "/{productId}", PRODUCT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()));
    }

    @Test
    void rejectsAMalformedProductId() throws Exception {
        mvc.perform(get(PRODUCTS + "/not-a-uuid")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("productId"));

        verifyNoInteractions(client);
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(client.createProduct(eq(TENANT), any(CreateProductCommand.class)))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_LINE",
                        "productLine must be one of CGL, PRODUCTS_LIABILITY, UMBRELLA, EXCESS, OTHER.",
                        List.of(new ApiException.FieldIssue("productLine", "INVALID_PRODUCT_LINE",
                                "productLine must be one of CGL, PRODUCTS_LIABILITY, UMBRELLA, EXCESS, OTHER.")),
                        Map.of()));

        mvc.perform(post(PRODUCTS)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode": "CGL", "productName": "General Liability",
                                 "productLine": "CUSTOM", "jurisdiction": "US"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_LINE"))
                .andExpect(jsonPath("$.category").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("productLine"));
    }

    @Test
    void preservesTheExpertProductNotFoundProblem() throws Exception {
        when(client.findProduct(TENANT, PRODUCT_ID)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND",
                "Product not found: " + PRODUCT_ID,
                List.of(new ApiException.FieldIssue("productId", "PRODUCT_NOT_FOUND",
                        "Product not found: " + PRODUCT_ID))));

        mvc.perform(get(PRODUCTS + "/{productId}", PRODUCT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].field").value("productId"));
    }

    @Test
    void preservesTheDuplicateProductCodeConflict() throws Exception {
        when(client.createProduct(eq(TENANT), any(CreateProductCommand.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "DUPLICATE_PRODUCT_CODE",
                        "Product code already exists for this tenant: CGL",
                        List.of(new ApiException.FieldIssue("productCode", "DUPLICATE_CODE",
                                "Product code already exists for this tenant: CGL"))));

        mvc.perform(post(PRODUCTS)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT_CODE"))
                .andExpect(jsonPath("$.category").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_CODE"));
    }

    @Test
    void preservesTheConcurrentStatusConflict() throws Exception {
        when(client.updateProductStatus(eq(TENANT), eq(PRODUCT_ID),
                any(UpdateProductStatusCommand.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                        "Expected version 0 but product is at version 1; reload and retry",
                        List.of(new ApiException.FieldIssue("version", "STALE_VERSION",
                                "Expected version 0 but product is at version 1; reload and retry")),
                        Map.of("currentVersion", 1, "expectedVersion", 0)));

        mvc.perform(patch(PRODUCTS + "/{productId}/status", PRODUCT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", DIRECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RETIRED\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.errors[0].code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.meta.currentVersion").value(1));
    }

    private static ProductDto product(String status, int version) {
        Instant created = Instant.parse("2026-10-21T12:00:00Z");
        return new ProductDto(PRODUCT_ID, "CGL", "Commercial General Liability", "CGL",
                "US", status, version, created, created);
    }
}
