package com.example.pdfanalyzer.model;

public record Section(
        String role,
        String content,
        int pageNumber,
        Double confidence) {
}
