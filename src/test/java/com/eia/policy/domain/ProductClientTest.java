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

class ProductClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID PRODUCT_ID =
            UUID.fromString("55555555-5555-7555-8555-555555555555");
    private static final String BASE = "http://expert";
    private static final String PRODUCTS = BASE + "/internal/v1/products";
    private static final String PRODUCT_STATUS = PRODUCTS + "/" + PRODUCT_ID + "/status";

    @Test
    void listsProductsForwardingTenantDirectBookAndFilters() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCTS + "?tenantId=" + TENANT
                        + "&q=liability&status=ACTIVE"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[%s]".formatted(productJson("ACTIVE", 0))));

        List<ProductDto> products = client.findProducts(TENANT, "liability", "ACTIVE");

        assertThat(products).singleElement().satisfies(product -> {
            assertThat(product.productId()).isEqualTo(PRODUCT_ID);
            assertThat(product.productCode()).isEqualTo("CGL");
            assertThat(product.productName()).isEqualTo("Commercial General Liability");
            assertThat(product.productLine()).isEqualTo("CGL");
            assertThat(product.jurisdiction()).isEqualTo("US");
            assertThat(product.status()).isEqualTo("ACTIVE");
            assertThat(product.version()).isZero();
            assertThat(product.createdAt()).isEqualTo(Instant.parse("2026-10-21T12:00:00Z"));
        });
        server.verify();
    }

    @Test
    void listsProductsWithoutOptionalFiltersAndReturnsAnEmptyArray() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCTS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[]"));

        assertThat(client.findProducts(TENANT, null, null)).isEmpty();
        server.verify();
    }

    @Test
    void acceptsAnItemsPageEnvelopeForTheProductList() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCTS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"items": [%s], "page": 0, "size": 20, "total": 1}
                                """.formatted(productJson("ACTIVE", 0))));

        assertThat(client.findProducts(TENANT, "", " ")).singleElement()
                .satisfies(product -> assertThat(product.productCode()).isEqualTo("CGL"));
        server.verify();
    }

    @Test
    void getsOneProductForwardingTenantDirectBookAndEtag() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCTS + "/" + PRODUCT_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"4\"")
                        .body(productJson("RETIRED", 4)));

        Mutation<ProductDto> mutation = client.findProduct(TENANT, PRODUCT_ID);

        assertThat(mutation.etag()).isEqualTo("\"4\"");
        assertThat(mutation.body().status()).isEqualTo("RETIRED");
        assertThat(mutation.body().version()).isEqualTo(4);
        server.verify();
    }

    @Test
    void mapsTheExpertIdAndSpecColumnNames() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCTS + "/" + PRODUCT_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "id": "%s",
                                  "product_code": "UMBRELLA",
                                  "product_name": "Umbrella Liability",
                                  "product_line": "UMBRELLA",
                                  "jurisdiction": "US",
                                  "status": "ACTIVE",
                                  "version": 2,
                                  "created_at": "2026-10-21T12:00:00Z",
                                  "updated_at": "2026-10-22T12:00:00Z"
                                }
                                """.formatted(PRODUCT_ID)));

        ProductDto product = client.findProduct(TENANT, PRODUCT_ID).body();

        assertThat(product.productId()).isEqualTo(PRODUCT_ID);
        assertThat(product.productCode()).isEqualTo("UMBRELLA");
        assertThat(product.productName()).isEqualTo("Umbrella Liability");
        assertThat(product.productLine()).isEqualTo("UMBRELLA");
        assertThat(product.version()).isEqualTo(2);
        assertThat(product.updatedAt()).isEqualTo(Instant.parse("2026-10-22T12:00:00Z"));
        server.verify();
    }

    @Test
    void acceptsASingleProductEnvelope() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCTS + "/" + PRODUCT_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"product": %s}
                                """.formatted(productJson("ACTIVE", 0))));

        assertThat(client.findProduct(TENANT, PRODUCT_ID).body().productId())
                .isEqualTo(PRODUCT_ID);
        server.verify();
    }

    @Test
    void createsTheProductForwardingTenantDirectBookAndPayload() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCTS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {
                          "productCode": "CGL",
                          "productName": "Commercial General Liability",
                          "productLine": "CGL",
                          "jurisdiction": "US"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"0\"")
                        .body(productJson("ACTIVE", 0)));

        Mutation<ProductDto> mutation = client.createProduct(TENANT,
                new CreateProductCommand("CGL", "Commercial General Liability", "CGL", "US"));

        assertThat(mutation.etag()).isEqualTo("\"0\"");
        assertThat(mutation.body().productId()).isEqualTo(PRODUCT_ID);
        assertThat(mutation.body().status()).isEqualTo("ACTIVE");
        assertThat(mutation.body().version()).isZero();
        server.verify();
    }

    @Test
    void updatesTheProductStatusForwardingTenantDirectBookAndPayload() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCT_STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(header("X-EIA-Book", "DIRECT"))
                .andExpect(content().json("""
                        {"status": "RETIRED"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("ETag", "\"1\"")
                        .body(productJson("RETIRED", 1)));

        Mutation<ProductDto> mutation = client.updateProductStatus(TENANT, PRODUCT_ID,
                new UpdateProductStatusCommand("RETIRED"));

        assertThat(mutation.etag()).isEqualTo("\"1\"");
        assertThat(mutation.body().status()).isEqualTo("RETIRED");
        assertThat(mutation.body().version()).isEqualTo(1);
        server.verify();
    }

    @Test
    void preservesTheExpertValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCTS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/invalid-product-line",
                                  "title": "Invalid product line",
                                  "status": 400,
                                  "detail": "productLine must be one of CGL, PRODUCTS_LIABILITY, UMBRELLA, EXCESS, OTHER.",
                                  "code": "INVALID_PRODUCT_LINE",
                                  "category": "VALIDATION",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "productLine", "code": "INVALID_PRODUCT_LINE",
                                     "message": "productLine must be one of CGL, PRODUCTS_LIABILITY, UMBRELLA, EXCESS, OTHER."}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.createProduct(TENANT,
                new CreateProductCommand("CGL", "General Liability", "CUSTOM", "US")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_PRODUCT_LINE");
                    assertThat(api.details()).singleElement().satisfies(issue -> {
                        assertThat(issue.field()).isEqualTo("productLine");
                        assertThat(issue.code()).isEqualTo("INVALID_PRODUCT_LINE");
                    });
                });
        server.verify();
    }

    @Test
    void preservesTheProductNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCTS + "/" + PRODUCT_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/product-not-found",
                                  "title": "Product not found",
                                  "status": 404,
                                  "detail": "Product not found: %s",
                                  "code": "PRODUCT_NOT_FOUND",
                                  "category": "NOT_FOUND",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "productId", "code": "PRODUCT_NOT_FOUND",
                                     "message": "Product not found: %s"}
                                  ]
                                }
                                """.formatted(PRODUCT_ID, PRODUCT_ID)));

        assertThatThrownBy(() -> client.findProduct(TENANT, PRODUCT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("PRODUCT_NOT_FOUND");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.field()).isEqualTo("productId"));
                });
        server.verify();
    }

    @Test
    void preservesTheDuplicateProductCodeConflict() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCTS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/duplicate-product-code",
                                  "title": "Duplicate product code",
                                  "status": 409,
                                  "detail": "Product code already exists for this tenant: CGL",
                                  "code": "DUPLICATE_PRODUCT_CODE",
                                  "category": "CONFLICT",
                                  "retryable": false,
                                  "errors": [
                                    {"field": "productCode", "code": "DUPLICATE_CODE",
                                     "message": "Product code already exists for this tenant: CGL"}
                                  ]
                                }
                                """));

        assertThatThrownBy(() -> client.createProduct(TENANT,
                new CreateProductCommand("CGL", "Commercial General Liability", "CGL", "US")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("DUPLICATE_PRODUCT_CODE");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.code()).isEqualTo("DUPLICATE_CODE"));
                });
        server.verify();
    }

    @Test
    void preservesTheConcurrentStatusConflict() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCT_STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://errors.eia-open-insurance.example/policy/concurrent-modification",
                                  "title": "Concurrent modification",
                                  "status": 409,
                                  "detail": "Expected version 0 but product is at version 1; reload and retry",
                                  "code": "CONCURRENT_MODIFICATION",
                                  "category": "CONFLICT",
                                  "retryable": true,
                                  "errors": [
                                    {"field": "version", "code": "STALE_VERSION",
                                     "message": "Expected version 0 but product is at version 1; reload and retry"}
                                  ],
                                  "meta": {"currentVersion": 1, "expectedVersion": 0}
                                }
                                """));

        assertThatThrownBy(() -> client.updateProductStatus(TENANT, PRODUCT_ID,
                new UpdateProductStatusCommand("RETIRED")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("CONCURRENT_MODIFICATION");
                    assertThat(api.details()).singleElement().satisfies(issue ->
                            assertThat(issue.code()).isEqualTo("STALE_VERSION"));
                    assertThat(api.meta()).containsEntry("currentVersion", 1);
                });
        server.verify();
    }

    @Test
    void fallsBackToProductNotFoundForABareNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCTS + "/" + PRODUCT_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.findProduct(TENANT, PRODUCT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("PRODUCT_NOT_FOUND");
                });
        server.verify();
    }

    @Test
    void rejectsAnEmptyExpertProductResponse() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, objectMapper(), BASE);

        server.expect(requestTo(PRODUCTS + "/" + PRODUCT_ID + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findProduct(TENANT, PRODUCT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(api.code()).isEqualTo("EXPERT_BAD_RESPONSE");
                });
        server.verify();
    }

    private static String productJson(String status, int version) {
        return """
                {
                  "productId": "%s",
                  "productCode": "CGL",
                  "productName": "Commercial General Liability",
                  "productLine": "CGL",
                  "jurisdiction": "US",
                  "status": "%s",
                  "version": %d,
                  "createdAt": "2026-10-21T12:00:00Z",
                  "updatedAt": "2026-10-21T12:00:00Z"
                }
                """.formatted(PRODUCT_ID, status, version);
    }

    /**
     * The production RestClient builder is supplied by Spring Boot and writes
     * ISO dates; a plain builder would serialize {@link java.time.LocalDate} as
     * an array.
     */
    private static RestClient.Builder builder() {
        return RestClient.builder()
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(0, new MappingJackson2HttpMessageConverter(objectMapper()));
                });
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
