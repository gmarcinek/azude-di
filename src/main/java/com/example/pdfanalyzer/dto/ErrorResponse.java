package com.example.pdfanalyzer.dto;

public record ErrorResponse(
        String error,
        String message,
        long timestamp) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, System.currentTimeMillis());
    }
}