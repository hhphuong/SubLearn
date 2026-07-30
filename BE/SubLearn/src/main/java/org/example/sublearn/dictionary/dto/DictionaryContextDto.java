package org.example.sublearn.dictionary.dto;

import java.time.Instant;

public record DictionaryContextDto(
        Long id,
        Long videoId,
        Long subtitleLineId,
        String sentenceSnapshot,
        Instant savedAt
) {
}
