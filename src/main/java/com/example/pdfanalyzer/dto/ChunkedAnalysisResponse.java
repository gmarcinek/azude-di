package com.example.pdfanalyzer.dto;

import com.example.pdfanalyzer.model.Section;

import java.util.List;

public record ChunkedAnalysisResponse(
        List<Section> sections,
        String content) {
}