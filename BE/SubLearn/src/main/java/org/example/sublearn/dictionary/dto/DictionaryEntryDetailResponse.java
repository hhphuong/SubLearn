package org.example.sublearn.dictionary.dto;

import java.time.Instant;
import java.util.List;

public record DictionaryEntryDetailResponse(
        Long id,
        String displayWord,
        String sourceLanguage,
        String targetLanguage,
        String contextualMeaning,
        String dictionaryMeaning,
        List<String> examples,
        List<DictionaryContextDto> contexts,
        Instant createdAt,
        Instant updatedAt
) {
}
