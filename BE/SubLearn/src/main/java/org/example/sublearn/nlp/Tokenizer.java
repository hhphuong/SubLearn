package org.example.sublearn.nlp;

import java.util.List;

public interface Tokenizer {

    List<TokenSpan> tokenize(String text);
}
