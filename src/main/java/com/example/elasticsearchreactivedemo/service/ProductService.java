package com.example.elasticsearchreactivedemo.service;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.elasticsearchreactivedemo.model.ProductDocument;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor // Lombok constructor injection
@Slf4j
public class ProductService {

    private final ElasticsearchClient esClient;

    @Value("${elasticsearch.index.name}")
    private String indexName;

    // Save or update a product. Generates ID if null.
    public Mono<ProductDocument> saveProduct(ProductDocument product) {
        return Mono.fromCallable(() -> {
            // Generate ID if not present
            if (product.getId() == null || product.getId().isEmpty()) {
                product.setId(UUID.randomUUID().toString());
            }

            IndexRequest<ProductDocument> request = IndexRequest.of(i -> i
                .index(indexName)
                .id(product.getId())
                .document(product)
                .refresh(co.elastic.clients.elasticsearch._types.Refresh.True) // Refresh for visibility in tests/immediately
            );

            log.debug("Indexing document: {}", product);
            IndexResponse response = esClient.index(request);
            log.info("Indexed document ID: {}, Result: {}", response.id(), response.result());

            // Verify result - could throw exception on failure if needed
            if (response.result() != Result.Created && response.result() != Result.Updated) {
                 log.error("Failed to index document {}: {}", product.getId(), response.result());
                 throw new RuntimeException("Failed to index document: " + response.result());
            }
            return product; // Return the product with the (potentially generated) ID
        }).subscribeOn(Schedulers.boundedElastic()); // Offload blocking IO call
    }

    public Mono<ProductDocument> getProductById(String id) {
        return Mono.fromCallable(() -> {
            log.debug("Getting document with ID: {}", id);
            GetRequest getRequest = GetRequest.of(g -> g.index(indexName).id(id));
            GetResponse<ProductDocument> response = esClient.get(getRequest, ProductDocument.class);

            if (response.found()) {
                ProductDocument doc = response.source();
                // ES doesn't store the ID in the _source by default
                if (doc != null) {
                    doc.setId(response.id());
                }
                log.debug("Found document: {}", doc);
                return doc;
            } else {
                log.debug("Document not found with ID: {}", id);
                return null; // Will be mapped to Mono.empty()
            }
        })
        .subscribeOn(Schedulers.boundedElastic()) // Offload blocking IO call
        .filter(Objects::nonNull); // Convert null result to empty Mono
    }

    public Flux<ProductDocument> searchProducts(String query) {
        return Mono.fromCallable(() -> {
            log.debug("Searching for products with query: '{}'", query);
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(indexName)
                .query(q -> q
                    .multiMatch(m -> m // Search across multiple fields
                        .query(query)
                        .fields("name^3", "description", "category", "tags") // Boost name field
                    )
                )
            );

            SearchResponse<ProductDocument> response = esClient.search(searchRequest, ProductDocument.class);
            log.debug("Search hits: {}", response.hits().total().value());

            return response.hits().hits().stream()
                .map(hit -> {
                    ProductDocument doc = hit.source();
                    if (doc != null) {
                        doc.setId(hit.id()); // Set the ID from the hit metadata
                    }
                    return doc;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList()); // Collect results first

        })
        .subscribeOn(Schedulers.boundedElastic()) // Offload blocking IO call
        .flatMapMany(Flux::fromIterable); // Convert the List<Product> to Flux<Product>
    }


    public Mono<Void> deleteProduct(String id) {
         return Mono.fromCallable(() -> {
            log.debug("Deleting document with ID: {}", id);
            DeleteRequest deleteRequest = DeleteRequest.of(d -> d.index(indexName).id(id)
                                                                 .refresh(co.elastic.clients.elasticsearch._types.Refresh.True));
            DeleteResponse response = esClient.delete(deleteRequest);

            log.info("Delete result for ID {}: {}", id, response.result());
            if (response.result() == Result.NotFound) {
                 log.warn("Document not found for deletion with ID: {}", id);
                 // Decide if NotFound should be an error or just complete normally
                 // Throwing an exception here would propagate to onErrorResume in controller
                 // throw new ProductNotFoundException("Product with id " + id + " not found for deletion.");
            } else if (response.result() != Result.Deleted) {
                log.error("Failed to delete document {}: {}", id, response.result());
                throw new RuntimeException("Failed to delete document: " + response.result());
            }
            return response; // Return something non-null for fromCallable
        })
        .subscribeOn(Schedulers.boundedElastic()) // Offload blocking IO call
        .then(); // Convert to Mono<Void> on success
    }
}