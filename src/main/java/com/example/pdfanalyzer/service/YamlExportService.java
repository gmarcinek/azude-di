package com.example.pdfanalyzer.service;

import com.example.pdfanalyzer.dto.ChunkedAnalysisResponse;
import com.example.pdfanalyzer.model.Section;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class YamlExportService {

    private static final Logger log = LoggerFactory.getLogger(YamlExportService.class);
    private final ObjectMapper yamlMapper;

    public YamlExportService() {
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .build();
        this.yamlMapper = new ObjectMapper(yamlFactory);
    }

    public String convertJsonToYaml(ChunkedAnalysisResponse response) {
        log.info("Converting {} sections to YAML", response.sections().size());

        Map<String, Object> yamlStructure = new LinkedHashMap<>();
        List<Map<String, String>> chunks = response.sections().stream()
                .map(this::sectionToMap)
                .collect(Collectors.toList());

        yamlStructure.put("chunks", chunks);

        try {
            return yamlMapper.writeValueAsString(yamlStructure);
        } catch (IOException e) {
            log.error("Failed to convert to YAML", e);
            throw new RuntimeException("Failed to convert to YAML", e);
        }
    }

    public void convertJsonFileToYaml(Path jsonPath, Path yamlPath) throws IOException {
        log.info("Converting JSON file to YAML: {} -> {}", jsonPath, yamlPath);

        // Read JSON
        ObjectMapper jsonMapper = new ObjectMapper();
        ChunkedAnalysisResponse response = jsonMapper.readValue(jsonPath.toFile(), ChunkedAnalysisResponse.class);

        // Convert to YAML
        String yamlContent = convertJsonToYaml(response);

        // Write YAML
        Files.writeString(yamlPath, yamlContent);
        log.info("YAML file saved: {}", yamlPath);
    }

    private Map<String, String> sectionToMap(Section section) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(section.role(), section.content());
        return map;
    }
}