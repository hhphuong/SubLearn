package org.example.sublearn.nlp;

import org.example.sublearn.domain.TokenType;

public record TokenSpan(
        String surfaceText,
        int startCharOffset,
        int endCharOffset,
        TokenType tokenType,
        String partOfSpeech
) {
}
