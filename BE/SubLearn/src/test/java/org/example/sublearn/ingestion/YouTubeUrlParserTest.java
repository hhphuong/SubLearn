package org.example.sublearn.ingestion;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeUrlParserTest {

    private final YouTubeUrlParser parser = new YouTubeUrlParser();

    @Test
    void extractsVideoIdFromStandardWatchUrl() {
        assertEquals(Optional.of("dQw4w9WgXcQ"), parser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test
    void extractsVideoIdFromWatchUrlWithExtraQueryParams() {
        assertEquals(Optional.of("dQw4w9WgXcQ"),
                parser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&list=PLxyz"));
    }

    @Test
    void extractsVideoIdFromShortYoutubeBeUrl() {
        assertEquals(Optional.of("dQw4w9WgXcQ"), parser.extractVideoId("https://youtu.be/dQw4w9WgXcQ"));
    }

    @Test
    void extractsVideoIdFromShortsUrl() {
        assertEquals(Optional.of("dQw4w9WgXcQ"), parser.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"));
    }

    @Test
    void returnsEmptyForNonYoutubeUrl() {
        assertTrue(parser.extractVideoId("https://example.com/watch?v=dQw4w9WgXcQ").isEmpty());
    }

    @Test
    void returnsEmptyForMalformedOrBlankUrl() {
        assertTrue(parser.extractVideoId("not a url").isEmpty());
        assertTrue(parser.extractVideoId("").isEmpty());
        assertTrue(parser.extractVideoId(null).isEmpty());
    }
}
