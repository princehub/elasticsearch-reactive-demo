package com.example.elasticsearchreactivedemo.config;

import com.example.elasticsearchreactivedemo.model.ProductDocument;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.stream.IntStream;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner loadData(ElasticsearchClient elasticsearchClient) {
        return args -> {
            IntStream.range(1, 21).forEach(i -> {
                try {
                    ProductDocument product = new ProductDocument();
                    product.setId(String.valueOf(i));
                    product.setName("Product " + i);
                    product.setDescription("Description for Product " + i);
                    product.setPrice(i * 10.0);

                    IndexRequest<ProductDocument> request = IndexRequest.of(builder -> builder
                        .index("products")
                        .id(product.getId())
                        .document(product)
                    );

                    elasticsearchClient.index(request);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        };
    }
}