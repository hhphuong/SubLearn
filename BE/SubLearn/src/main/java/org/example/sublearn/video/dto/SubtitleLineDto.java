package org.example.sublearn.video.dto;

import java.util.List;

public record SubtitleLineDto(
        int sequence,
        long startMs,
        long endMs,
        String sourceText,
        String targetText,
        List<SubtitleTokenDto> tokens
) {
}
