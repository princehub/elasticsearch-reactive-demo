package com.example.elasticsearchreactivedemo;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;
import java.util.Objects;

@TestConfiguration
public class ElasticsearchConfig {

    @Value("${elasticsearch.uris}")
    private String elasticsearchUri;

    @Value("${elasticsearch.username:#{null}}")
    private String username;

    @Value("${elasticsearch.password:#{null}}")
    private String password;

    @Bean
    @Primary
    public RestClient elasticsearchRestClient() throws Exception {
        // Ensure the URI is not null
        String uri = Objects.requireNonNull(elasticsearchUri, "Elasticsearch URI must not be null");
        
        // Parse host and port from URI
        String[] parts = uri.split(":");
        String scheme = parts[0];
        String host = parts[1].replace("//", "");
        int port = Integer.parseInt(parts[2]);

        RestClientBuilder builder = RestClient.builder(
            new HttpHost(host, port, scheme)
        );

        // Configure credentials if username is provided
        if (StringUtils.hasText(username)) {
            final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(username, password));

            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                
                // If HTTPS, disable SSL verification
                if ("https".equalsIgnoreCase(scheme)) {
                    try {
                        SSLContext sslContext = new SSLContextBuilder()
                            .loadTrustMaterial(null, (X509Certificate[] chain, String authType) -> true)
                            .build();
                        
                        httpClientBuilder.setSSLContext(sslContext)
                            .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to configure SSL context", e);
                    }
                }
                return httpClientBuilder;
            });
        }

        return builder.build();
    }

    @Bean
    @Primary
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    @Primary
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}