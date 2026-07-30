package org.example.sublearn.nlp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KuromojiTokenizerTest {

    private final KuromojiTokenizer tokenizer = new KuromojiTokenizer();

    @Test
    void tokenizesJapaneseSentenceIntoMorphemesInOrder() {
        // Classic Kuromoji example sentence ("plums and peaches are both peaches").
        List<TokenSpan> tokens = tokenizer.tokenize("すもももももももものうち");

        List<String> surfaces = tokens.stream().map(TokenSpan::surfaceText).toList();
        assertEquals(List.of("すもも", "も", "もも", "も", "もも", "の", "うち"), surfaces);

        // offsets should reconstruct the original text when concatenated in order
        StringBuilder rebuilt = new StringBuilder();
        surfaces.forEach(rebuilt::append);
        assertEquals("すもももももももものうち", rebuilt.toString());
    }

    @Test
    void returnsEmptyListForBlankInput() {
        assertEquals(List.of(), tokenizer.tokenize(""));
    }
}
