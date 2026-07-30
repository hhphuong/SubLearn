package org.example.sublearn.wordmeaning.dto;

import jakarta.validation.constraints.NotBlank;

public record WordLookupRequest(
        @NotBlank String word,
        @NotBlank String sourceLanguage,
        @NotBlank String targetLanguage,
        @NotBlank String sentenceContext,
        Long videoId,
        Long subtitleLineId
) {
}
