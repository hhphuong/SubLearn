package org.example.sublearn.nlp;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class TokenizerFactory {

    private final OpenNlpTokenizer openNlpTokenizer;
    private final KuromojiTokenizer kuromojiTokenizer;
    private final WhitespaceFallbackTokenizer whitespaceFallbackTokenizer;

    public TokenizerFactory(OpenNlpTokenizer openNlpTokenizer, KuromojiTokenizer kuromojiTokenizer,
                             WhitespaceFallbackTokenizer whitespaceFallbackTokenizer) {
        this.openNlpTokenizer = openNlpTokenizer;
        this.kuromojiTokenizer = kuromojiTokenizer;
        this.whitespaceFallbackTokenizer = whitespaceFallbackTokenizer;
    }

    public Tokenizer get(String sourceLanguage) {
        if (sourceLanguage == null) {
            return whitespaceFallbackTokenizer;
        }
        return switch (sourceLanguage.toLowerCase(Locale.ROOT)) {
            case "en" -> openNlpTokenizer;
            case "ja" -> kuromojiTokenizer;
            default -> whitespaceFallbackTokenizer;
        };
    }
}
