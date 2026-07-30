package org.example.sublearn.nlp;

public class TokenizationException extends RuntimeException {

    public TokenizationException(String message, Throwable cause) {
        super(message, cause);
    }

    public TokenizationException(String message) {
        super(message);
    }
}
