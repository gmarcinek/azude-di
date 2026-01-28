package com.example.pdfanalyzer.model;

public record QualityMetrics(
        double avgConfidence,
        int totalParagraphs,
        int totalChars,
        boolean hasStructureMarkers) {
}
