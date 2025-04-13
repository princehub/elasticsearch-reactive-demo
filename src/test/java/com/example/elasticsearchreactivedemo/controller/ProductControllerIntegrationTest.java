package com.example.elasticsearchreactivedemo.controller;

import com.example.elasticsearchreactivedemo.config.ElasticsearchConfig;
import com.example.elasticsearchreactivedemo.model.ProductDocument;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(ElasticsearchConfig.class)
class ProductControllerIntegrationTest {

    private static final String ELASTICSEARCH_VERSION = "8.11.3";

    @Container
    private static final ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:" + ELASTICSEARCH_VERSION)
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withStartupTimeout(Duration.ofMinutes(2));

    @Autowired
    private WebTestClient webTestClient;

    private static String createdProductId;

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("elasticsearch.uris",
            () -> "http://" + elasticsearchContainer.getHost() + ":" + elasticsearchContainer.getMappedPort(9200));
    }

    @BeforeAll
    static void setUp() {
        elasticsearchContainer.start();
    }

    @AfterAll
    static void tearDown() {
        if (elasticsearchContainer != null && elasticsearchContainer.isRunning()) {
            elasticsearchContainer.stop();
        }
    }

    @Test
    @Order(1)
    void shouldCreateProduct() {
        ProductDocument newProduct = new ProductDocument(
                "Test Laptop", "A powerful test laptop", 1299.99, "Electronics", List.of("computer", "test")
        );

        webTestClient.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(newProduct), ProductDocument.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductDocument.class)
                .value(product -> {
                    assertThat(product.getId()).isNotNull().isNotEmpty();
                    assertThat(product.getName()).isEqualTo("Test Laptop");
                    assertThat(product.getPrice()).isEqualTo(1299.99);
                    createdProductId = product.getId();
                    System.out.println("Created Product ID: " + createdProductId);
                });
    }

    @Test
    @Order(2)
    void shouldGetProductById() {
        assertThat(createdProductId).as("Product ID should be set from create test").isNotNull();

        webTestClient.get().uri("/api/v1/products/{id}", createdProductId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductDocument.class)
                .value(product -> {
                    assertThat(product.getId()).isEqualTo(createdProductId);
                    assertThat(product.getName()).isEqualTo("Test Laptop");
                });
    }

    @Test
    @Order(3)
    void shouldReturnNotFoundForUnknownId() {
        webTestClient.get().uri("/api/v1/products/{id}", "unknown-id-123")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(4)
    void shouldSearchProducts() {
        // Clean up existing data first
        ProductDocument anotherProduct = new ProductDocument(
                "Test Monitor", "A wide test screen", 399.50, "Electronics", List.of("display", "test")
        );

        webTestClient.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(anotherProduct), ProductDocument.class)
                .exchange()
                .expectStatus().isCreated();

        webTestClient.get().uri("/api/v1/products/search?query=test")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProductDocument.class)
                .value(products -> {
                    assertThat(products).extracting(ProductDocument::getName)
                            .containsExactlyInAnyOrder("Test Laptop", "Test Monitor");
                });

        webTestClient.get().uri("/api/v1/products/search?query=Monitor")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProductDocument.class)
                .value(products -> {
                    assertThat(products).hasSize(1);
                    assertThat(products.get(0).getName()).isEqualTo("Test Monitor");
                });
    }

    @Test
    @Order(5)
    void shouldUpdateProduct() {
        assertThat(createdProductId).as("Product ID should be set").isNotNull();
        ProductDocument updatedInfo = new ProductDocument(
                "Updated Test Laptop", "An even better test laptop", 1349.00, "Electronics", List.of("computer", "updated")
        );

        webTestClient.put().uri("/api/v1/products/{id}", createdProductId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(updatedInfo), ProductDocument.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductDocument.class)
                .value(product -> {
                    assertThat(product.getId()).isEqualTo(createdProductId);
                    assertThat(product.getName()).isEqualTo("Updated Test Laptop");
                    assertThat(product.getPrice()).isEqualTo(1349.00);
                    assertThat(product.getTags()).containsExactlyInAnyOrder("computer", "updated");
                });

        webTestClient.get().uri("/api/v1/products/{id}", createdProductId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductDocument.class)
                .value(product -> assertThat(product.getName()).isEqualTo("Updated Test Laptop"));
    }

    @Test
    @Order(6)
    void shouldDeleteProduct() {
        assertThat(createdProductId).as("Product ID should be set").isNotNull();

        webTestClient.delete().uri("/api/v1/products/{id}", createdProductId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @Order(7)
    void shouldReturnNotFoundAfterDelete() {
        assertThat(createdProductId).as("Product ID should be set").isNotNull();

        webTestClient.get().uri("/api/v1/products/{id}", createdProductId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }
    // write one more test case

}
