package org.example.sublearn.nlp;

import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;
import org.example.sublearn.domain.TokenType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * English tokenizer. Uses OpenNLP's rule-based {@link SimpleTokenizer} rather than the
 * maxent {@code TokenizerME}, which needs a pretrained model file to be sourced/bundled
 * separately. SimpleTokenizer ships inside opennlp-tools itself, so tokenization works
 * offline with no extra model download.
 */
@Component
public class OpenNlpTokenizer implements Tokenizer {

    @Override
    public List<TokenSpan> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Span[] spans = SimpleTokenizer.INSTANCE.tokenizePos(text);
        List<TokenSpan> tokens = new ArrayList<>(spans.length);
        for (Span span : spans) {
            String surface = text.substring(span.getStart(), span.getEnd());
            tokens.add(new TokenSpan(surface, span.getStart(), span.getEnd(), TokenType.WORD,
                    null));
        }
        return tokens;
    }
}
