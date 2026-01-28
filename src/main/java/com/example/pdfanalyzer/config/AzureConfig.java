package com.example.pdfanalyzer.config;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.DocumentIntelligenceClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureConfig {

    private final String endpoint;
    private final String key;

    public AzureConfig(
            @Value("${azure.document-intelligence.endpoint}") String endpoint,
            @Value("${azure.document-intelligence.key}") String key) {
        this.endpoint = endpoint;
        this.key = key;
    }

    @Bean
    public DocumentIntelligenceClient documentIntelligenceClient() {
        return new DocumentIntelligenceClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(key))
                .buildClient();
    }
}
