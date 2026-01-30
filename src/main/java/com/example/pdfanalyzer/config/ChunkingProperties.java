package com.example.pdfanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chunking")
public record ChunkingProperties(
        String strategy,
        int pagesPerChunk,
        int maxChunkSize,
        int overlap) {
}
