package com.example.pdfanalyzer.model;

public record EnrichedSection(
        String role,
        String content,
        int pageNumber,
        Double confidence,
        SectionClassification classification) {
    public static EnrichedSection fromSection(Section section, SectionClassification classification) {
        return new EnrichedSection(
                section.role(),
                section.content(),
                section.pageNumber(),
                section.confidence(),
                classification);
    }
}
