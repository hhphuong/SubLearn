package org.example.sublearn.video.dto;

import org.example.sublearn.domain.TokenType;

public record SubtitleTokenDto(
        int sequence,
        int startCharOffset,
        int endCharOffset,
        String surfaceText,
        TokenType tokenType
) {
}
