package com.example.pdfanalyzer.service;

import com.example.pdfanalyzer.model.AnalysisResult;
import com.example.pdfanalyzer.model.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class MarkdownExportService {

    private static final Logger log = LoggerFactory.getLogger(MarkdownExportService.class);

    public void exportToMarkdown(AnalysisResult result, Path outputPath) {
        log.info("Exporting to Markdown: {}", outputPath);

        StringBuilder md = new StringBuilder();

        // Header
        md.append("# ").append(result.fileName()).append("\n\n");

        // Metadata
        md.append("## Document Information\n\n");
        md.append("- **File**: ").append(result.fileName()).append("\n");
        md.append("- **Pages**: ").append(result.pageCount()).append("\n");
        md.append("- **Total Sections**: ").append(result.sections().size()).append("\n");
        md.append("- **Total Characters**: ").append(result.quality().totalChars()).append("\n");
        md.append("- **Average Confidence**: ").append(String.format("%.2f%%", result.quality().avgConfidence() * 100))
                .append("\n");
        md.append("- **Has Structure Markers**: ")
                .append(result.quality().hasStructureMarkers() ? "Yes (§, Art., pkt)" : "No").append("\n\n");

        // Quality Metrics
        md.append("## Quality Metrics\n\n");
        md.append("| Metric | Value |\n");
        md.append("|--------|-------|\n");
        md.append("| Total Paragraphs | ").append(result.quality().totalParagraphs()).append(" |\n");
        md.append("| Total Characters | ").append(result.quality().totalChars()).append(" |\n");
        md.append("| Avg Confidence | ").append(String.format("%.2f%%", result.quality().avgConfidence() * 100))
                .append(" |\n");
        md.append("| Structure Markers | ").append(result.quality().hasStructureMarkers() ? "✓" : "✗").append(" |\n\n");

        // Content by page
        md.append("## Document Content\n\n");

        int currentPage = -1;
        boolean inList = false;

        for (int i = 0; i < result.sections().size(); i++) {
            Section section = result.sections().get(i);

            if (section.pageNumber() != currentPage) {
                if (inList) {
                    inList = false;
                }
                currentPage = section.pageNumber();
                md.append("\n### Page ").append(currentPage).append("\n\n");
            }

            String content = section.content().trim();
            if (content.isEmpty())
                continue;

            // Check if this is a list item
            boolean isBullet = isListItem(content);

            if (isBullet) {
                if (!inList) {
                    inList = true;
                }
                // Convert to Markdown list
                String listContent = content.replaceFirst("^[·•\\-*]\\s*", "");
                md.append("- ").append(listContent).append("\n");
            } else {
                if (inList) {
                    md.append("\n");
                    inList = false;
                }

                // Section header based on role
                String header = formatSectionHeader(section.role());
                md.append(header);
                md.append(content);
                md.append("\n\n");
            }
        }

        // Write to file
        try {
            Files.writeString(outputPath, md.toString());
            log.info("Markdown exported successfully: {}", outputPath);
        } catch (IOException e) {
            log.error("Error writing Markdown file: {}", outputPath, e);
            throw new RuntimeException("Failed to export Markdown: " + e.getMessage(), e);
        }
    }

    private String formatSectionHeader(String role) {
        return switch (role.toLowerCase()) {
            case "title" -> "# ";
            case "sectionheading" -> "## ";
            case "footnote" -> "> ";
            case "pagenumber" -> "";
            default -> "";
        };
    }

    private boolean isListItem(String content) {
        return content.matches("^[·•\\-*]\\s+.*");
    }
}
