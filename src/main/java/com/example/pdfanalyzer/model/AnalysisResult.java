package com.example.pdfanalyzer.model;

import java.util.List;
import java.util.Map;

public record AnalysisResult(
        String fileName,
        int pageCount,
        List<Section> sections,
        QualityMetrics quality) {
}
