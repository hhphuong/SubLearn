package org.example.sublearn.gemini;

public class GeminiRateLimitedException extends GeminiUnavailableException {

    public GeminiRateLimitedException(String message) {
        super(message);
    }
}
