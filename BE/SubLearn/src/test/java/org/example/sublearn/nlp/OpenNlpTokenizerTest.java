package org.example.sublearn.nlp;

import org.example.sublearn.domain.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenNlpTokenizerTest {

    private final OpenNlpTokenizer tokenizer = new OpenNlpTokenizer();

    @Test
    void tokenizesEnglishSentenceIntoWordsAndPunctuationInOrder() {
        List<TokenSpan> tokens = tokenizer.tokenize("Hello, world!");

        List<String> surfaces = tokens.stream().map(TokenSpan::surfaceText).toList();
        assertEquals(List.of("Hello", ",", "world", "!"), surfaces);

        assertEquals(0, tokens.get(0).startCharOffset());
        assertEquals(5, tokens.get(0).endCharOffset());
        assertEquals(TokenType.WORD, tokens.get(0).tokenType());
    }

    @Test
    void returnsEmptyListForBlankInput() {
        assertEquals(List.of(), tokenizer.tokenize("   "));
        assertEquals(List.of(), tokenizer.tokenize(null));
    }
}
