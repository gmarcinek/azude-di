package com.example.pdfanalyzer.service;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.models.AnalyzeDocumentRequest;
import com.azure.ai.documentintelligence.models.AnalyzeResult;
import com.azure.ai.documentintelligence.models.ContentFormat;
import com.azure.ai.documentintelligence.models.DocumentAnalysisFeature;
import com.azure.ai.documentintelligence.models.DocumentParagraph;
import com.azure.ai.documentintelligence.models.DocumentTable;
import com.example.pdfanalyzer.config.ChunkingProperties;
import com.example.pdfanalyzer.dto.ChunkedAnalysisResponse;
import com.example.pdfanalyzer.model.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChunkedDocumentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ChunkedDocumentAnalysisService.class);
    private static final Set<String> EXCLUDED_ROLES = Set.of("pageHeader", "pageFooter", "pageNumber");

    private final DocumentIntelligenceClient client;
    private final PdfSplitterService splitterService;
    private final ChunkingProperties chunkingProperties;

    public ChunkedDocumentAnalysisService(DocumentIntelligenceClient client,
            PdfSplitterService splitterService,
            ChunkingProperties chunkingProperties) {
        this.client = client;
        this.splitterService = splitterService;
        this.chunkingProperties = chunkingProperties;
    }

    public ChunkedAnalysisResponse analyzeDocumentInChunks(Path pdfPath) throws IOException {
        log.info("Starting chunked analysis for: {}", pdfPath);

        int pagesPerChunk = Math.max(1, chunkingProperties.pagesPerChunk());
        List<byte[]> pdfChunks = splitterService.splitPdfByPages(pdfPath, pagesPerChunk);

        List<Section> allSections = new ArrayList<>();
        StringBuilder markdownBuilder = new StringBuilder();
        int pageOffset = 0;

        for (int i = 0; i < pdfChunks.size(); i++) {
            log.info("Analyzing chunk {}/{}", i + 1, pdfChunks.size());

            byte[] chunkBytes = pdfChunks.get(i);
            AnalyzeResult result = analyzeChunk(chunkBytes);

            // Extract sections with page offset
            List<Section> chunkSections = extractSections(result, pageOffset);
            allSections.addAll(chunkSections);

            // Extract tables as sections
            List<Section> tableSections = extractTables(result, pageOffset);
            allSections.addAll(tableSections);

            // Build markdown for this chunk
            String chunkMarkdown = buildMarkdownForChunk(chunkSections, tableSections, result, i + 1);
            markdownBuilder.append(chunkMarkdown);

            // Update page offset for next chunk
            if (result.getPages() != null) {
                pageOffset += result.getPages().size();
            }
        }

        String fullMarkdown = markdownBuilder.toString();
        log.info("Chunked analysis completed. Total sections: {}, Total markdown length: {}",
                allSections.size(), fullMarkdown.length());

        return new ChunkedAnalysisResponse(allSections, fullMarkdown);
    }

    private AnalyzeResult analyzeChunk(byte[] pdfBytes) {
        AnalyzeDocumentRequest request = new AnalyzeDocumentRequest();
        request.setBase64Source(pdfBytes);

        // Features to extract
        List<DocumentAnalysisFeature> features = List.of(
                DocumentAnalysisFeature.STYLE_FONT, // Bold, italic, font sizes
                DocumentAnalysisFeature.KEY_VALUE_PAIRS // Key-value pairs
        );

        var poller = client.beginAnalyzeDocument(
                "prebuilt-layout",
                null, // pages - null = all pages
                "pl-PL", // locale for better Polish OCR
                null, // stringIndexType
                features, // features: tables, styles, etc.
                null, // queryFields
                ContentFormat.MARKDOWN, // outputContentFormat: MARKDOWN
                request);

        return poller.getFinalResult();
    }

    private List<Section> extractSections(AnalyzeResult result, int pageOffset) {
        if (result.getParagraphs() == null) {
            return List.of();
        }

        return result.getParagraphs().stream()
                .filter(p -> !isExcludedRole(p.getRole()))
                .map(p -> mapToSection(p, pageOffset))
                .collect(Collectors.toList());
    }

    private List<Section> extractTables(AnalyzeResult result, int pageOffset) {
        if (result.getTables() == null) {
            return List.of();
        }

        List<Section> tableSections = new ArrayList<>();
        for (DocumentTable table : result.getTables()) {
            // Get table page number
            int pageNumber = 1;
            if (table.getBoundingRegions() != null && !table.getBoundingRegions().isEmpty()) {
                Integer pageNum = table.getBoundingRegions().get(0).getPageNumber();
                pageNumber = (pageNum != null ? pageNum : 1) + pageOffset;
            }

            // Build markdown table
            String tableMarkdown = buildTableMarkdown(table);
            tableSections.add(new Section("table", tableMarkdown, pageNumber, 1.0));
        }

        return tableSections;
    }

    private String buildTableMarkdown(DocumentTable table) {
        if (table.getCells() == null || table.getCells().isEmpty()) {
            return "";
        }

        Integer rowCount = table.getRowCount();
        Integer columnCount = table.getColumnCount();

        if (rowCount == null || columnCount == null || rowCount == 0 || columnCount == 0) {
            return "";
        }

        // Initialize table structure
        String[][] tableData = new String[rowCount][columnCount];
        for (int i = 0; i < rowCount; i++) {
            for (int j = 0; j < columnCount; j++) {
                tableData[i][j] = "";
            }
        }

        // Fill table data
        table.getCells().forEach(cell -> {
            Integer rowIdx = cell.getRowIndex();
            Integer colIdx = cell.getColumnIndex();
            String content = cell.getContent() != null ? cell.getContent() : "";

            if (rowIdx != null && colIdx != null && 
                rowIdx >= 0 && rowIdx < rowCount && 
                colIdx >= 0 && colIdx < columnCount) {
                tableData[rowIdx][colIdx] = content;
            }
        });

        // Build markdown
        StringBuilder md = new StringBuilder();

        // Header row
        md.append("|");
        for (int j = 0; j < columnCount; j++) {
            md.append(" ").append(tableData[0][j]).append(" |");
        }
        md.append("\\n");

        // Separator
        md.append("|");
        for (int j = 0; j < columnCount; j++) {
            md.append("---|");
        }
        md.append("\\n");

        // Data rows
        for (int i = 1; i < rowCount; i++) {
            md.append("|");
            for (int j = 0; j < columnCount; j++) {
                md.append(" ").append(tableData[i][j]).append(" |");
            }
            md.append("\\n");
        }

        return md.toString();
    }

    private boolean isExcludedRole(com.azure.ai.documentintelligence.models.ParagraphRole role) {
        if (role == null) {
            return false;
        }
        return EXCLUDED_ROLES.contains(role.toString());
    }

    private Section mapToSection(DocumentParagraph paragraph, int pageOffset) {
        String role = paragraph.getRole() != null ? paragraph.getRole().toString() : "paragraph";
        String content = paragraph.getContent() != null ? paragraph.getContent() : "";
        int pageNumber = extractPageNumber(paragraph) + pageOffset;
        Double confidence = 1.0;

        return new Section(role, content, pageNumber, confidence);
    }

    private int extractPageNumber(DocumentParagraph paragraph) {
        if (paragraph.getBoundingRegions() != null && !paragraph.getBoundingRegions().isEmpty()) {
            Integer pageNum = paragraph.getBoundingRegions().get(0).getPageNumber();
            return pageNum != null ? pageNum : 1;
        }
        return 1;
    }

    private String buildMarkdownForChunk(List<Section> sections, List<Section> tables, AnalyzeResult result,
            int chunkNumber) {
        StringBuilder md = new StringBuilder();

        if (chunkNumber == 1) {
            md.append("# Document Analysis\n\n");
        }

        md.append("## Chunk ").append(chunkNumber).append("\n\n");

        // If Azure DI returned markdown content directly, use it
        if (result.getContent() != null && !result.getContent().isEmpty()) {
            md.append(result.getContent()).append("\n\n");
            return md.toString();
        }

        // Otherwise build from sections
        int currentPage = -1;
        for (Section section : sections) {
            if (section.pageNumber() != currentPage) {
                currentPage = section.pageNumber();
                md.append("\n### Page ").append(currentPage).append("\n\n");
            }

            String content = section.content().trim();
            if (content.isEmpty()) {
                continue;
            }

            String formattedSection = formatSectionForMarkdown(section.role(), content);
            md.append(formattedSection).append("\n\n");
        }

        // Add tables
        if (!tables.isEmpty()) {
            md.append("\n### Tables\n\n");
            for (Section table : tables) {
                md.append(table.content()).append("\n\n");
            }
        }

        return md.toString();
    }

    private String formatSectionForMarkdown(String role, String content) {
        return switch (role.toLowerCase()) {
            case "title" -> "# " + content;
            case "sectionheading" -> "## " + content;
            default -> content;
        };
    }
}