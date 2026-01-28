package com.example.pdfanalyzer.model;

public enum SectionClassification {
    KEEP, // Treść główna dokumentu
    REMOVE, // Śmieci do usunięcia (artefakty OCR, pojedyncze znaki)
    AUXILIARY // Tekst pomocniczy (komentarze, wyjaśnienia)
}
