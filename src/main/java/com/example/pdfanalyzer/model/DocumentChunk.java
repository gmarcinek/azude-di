package com.example.pdfanalyzer.model;

import java.util.Map;

public record DocumentChunk(
        String id,
        String content,
        int chunkIndex,
        int pageNumber,
        Map<String, Object> metadata) {
}
