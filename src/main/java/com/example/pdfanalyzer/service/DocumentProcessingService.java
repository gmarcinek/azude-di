package com.example.pdfanalyzer.service;

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

    public DocumentProcessingService(
            @Qualifier("openai") ChatClient openAiClient,
            @Qualifier("anthropic") ChatClient anthropicClient) {
        this.openAiClient = openAiClient;
        this.anthropicClient = anthropicClient;
    }

    public List<DocumentChunk> processAndChunk(AnalysisResult analysisResult) {
        log.info("Processing document for chunking: {}", analysisResult.fileName());

        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (Section section : analysisResult.sections()) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("role", section.role());
            metadata.put("confidence", section.confidence());
            metadata.put("fileName", analysisResult.fileName());

            DocumentChunk chunk = new DocumentChunk(
                    analysisResult.fileName() + "-chunk-" + chunkIndex,
                    section.content(),
                    chunkIndex,
                    section.pageNumber(),
                    metadata);

            chunks.add(chunk);
            chunkIndex++;
        }

        log.info("Created {} chunks from document", chunks.size());
        return chunks;
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
