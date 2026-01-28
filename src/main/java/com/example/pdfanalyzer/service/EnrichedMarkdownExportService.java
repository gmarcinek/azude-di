package com.example.pdfanalyzer.service;

import com.example.pdfanalyzer.model.AnalysisResult;
import com.example.pdfanalyzer.model.EnrichedSection;
import com.example.pdfanalyzer.model.SectionClassification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class EnrichedMarkdownExportService {

    private static final Logger log = LoggerFactory.getLogger(EnrichedMarkdownExportService.class);
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^[·•\\-*]\\s+.*");

    public void exportToMarkdown(AnalysisResult result, List<EnrichedSection> enrichedSections, Path outputPath) {
        log.info("Exporting enriched markdown: {}", outputPath);

        StringBuilder md = new StringBuilder();

        // Header
        md.append("# ").append(result.fileName()).append("\n\n");

        // Metadata
        md.append("## Document Information\n\n");
        md.append("- **File**: ").append(result.fileName()).append("\n");
        md.append("- **Pages**: ").append(result.pageCount()).append("\n");
        md.append("- **Total Sections**: ").append(enrichedSections.size()).append("\n");

        long keptSections = enrichedSections.stream()
                .filter(s -> s.classification() == SectionClassification.KEEP)
                .count();
        long auxiliarySections = enrichedSections.stream()
                .filter(s -> s.classification() == SectionClassification.AUXILIARY)
                .count();
        long removedSections = enrichedSections.stream()
                .filter(s -> s.classification() == SectionClassification.REMOVE)
                .count();

        md.append("- **Content Sections**: ").append(keptSections).append("\n");
        md.append("- **Auxiliary Sections**: ").append(auxiliarySections).append("\n");
        md.append("- **Removed Sections**: ").append(removedSections).append("\n");
        md.append("- **Average Confidence**: ").append(String.format("%.2f%%", result.quality().avgConfidence() * 100))
                .append("\n\n");

        // Content by page
        md.append("## Document Content\n\n");

        int currentPage = -1;
        List<EnrichedSection> currentList = new ArrayList<>();

        for (EnrichedSection section : enrichedSections) {
            // Skip removed sections
            if (section.classification() == SectionClassification.REMOVE) {
                continue;
            }

            // Page header
            if (section.pageNumber() != currentPage) {
                flushList(md, currentList);
                currentList.clear();
                currentPage = section.pageNumber();
                md.append("\n### Page ").append(currentPage).append("\n\n");
            }

            String content = section.content().trim();
            if (content.isEmpty()) {
                continue;
            }

            // Handle auxiliary sections
            if (section.classification() == SectionClassification.AUXILIARY) {
                flushList(md, currentList);
                currentList.clear();
                md.append("> **[AUXILIARY]** ").append(content).append("\n\n");
                continue;
            }

            // Handle list items
            if (isListItem(content)) {
                currentList.add(section);
                continue;
            }

            // Flush accumulated list
            flushList(md, currentList);
            currentList.clear();

            // Format section based on role
            String formattedSection = formatSectionHeader(section.role(), content);
            md.append(formattedSection).append("\n\n");
        }

        flushList(md, currentList);

        try {
            Files.writeString(outputPath, md.toString());
            log.info("Markdown export complete: {}", outputPath);
        } catch (IOException e) {
            log.error("Failed to write markdown file", e);
            throw new RuntimeException("Failed to write markdown: " + e.getMessage(), e);
        }
    }

    private boolean isListItem(String content) {
        return LIST_ITEM_PATTERN.matcher(content).matches();
    }

    private void flushList(StringBuilder md, List<EnrichedSection> listItems) {
        if (listItems.isEmpty()) {
            return;
        }

        for (EnrichedSection item : listItems) {
            String content = item.content().trim();
            if (!content.startsWith("-") && !content.startsWith("*") && !content.startsWith("•")
                    && !content.startsWith("·")) {
                content = "- " + content;
            } else if (content.startsWith("•") || content.startsWith("·")) {
                content = "- " + content.substring(1).trim();
            }
            md.append(content).append("\n");
        }
        md.append("\n");
    }

    private String formatSectionHeader(String role, String content) {
        return switch (role) {
            case "title" -> "# " + content;
            case "sectionHeading" -> "## " + content;
            default -> content;
        };
    }
}
