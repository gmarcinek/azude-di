package com.example.pdfanalyzer.service;

import com.example.pdfanalyzer.config.ChunkingProperties;
import com.example.pdfanalyzer.model.AnalysisResult;
import com.example.pdfanalyzer.model.DocumentChunk;
import com.example.pdfanalyzer.model.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final ChatClient openAiClient;
    private final ChatClient anthropicClient;
    private final ChunkingProperties chunkingProperties;

    public DocumentProcessingService(
            @Qualifier("openai") ChatClient openAiClient,
            @Qualifier("anthropic") ChatClient anthropicClient,
            ChunkingProperties chunkingProperties) {
        this.openAiClient = openAiClient;
        this.anthropicClient = anthropicClient;
        this.chunkingProperties = chunkingProperties;
    }

    public List<DocumentChunk> processAndChunk(AnalysisResult analysisResult) {
        log.info("Processing document for chunking: {} (strategy: {})",
                analysisResult.fileName(), chunkingProperties.strategy());

        if ("page-based".equalsIgnoreCase(chunkingProperties.strategy())) {
            return chunkByPages(analysisResult);
        } else {
            return chunkBySize(analysisResult);
        }
    }

    private List<DocumentChunk> chunkByPages(AnalysisResult analysisResult) {
        int pagesPerChunk = Math.max(1, chunkingProperties.pagesPerChunk());
        log.info("Chunking by pages: {} pages per chunk", pagesPerChunk);

        List<DocumentChunk> chunks = new ArrayList<>();
        Map<Integer, List<Section>> sectionsByPage = analysisResult.sections().stream()
                .collect(Collectors.groupingBy(Section::pageNumber));

        int chunkIndex = 0;
        int currentPage = 1;
        int maxPage = analysisResult.pageCount();

        while (currentPage <= maxPage) {
            int startPage = currentPage;
            int endPage = Math.min(currentPage + pagesPerChunk - 1, maxPage);

            StringBuilder content = new StringBuilder();
            int sectionCount = 0;

            for (int page = startPage; page <= endPage; page++) {
                List<Section> pageSections = sectionsByPage.getOrDefault(page, List.of());
                for (Section section : pageSections) {
                    String text = section.content() != null ? section.content().trim() : "";
                    if (!text.isEmpty()) {
                        content.append(text).append("\n\n");
                        sectionCount++;
                    }
                }
            }

            if (content.length() > 0) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("fileName", analysisResult.fileName());
                metadata.put("startPage", startPage);
                metadata.put("endPage", endPage);
                metadata.put("sectionCount", sectionCount);
                metadata.put("chunkingStrategy", "page-based");

                DocumentChunk chunk = new DocumentChunk(
                        analysisResult.fileName() + "-chunk-" + chunkIndex,
                        content.toString().trim(),
                        chunkIndex,
                        startPage,
                        metadata);

                chunks.add(chunk);
                chunkIndex++;
            }

            currentPage += pagesPerChunk;
        }

        log.info("Created {} page-based chunks from document", chunks.size());
        return chunks;
    }

    private List<DocumentChunk> chunkBySize(AnalysisResult analysisResult) {
        int maxChunkSize = Math.max(1, chunkingProperties.maxChunkSize());
        int overlapSize = Math.max(0, chunkingProperties.overlap());

        List<DocumentChunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int chunkIndex = 0;
        int startPage = -1;
        int endPage = -1;
        int sectionCount = 0;
        int lastChunkEndPage = -1;

        for (Section section : analysisResult.sections()) {
            String content = section.content() != null ? section.content().trim() : "";
            if (content.isEmpty()) {
                continue;
            }

            int incomingLength = content.length() + 2;
            if (buffer.length() + incomingLength > maxChunkSize && buffer.length() > 0) {
                chunks.add(buildChunk(analysisResult.fileName(), chunkIndex, buffer, startPage, endPage,
                        sectionCount));
                lastChunkEndPage = endPage;
                chunkIndex++;

                String overlapText = "";
                if (overlapSize > 0 && buffer.length() > 0) {
                    int overlapStart = Math.max(0, buffer.length() - overlapSize);
                    overlapText = buffer.substring(overlapStart);
                }

                buffer = new StringBuilder(overlapText);
                startPage = overlapText.isEmpty() ? section.pageNumber() : lastChunkEndPage;
                endPage = overlapText.isEmpty() ? section.pageNumber() : lastChunkEndPage;
                sectionCount = overlapText.isEmpty() ? 0 : 1;
            }

            if (buffer.length() == 0) {
                startPage = section.pageNumber();
            }

            buffer.append(content).append("\n\n");
            endPage = section.pageNumber();
            sectionCount++;
        }

        if (buffer.length() > 0) {
            chunks.add(buildChunk(analysisResult.fileName(), chunkIndex, buffer, startPage, endPage,
                    sectionCount));
        }

        log.info("Created {} size-based chunks from document", chunks.size());
        return chunks;
    }

    private DocumentChunk buildChunk(String fileName,
            int chunkIndex,
            StringBuilder buffer,
            int startPage,
            int endPage,
            int sectionCount) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", fileName);
        metadata.put("startPage", startPage);
        metadata.put("endPage", endPage);
        metadata.put("sectionCount", sectionCount);

        return new DocumentChunk(
                fileName + "-chunk-" + chunkIndex,
                buffer.toString().trim(),
                chunkIndex,
                startPage,
                metadata);
    }

    public String classifyDocument(AnalysisResult result) {
        log.info("Classifying document: {}", result.fileName());

        String content = result.sections().stream()
                .limit(10)
                .map(Section::content)
                .collect(Collectors.joining("\n"));

        if (content.isEmpty()) {
            log.warn("No content available for classification");
            return "UNKNOWN";
        }

        String prompt = """
                Classify the following document into one of these categories:
                - CONTRACT
                - TERMS_AND_CONDITIONS
                - INVOICE
                - LEGAL_DOCUMENT
                - OTHER

                Document excerpt:
                %s

                Return only the category name.
                """.formatted(content);

        try {
            String category = openAiClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("Document classified as: {}", category);
            return category.trim().toUpperCase();

        } catch (Exception e) {
            log.error("Error during document classification", e);
            return "ERROR";
        }
    }
}
