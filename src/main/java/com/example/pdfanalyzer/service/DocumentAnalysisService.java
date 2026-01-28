package com.example.pdfanalyzer.service;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.models.*;
import com.example.pdfanalyzer.model.AnalysisResult;
import com.example.pdfanalyzer.model.QualityMetrics;
import com.example.pdfanalyzer.model.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DocumentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAnalysisService.class);
    private static final Set<String> EXCLUDED_ROLES = Set.of(
            "pageHeader",
            "pageFooter",
            "pageNumber"
    // Dodaj tutaj więcej jeśli chcesz:
    // "footnote", // przypisy
    // "caption", // podpisy
    // "formulaBlock" // formuły
    );
    private static final Pattern STRUCTURE_MARKERS = Pattern.compile("§\\s*\\d+|Art\\.\\s*\\d+|pkt\\s+\\d+");

    private final DocumentIntelligenceClient client;

    public DocumentAnalysisService(DocumentIntelligenceClient client) {
        this.client = client;
    }

    @Cacheable(value = "documents", key = "#pdfPath.fileName.toString() + '-' + #pdfPath.toFile().length()")
    public AnalysisResult analyzeDocument(Path pdfPath) {
        log.info("Starting document analysis for: {}", pdfPath);

        try {
            byte[] documentBytes = Files.readAllBytes(pdfPath);

            log.debug("Sending document to Azure Document Intelligence...");

            AnalyzeDocumentRequest request = new AnalyzeDocumentRequest();
            request.setBase64Source(documentBytes);

            var poller = client.beginAnalyzeDocument(
                    "prebuilt-layout",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    request);

            AnalyzeResult result = poller.getFinalResult();
            log.info("Document analysis completed. Pages: {}", result.getPages().size());

            return mapToAnalysisResult(pdfPath.getFileName().toString(), result);

        } catch (Exception e) {
            log.error("Error analyzing document: {}", pdfPath, e);
            throw new RuntimeException("Failed to analyze document: " + e.getMessage(), e);
        }
    }

    private AnalysisResult mapToAnalysisResult(String fileName, AnalyzeResult azureResult) {
        int pageCount = azureResult.getPages() != null ? azureResult.getPages().size() : 0;

        List<Section> sections = azureResult.getParagraphs() != null
                ? azureResult.getParagraphs().stream()
                        .filter(p -> !isExcludedRole(p.getRole()))
                        .map(this::mapToSection)
                        .collect(Collectors.toList())
                : List.of();

        QualityMetrics quality = calculateQualityMetrics(sections);

        return new AnalysisResult(fileName, pageCount, sections, quality);
    }

    private boolean isExcludedRole(ParagraphRole role) {
        if (role == null) {
            return false;
        }
        String roleStr = role.toString();
        return EXCLUDED_ROLES.contains(roleStr);
    }

    private Section mapToSection(DocumentParagraph paragraph) {
        String role = paragraph.getRole() != null ? paragraph.getRole().toString() : "paragraph";
        String content = paragraph.getContent() != null ? paragraph.getContent() : "";
        int pageNumber = extractPageNumber(paragraph);
        Double confidence = 1.0; // Azure DI beta.4 doesn't expose confidence at paragraph level

        return new Section(role, content, pageNumber, confidence);
    }

    private int extractPageNumber(DocumentParagraph paragraph) {
        if (paragraph.getBoundingRegions() != null && !paragraph.getBoundingRegions().isEmpty()) {
            Integer pageNum = paragraph.getBoundingRegions().get(0).getPageNumber();
            return pageNum != null ? pageNum : 1;
        }
        return 1;
    }

    private QualityMetrics calculateQualityMetrics(List<Section> sections) {
        if (sections.isEmpty()) {
            return new QualityMetrics(0.0, 0, 0, false);
        }

        double avgConfidence = sections.stream()
                .mapToDouble(s -> s.confidence() != null ? s.confidence() : 0.0)
                .average()
                .orElse(0.0);

        int totalParagraphs = sections.size();

        int totalChars = sections.stream()
                .mapToInt(s -> s.content().length())
                .sum();

        boolean hasStructureMarkers = sections.stream()
                .anyMatch(s -> STRUCTURE_MARKERS.matcher(s.content()).find());

        return new QualityMetrics(avgConfidence, totalParagraphs, totalChars, hasStructureMarkers);
    }
}
