package com.example.pdfanalyzer.runner;

import com.example.pdfanalyzer.model.AnalysisResult;
import com.example.pdfanalyzer.service.DocumentAnalysisService;
import com.example.pdfanalyzer.service.MarkdownExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PdfProcessorRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PdfProcessorRunner.class);

    private final DocumentAnalysisService service;
    private final MarkdownExportService markdownExportService;
    private final ObjectMapper objectMapper;
    private final String defaultPdfPath;

    public PdfProcessorRunner(DocumentAnalysisService service,
            MarkdownExportService markdownExportService,
            ObjectMapper objectMapper,
            @Value("${app.input.pdf-path}") String defaultPdfPath) {
        this.service = service;
        this.markdownExportService = markdownExportService;
        this.objectMapper = objectMapper;
        this.defaultPdfPath = defaultPdfPath;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("PDF Processor Runner started");

        String pdfPathString = args.length > 0 ? args[0] : defaultPdfPath;
        Path pdfPath = Path.of(pdfPathString);
        log.info("Using PDF path: {}", pdfPath);

        if (!Files.exists(pdfPath)) {
            System.err.println("Error: File not found: " + pdfPath);
            return;
        }

        if (!pdfPath.toString().toLowerCase().endsWith(".pdf")) {
            System.err.println("Error: File must be a PDF: " + pdfPath);
            return;
        }

        log.info("Processing PDF: {}", pdfPath.toAbsolutePath());

        AnalysisResult result = service.analyzeDocument(pdfPath);

        // Save JSON
        Path jsonPath = pdfPath.resolveSibling(
                pdfPath.getFileName().toString().replace(".pdf", "_analysis.json"));
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(jsonPath.toFile(), result);

        // Save Markdown
        Path mdPath = pdfPath.resolveSibling(
                pdfPath.getFileName().toString().replace(".pdf", "_analysis.md"));
        markdownExportService.exportToMarkdown(result, mdPath);

        System.out.println("✓ Analysis complete!");
        System.out.println("JSON saved: " + jsonPath.toAbsolutePath());
        System.out.println("Markdown saved: " + mdPath.toAbsolutePath());
        System.out.println("Pages: " + result.pageCount());
        System.out.println("Sections: " + result.sections().size());
        System.out.println("Avg Confidence: " + String.format("%.2f%%", result.quality().avgConfidence() * 100));
    }
}
