package org.example.sublearn.dictionary.dto;

import java.time.Instant;
import java.util.List;

public record DictionaryEntrySummaryResponse(
        Long id,
        String displayWord,
        String sourceLanguage,
        String targetLanguage,
        String contextualMeaning,
        String dictionaryMeaning,
        List<String> examples,
        int contextCount,
        Instant createdAt,
        Instant updatedAt
) {
}
