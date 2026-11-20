package org.example.sublearn.gemini;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Uses a {@link ClientHttpRequestInterceptor} that short-circuits before any real
 * network call to simulate Gemini responses, rather than exercising a live endpoint.
 */
class GeminiClientTest {

    @Test
    void rateLimitedResponseIsRetriedThenThrowsRateLimitedExceptionAfterRetriesExhausted() {
        AtomicInteger callCount = new AtomicInteger();
        ClientHttpRequestInterceptor alwaysTooManyRequests = (request, body, execution) -> {
            callCount.incrementAndGet();
            return new MockClientHttpResponse(new byte[0], HttpStatus.TOO_MANY_REQUESTS);
        };
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost")
                .requestInterceptor(alwaysTooManyRequests)
                .build();
        GeminiClient client = new GeminiClient(restClient, "test-key", "gemini-test", new ObjectMapper());

        assertThrows(GeminiRateLimitedException.class,
                () -> client.lookupWordMeaning("run", "I run every day.", "en", "vi"));
        assertEquals(4, callCount.get());
    }

    @Test
    void successfulResponseIsParsedIntoWordMeaningResult() {
        String innerJson = "{\"contextualMeaning\":\"to jog\",\"dictionaryMeaning\":\"move fast on foot\",\"examples\":[\"She runs daily.\"]}";
        String wrapped = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + toJsonStringLiteral(innerJson) + "}]}}]}";

        ClientHttpRequestInterceptor ok = (request, body, execution) -> {
            MockClientHttpResponse response = new MockClientHttpResponse(wrapped.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response;
        };
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost")
                .requestInterceptor(ok)
                .build();
        GeminiClient client = new GeminiClient(restClient, "test-key", "gemini-test", new ObjectMapper());

        GeminiWordMeaningResult result = client.lookupWordMeaning("run", "I run every day.", "en", "vi");

        assertEquals("to jog", result.contextualMeaning());
        assertEquals("move fast on foot", result.dictionaryMeaning());
        assertEquals(1, result.examples().size());
    }

    @Test
    void blankApiKeyFailsFastWithoutCallingGemini() {
        AtomicInteger callCount = new AtomicInteger();
        ClientHttpRequestInterceptor counter = (request, body, execution) -> {
            callCount.incrementAndGet();
            return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        };
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost")
                .requestInterceptor(counter)
                .build();
        GeminiClient client = new GeminiClient(restClient, "   ", "gemini-test", new ObjectMapper());

        assertThrows(GeminiUnavailableException.class,
                () -> client.translateLines(java.util.List.of("Hello"), "en", "vi"));
        assertEquals(0, callCount.get());
    }

    private String toJsonStringLiteral(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
