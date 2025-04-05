package com.example.elasticsearchreactivedemo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data // Generates getters, setters, equals, hashCode, toString
@NoArgsConstructor // Needed for Jackson/ES deserialization
@AllArgsConstructor // Convenient constructor
public class ProductDocument {

    private String id; // Will be the Elasticsearch document ID
    private String name;
    private String description;
    private double price;
    private String category;
    private List<String> tags;

    // Optional: Constructor without ID for creation requests
    public ProductDocument(String name, String description, double price, String category, List<String> tags) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.tags = tags;
    }
}