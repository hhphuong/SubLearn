package org.example.sublearn.dictionary.dto;

import java.util.List;

public record DictionaryPageResponse(
        int page,
        int size,
        long totalElements,
        List<DictionaryEntrySummaryResponse> entries
) {
}
