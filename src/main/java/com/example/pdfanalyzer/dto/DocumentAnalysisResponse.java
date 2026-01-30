package com.example.pdfanalyzer.dto;

import com.example.pdfanalyzer.model.DocumentChunk;
import com.example.pdfanalyzer.model.QualityMetrics;
import com.example.pdfanalyzer.model.Section;

import java.util.List;

public record DocumentAnalysisResponse(
        String fileName,
        int pageCount,
        List<Section> sections,
        List<DocumentChunk> chunks,
        QualityMetrics qualityMetrics,
        String markdown) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String fileName;
        private int pageCount;
        private List<Section> sections;
        private List<DocumentChunk> chunks;
        private QualityMetrics qualityMetrics;
        private String markdown;

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder pageCount(int pageCount) {
            this.pageCount = pageCount;
            return this;
        }

        public Builder sections(List<Section> sections) {
            this.sections = sections;
            return this;
        }

        public Builder chunks(List<DocumentChunk> chunks) {
            this.chunks = chunks;
            return this;
        }

        public Builder qualityMetrics(QualityMetrics qualityMetrics) {
            this.qualityMetrics = qualityMetrics;
            return this;
        }

        public Builder markdown(String markdown) {
            this.markdown = markdown;
            return this;
        }

        public DocumentAnalysisResponse build() {
            return new DocumentAnalysisResponse(fileName, pageCount, sections, chunks, qualityMetrics, markdown);
        }
    }
}