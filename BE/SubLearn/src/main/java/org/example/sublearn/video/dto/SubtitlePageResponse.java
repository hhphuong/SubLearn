package org.example.sublearn.video.dto;

import java.util.List;

public record SubtitlePageResponse(
        int page,
        int size,
        long totalLines,
        String sourceLanguage,
        String targetLanguage,
        boolean sameLanguage,
        List<SubtitleLineDto> lines,
        boolean translationUnavailable
) {
}
