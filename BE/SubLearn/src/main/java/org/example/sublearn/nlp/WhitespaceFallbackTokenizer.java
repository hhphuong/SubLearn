package org.example.sublearn.nlp;

import org.example.sublearn.domain.TokenType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback tokenizer for languages without a dedicated JVM tokenizer available in this
 * project (notably Vietnamese). Splits on whitespace, i.e. at the syllable level for
 * Vietnamese, which is linguistically imprecise (it will not merge multi-syllable words
 * like "học sinh" into a single token) but is a reasonable, honest MVP baseline —
 * multi-word phrase merging is left as future work.
 */
@Component
public class WhitespaceFallbackTokenizer implements Tokenizer {

    private static final Pattern WORD_PATTERN = Pattern.compile("\\S+");

    @Override
    public List<TokenSpan> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<TokenSpan> tokens = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            tokens.add(new TokenSpan(matcher.group(), matcher.start(), matcher.end(), TokenType.WORD, null));
        }
        return tokens;
    }
}
