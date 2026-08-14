package org.example.sublearn.dictionary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveDictionaryRequest(
        @NotBlank String word,
        @NotBlank String sourceLanguage,
        @NotBlank String targetLanguage,
        String contextualMeaning,
        String dictionaryMeaning,
        List<String> examples,
        @NotNull Long videoId,
        Long subtitleLineId,
        @NotBlank String sentenceSnapshot,
        Long folderId
) {
}
