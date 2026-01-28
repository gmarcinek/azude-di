package com.example.pdfanalyzer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CleanupSuggestions(
        @JsonProperty("to_remove") List<String> toRemove,

        @JsonProperty("to_mark_as_auxiliary") List<String> toMarkAsAuxiliary) {
}
