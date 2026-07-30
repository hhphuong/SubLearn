package org.example.sublearn.nlp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WhitespaceFallbackTokenizerTest {

    private final WhitespaceFallbackTokenizer tokenizer = new WhitespaceFallbackTokenizer();

    @Test
    void splitsOnWhitespaceWithCorrectOffsets() {
        List<TokenSpan> tokens = tokenizer.tokenize("học sinh giỏi");

        List<String> surfaces = tokens.stream().map(TokenSpan::surfaceText).toList();
        assertEquals(List.of("học", "sinh", "giỏi"), surfaces);

        assertEquals(0, tokens.get(0).startCharOffset());
        assertEquals(3, tokens.get(0).endCharOffset());
        assertEquals(4, tokens.get(1).startCharOffset());
    }

    @Test
    void returnsEmptyListForBlankInput() {
        assertEquals(List.of(), tokenizer.tokenize(" "));
    }
}
