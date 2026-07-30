package org.example.sublearn.nlp;

import com.atilika.kuromoji.ipadic.Token;
import org.example.sublearn.domain.TokenType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Japanese tokenizer backed by Kuromoji (IPADIC), the de-facto standard pure-JVM
 * morphological analyzer for Japanese. Japanese has no whitespace between words, so a
 * dedicated analyzer is required rather than a whitespace/rule-based tokenizer. The
 * IPADIC dictionary ships inside the kuromoji-ipadic jar itself — no separate model
 * download needed.
 */
@Component
public class KuromojiTokenizer implements Tokenizer {

    private final com.atilika.kuromoji.ipadic.Tokenizer delegate = new com.atilika.kuromoji.ipadic.Tokenizer();

    @Override
    public List<TokenSpan> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Token> tokens = delegate.tokenize(text);
        List<TokenSpan> result = new ArrayList<>(tokens.size());
        for (Token token : tokens) {
            int start = token.getPosition();
            int end = start + token.getSurface().length();
            result.add(new TokenSpan(token.getSurface(), start, end, TokenType.WORD,
                    token.getPartOfSpeechLevel1()));
        }
        return result;
    }
}
