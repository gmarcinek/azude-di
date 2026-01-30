package com.example.pdfanalyzer.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfSplitterService {

    private static final Logger log = LoggerFactory.getLogger(PdfSplitterService.class);

    public List<byte[]> splitPdfByPages(Path pdfPath, int pagesPerChunk) throws IOException {
        log.info("Splitting PDF: {} with {} pages per chunk", pdfPath, pagesPerChunk);

        List<byte[]> chunks = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            int totalPages = document.getNumberOfPages();
            log.info("Total pages in document: {}", totalPages);

            for (int startPage = 0; startPage < totalPages; startPage += pagesPerChunk) {
                int endPage = Math.min(startPage + pagesPerChunk, totalPages);

                try (PDDocument chunkDoc = new PDDocument()) {
                    for (int i = startPage; i < endPage; i++) {
                        PDPage page = document.getPage(i);
                        chunkDoc.addPage(page);
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    chunkDoc.save(baos);
                    byte[] chunkBytes = baos.toByteArray();
                    chunks.add(chunkBytes);

                    log.info("Created chunk {}: pages {}-{} ({} bytes)",
                            chunks.size(), startPage + 1, endPage, chunkBytes.length);
                }
            }
        }

        log.info("Split PDF into {} chunks", chunks.size());
        return chunks;
    }
}