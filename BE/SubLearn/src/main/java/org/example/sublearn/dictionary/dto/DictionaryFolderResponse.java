package org.example.sublearn.dictionary.dto;

import java.time.Instant;

public record DictionaryFolderResponse(
        Long id,
        String name,
        String sourceLanguage,
        int entryCount,
        Instant createdAt
) {
}
