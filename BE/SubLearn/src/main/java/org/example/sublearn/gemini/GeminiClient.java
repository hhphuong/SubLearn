package org.example.sublearn.gemini;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Thin wrapper around the Gemini {@code generateContent} REST API for the two prompts
 * this app needs: batch subtitle-line translation and single-word meaning lookup. Both
 * ask Gemini for strict JSON output ({@code responseMimeType=application/json}) so the
 * response can be parsed directly instead of scraping free text.
 */
@Component
public class GeminiClient {

    // Gemini's free-tier quota is enforced per-minute, so a transient 429 burst (several
    // users/lookups landing in the same second) usually clears within a couple of seconds -
    // 2 attempts with a maximum 1.5s wait wasn't enough headroom for that to resolve, which
    // is why word lookups intermittently succeeded or failed depending on timing.
    private static final int MAX_ATTEMPTS = 4;
    private static final long[] BACKOFF_MS = {500, 1000, 2000, 4000};

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public GeminiClient(RestClient geminiRestClient,
                         @Value("${sublearn.gemini.api-key}") String apiKey,
                         @Value("${sublearn.gemini.model}") String model,
                         ObjectMapper objectMapper) {
        this.restClient = geminiRestClient;
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    public List<String> translateLines(List<String> lines, String sourceLanguage, String targetLanguage) {
        String jsonText = callGemini(buildTranslatePrompt(lines, sourceLanguage, targetLanguage));
        try {
            List<String> translations = objectMapper.readValue(jsonText, new TypeReference<List<String>>() {
            });
            if (translations.size() != lines.size()) {
                throw new GeminiUnavailableException(
                        "Gemini returned " + translations.size() + " translations for " + lines.size() + " input lines");
            }
            return translations;
        } catch (JacksonException e) {
            throw new GeminiUnavailableException("Could not parse Gemini translation response", e);
        }
    }

    public GeminiWordMeaningResult lookupWordMeaning(String word, String sentenceContext, String sourceLanguage, String targetLanguage) {
        String jsonText = callGemini(buildWordMeaningPrompt(word, sentenceContext, sourceLanguage, targetLanguage));
        try {
            return objectMapper.readValue(jsonText, GeminiWordMeaningResult.class);
        } catch (JacksonException e) {
            throw new GeminiUnavailableException("Could not parse Gemini word meaning response", e);
        }
    }

    private String callGemini(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeminiUnavailableException("Gemini API key is not configured");
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json"));

        RuntimeException lastError = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode response = restClient.post()
                        .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                return extractText(response);
            } catch (HttpClientErrorException.TooManyRequests e) {
                lastError = new GeminiRateLimitedException("Gemini rate limit exceeded");
            } catch (HttpServerErrorException e) {
                lastError = new GeminiUnavailableException("Gemini server error: " + e.getStatusCode());
            } catch (ResourceAccessException e) {
                lastError = new GeminiUnavailableException("Gemini request timed out or the connection failed", e);
            } catch (HttpClientErrorException e) {
                throw new GeminiUnavailableException("Gemini rejected the request: " + e.getStatusCode());
            }
            sleep(BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)]);
        }
        throw lastError != null ? lastError : new GeminiUnavailableException("Gemini request failed after retries");
    }

    private String extractText(JsonNode response) {
        if (response == null) {
            throw new GeminiUnavailableException("Empty response from Gemini");
        }
        JsonNode textNode = response.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (textNode.isMissingNode() || textNode.stringValue("").isBlank()) {
            throw new GeminiUnavailableException("Gemini response did not contain any content");
        }
        return textNode.stringValue("");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildTranslatePrompt(List<String> lines, String sourceLanguage, String targetLanguage) {
        String numbered = IntStream.range(0, lines.size())
                .mapToObj(i -> (i + 1) + ". " + lines.get(i))
                .collect(Collectors.joining("\n"));
        return """
                Translate the following %d subtitle lines from language "%s" to language "%s".
                Respond with ONLY a JSON array of %d strings, in the same order, one translated string per input line.
                Do not merge, split, or omit lines.

                %s
                """.formatted(lines.size(), sourceLanguage, targetLanguage, lines.size(), numbered);
    }

    private String buildWordMeaningPrompt(String word, String sentenceContext, String sourceLanguage, String targetLanguage) {
        return """
                The word or phrase "%s" (language: %s) appears in this sentence: "%s".
                Respond with ONLY a JSON object with these exact fields, written in language "%s":
                {"contextualMeaning": string, "dictionaryMeaning": string, "examples": [string, string]}
                "contextualMeaning" explains what the word means specifically in that sentence.
                "dictionaryMeaning" is the general/common dictionary definition.
                "examples" is a list of 2 short example sentences using the word naturally.
                """.formatted(word, sourceLanguage, sentenceContext, targetLanguage);
    }
}
