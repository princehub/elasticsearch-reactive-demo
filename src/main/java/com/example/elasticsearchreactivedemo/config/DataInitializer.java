package com.example.elasticsearchreactivedemo.config;

import com.example.elasticsearchreactivedemo.model.ProductDocument;
import com.example.elasticsearchreactivedemo.service.ProductService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Configuration
public class DataInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final ProductService productService;
    private final ObjectMapper objectMapper;

    public DataInitializer(ProductService productService, ObjectMapper objectMapper) {
        this.productService = productService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadSampleData() {
        try {
            logger.info("Starting to load sample product data...");
            ClassPathResource resource = new ClassPathResource("sample-products.json");
            List<ProductDocument> products = objectMapper.readValue(
                Files.readString(Paths.get(resource.getURI())),
                new TypeReference<List<ProductDocument>>() {}
            );
            
            productService.saveProducts(products)
                .doOnSuccess(result -> logger.info("Successfully loaded {} products", products.size()))
                .doOnError(error -> logger.error("Error loading sample data", error))
                .subscribe();
                
        } catch (Exception e) {
            logger.error("Failed to load sample data", e);
            throw new RuntimeException("Failed to load sample data", e);
        }
    }
}