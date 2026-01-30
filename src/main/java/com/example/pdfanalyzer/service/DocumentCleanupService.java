package com.example.pdfanalyzer.service;

import com.example.pdfanalyzer.config.ChunkingProperties;
import com.example.pdfanalyzer.model.EnrichedSection;
import com.example.pdfanalyzer.model.Section;
import com.example.pdfanalyzer.model.SectionClassification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DocumentCleanupService.class);
    private final ChatClient openAiClient;
    private final ObjectMapper objectMapper;
    private final ChunkingProperties chunkingProperties;

    public DocumentCleanupService(
            @Qualifier("openai") ChatClient openAiClient,
            ObjectMapper objectMapper,
            ChunkingProperties chunkingProperties) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.chunkingProperties = chunkingProperties;
    }

    public List<EnrichedSection> classifySections(List<Section> sections) {
        log.info("Starting section classification for {} sections", sections.size());

        List<SectionChunk> chunks = chunkSections(sections);
        log.info("Created {} chunks from sections", chunks.size());

        Map<Integer, SectionClassification> classifications = new HashMap<>();

        for (int i = 0; i < chunks.size(); i++) {
            log.info("Analyzing chunk {}/{}", i + 1, chunks.size());
            Map<Integer, SectionClassification> chunkClassifications = analyzeChunk(chunks.get(i));
            classifications.putAll(chunkClassifications);
        }

        log.info("Classified {} sections: KEEP={}, REMOVE={}, AUXILIARY={}",
                classifications.size(),
                classifications.values().stream().filter(c -> c == SectionClassification.KEEP).count(),
                classifications.values().stream().filter(c -> c == SectionClassification.REMOVE).count(),
                classifications.values().stream().filter(c -> c == SectionClassification.AUXILIARY).count());

        List<EnrichedSection> enriched = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            SectionClassification classification = classifications.getOrDefault(i, SectionClassification.KEEP);
            enriched.add(EnrichedSection.fromSection(sections.get(i), classification));
        }

        return enriched;
    }

    private List<SectionChunk> chunkSections(List<Section> sections) {
        int chunkSize = Math.max(1, chunkingProperties.maxChunkSize());
        int overlapSize = Math.max(0, chunkingProperties.overlap());

        List<SectionChunk> chunks = new ArrayList<>();
        List<IndexedSection> currentChunk = new ArrayList<>();
        int currentSize = 0;

        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            int sectionSize = section.content().length();

            if (currentSize + sectionSize > chunkSize && !currentChunk.isEmpty()) {
                chunks.add(new SectionChunk(new ArrayList<>(currentChunk)));

                // Keep overlap by char size
                if (overlapSize > 0) {
                    int overlapChars = 0;
                    int overlapStart = currentChunk.size();
                    for (int j = currentChunk.size() - 1; j >= 0; j--) {
                        int sectionChars = currentChunk.get(j).section().content().length();
                        if (overlapChars + sectionChars > overlapSize) {
                            break;
                        }
                        overlapChars += sectionChars;
                        overlapStart = j;
                    }
                    currentChunk = new ArrayList<>(currentChunk.subList(overlapStart, currentChunk.size()));
                    currentSize = currentChunk.stream().mapToInt(s -> s.section().content().length()).sum();
                } else {
                    currentChunk = new ArrayList<>();
                    currentSize = 0;
                }
            }

            currentChunk.add(new IndexedSection(i, section));
            currentSize += sectionSize;
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(new SectionChunk(currentChunk));
        }

        return chunks;
    }

    private Map<Integer, SectionClassification> analyzeChunk(SectionChunk chunk) {
        String prompt = buildPrompt(chunk);

        try {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .withModel("gpt-4")
                    .withTemperature(0.0)
                    .build();

            String response = openAiClient.prompt()
                    .user(prompt)
                    .options(options)
                    .call()
                    .content();

            log.debug("LLM response: {}", response);

            return parseClassificationResponse(response);

        } catch (Exception e) {
            log.error("Error analyzing chunk", e);
            return new HashMap<>();
        }
    }

    private String buildPrompt(SectionChunk chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append("Przeanalizuj poniższe sekcje dokumentu i sklasyfikuj każdą jako:\n\n");
        sb.append("- KEEP: treść główna dokumentu (artykuły, paragrafy, definicje)\n");
        sb.append("- REMOVE: śmieci do usunięcia (pojedyncze znaki, artefakty OCR, znaczniki)\n");
        sb.append("- AUXILIARY: tekst pomocniczy (komentarze, wyjaśnienia, notki)\n\n");
        sb.append("Sekcje do analizy:\n\n");

        for (IndexedSection indexed : chunk.sections()) {
            sb.append(String.format("[%d] role=%s, page=%d\n",
                    indexed.index(),
                    indexed.section().role(),
                    indexed.section().pageNumber()));
            sb.append("content: ").append(indexed.section().content()).append("\n\n");
        }

        sb.append("Zwróć odpowiedź w formacie JSON:\n");
        sb.append("{\n");
        sb.append("  \"0\": \"KEEP\",\n");
        sb.append("  \"1\": \"REMOVE\",\n");
        sb.append("  \"2\": \"AUXILIARY\"\n");
        sb.append("}\n\n");
        sb.append("Gdzie klucze to indeksy [0], [1], [2] itd.\n");
        sb.append("Zwróć TYLKO JSON, bez żadnych dodatkowych komentarzy.");

        return sb.toString();
    }

    private Map<Integer, SectionClassification> parseClassificationResponse(String response) {
        try {
            Map<String, String> rawMap = objectMapper.readValue(
                    response,
                    new TypeReference<Map<String, String>>() {
                    });

            Map<Integer, SectionClassification> result = new HashMap<>();
            for (Map.Entry<String, String> entry : rawMap.entrySet()) {
                try {
                    int index = Integer.parseInt(entry.getKey());
                    SectionClassification classification = SectionClassification
                            .valueOf(entry.getValue().toUpperCase());
                    result.put(index, classification);
                } catch (Exception e) {
                    log.warn("Invalid classification entry: {} -> {}", entry.getKey(), entry.getValue());
                }
            }
            return result;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse classification response", e);
            return new HashMap<>();
        }
    }

    private record SectionChunk(List<IndexedSection> sections) {
    }

    private record IndexedSection(int index, Section section) {
    }
}
