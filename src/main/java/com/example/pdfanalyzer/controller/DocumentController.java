package com.example.pdfanalyzer.controller;

import com.example.pdfanalyzer.dto.ChunkedAnalysisResponse;
import com.example.pdfanalyzer.dto.DocumentAnalysisResponse;
import com.example.pdfanalyzer.model.AnalysisResult;
import com.example.pdfanalyzer.service.ChunkedDocumentAnalysisService;
import com.example.pdfanalyzer.service.DocumentAnalysisService;
import com.example.pdfanalyzer.service.DocumentProcessingService;
import com.example.pdfanalyzer.service.MarkdownExportService;
import com.example.pdfanalyzer.service.YamlExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private static final String OUTPUT_DIR = "output";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault());

    private final DocumentAnalysisService analysisService;
    private final ChunkedDocumentAnalysisService chunkedAnalysisService;
    private final DocumentProcessingService processingService;
    private final MarkdownExportService markdownService;
    private final YamlExportService yamlExportService;
    private final ObjectMapper objectMapper;

    public DocumentController(DocumentAnalysisService analysisService,
            ChunkedDocumentAnalysisService chunkedAnalysisService,
            DocumentProcessingService processingService,
            MarkdownExportService markdownService,
            YamlExportService yamlExportService,
            ObjectMapper objectMapper) {
        this.analysisService = analysisService;
        this.chunkedAnalysisService = chunkedAnalysisService;
        this.processingService = processingService;
        this.markdownService = markdownService;
        this.yamlExportService = yamlExportService;
        this.objectMapper = objectMapper;
        initOutputDirectory();
    }

    private void initOutputDirectory() {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
                log.info("Created output directory: {}", outputPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create output directory", e);
        }
    }

    @PostMapping(value = "/documents/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentAnalysisResponse> analyzeDocument(
            @RequestParam("file") MultipartFile file) {

        log.info("Analyzing document: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        if (!isPdfFile(file)) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("upload-", ".pdf");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            try {
                // Analyze document
                AnalysisResult result = analysisService.analyzeDocument(tempFile);

                // Generate chunks
                var chunks = processingService.processAndChunk(result);

                // Generate markdown
                String markdown = markdownService.exportToMarkdownString(result);

                // Build response
                DocumentAnalysisResponse response = DocumentAnalysisResponse.builder()
                        .fileName(file.getOriginalFilename())
                        .pageCount(result.pageCount())
                        .sections(result.sections())
                        .chunks(chunks)
                        .qualityMetrics(result.quality())
                        .markdown(markdown)
                        .build();

                // Save to output directory
                saveOutputFiles(response);

                log.info("Analysis completed successfully for: {}", file.getOriginalFilename());
                return ResponseEntity.ok(response);

            } finally {
                // Clean up temp file
                Files.deleteIfExists(tempFile);
            }

        } catch (IOException e) {
            log.error("Error processing file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error analyzing document: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/documents/analyze-chunked", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChunkedAnalysisResponse> analyzeDocumentChunked(
            @RequestParam("file") MultipartFile file) {

        log.info("Analyzing document in chunks: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        if (!isPdfFile(file)) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("upload-", ".pdf");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            try {
                // Analyze document in chunks
                ChunkedAnalysisResponse response = chunkedAnalysisService.analyzeDocumentInChunks(tempFile);

                // Save to output directory
                saveChunkedOutputFiles(response, file.getOriginalFilename());

                log.info("Chunked analysis completed successfully for: {}", file.getOriginalFilename());
                return ResponseEntity.ok(response);

            } finally {
                // Clean up temp file
                Files.deleteIfExists(tempFile);
            }

        } catch (IOException e) {
            log.error("Error processing file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error analyzing document: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/documents/convert-to-yaml")
    public ResponseEntity<String> convertJsonToYaml(@RequestParam("filename") String filename) {
        log.info("Converting JSON to YAML: {}", filename);

        try {
            // Find JSON file in output directory
            Path jsonPath = Paths.get(OUTPUT_DIR, filename);

            if (!Files.exists(jsonPath)) {
                log.warn("File not found: {}", jsonPath);
                return ResponseEntity.notFound().build();
            }

            if (!filename.endsWith(".json")) {
                return ResponseEntity.badRequest().body("File must be a JSON file");
            }

            // Generate YAML filename
            String yamlFilename = filename.replace(".json", ".yml");
            Path yamlPath = Paths.get(OUTPUT_DIR, yamlFilename);

            // Convert and save
            yamlExportService.convertJsonFileToYaml(jsonPath, yamlPath);

            log.info("Conversion complete: {}", yamlPath);
            return ResponseEntity.ok("YAML file created: " + yamlFilename);

        } catch (IOException e) {
            log.error("Error converting to YAML", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    private boolean isPdfFile(MultipartFile file) {
        if (file.getOriginalFilename() == null) {
            return false;
        }

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename().toLowerCase();

        return (contentType != null && contentType.equals("application/pdf")) ||
                filename.endsWith(".pdf");
    }

    private void saveOutputFiles(DocumentAnalysisResponse response) {
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        String baseFilename = sanitizeFilename(response.fileName());
        String filenameWithoutExt = baseFilename.replaceAll("\\.pdf$", "");

        try {
            // Save JSON
            Path jsonPath = Paths.get(OUTPUT_DIR, timestamp + "_" + filenameWithoutExt + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), response);
            log.info("Saved JSON to: {}", jsonPath);

            // Save Markdown
            Path mdPath = Paths.get(OUTPUT_DIR, timestamp + "_" + filenameWithoutExt + ".md");
            Files.writeString(mdPath, response.markdown());
            log.info("Saved Markdown to: {}", mdPath);

        } catch (IOException e) {
            log.error("Failed to save output files", e);
        }
    }

    private void saveChunkedOutputFiles(ChunkedAnalysisResponse response, String filename) {
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        String baseFilename = sanitizeFilename(filename);
        String filenameWithoutExt = baseFilename.replaceAll("\\.pdf$", "");

        try {
            // Save JSON
            Path jsonPath = Paths.get(OUTPUT_DIR, timestamp + "_" + filenameWithoutExt + "_chunked.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), response);
            log.info("Saved chunked JSON to: {}", jsonPath);

            // Save Markdown
            Path mdPath = Paths.get(OUTPUT_DIR, timestamp + "_" + filenameWithoutExt + "_chunked.md");
            Files.writeString(mdPath, response.content());
            log.info("Saved chunked Markdown to: {}", mdPath);

        } catch (IOException e) {
            log.error("Failed to save chunked output files", e);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unknown";
        }
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}