package org.example.sublearn.wordmeaning;

import org.example.sublearn.domain.WordMeaningCache;
import org.example.sublearn.gemini.GeminiClient;
import org.example.sublearn.gemini.GeminiWordMeaningResult;
import org.example.sublearn.repository.WordMeaningCacheRepository;
import org.example.sublearn.wordmeaning.dto.WordLookupRequest;
import org.example.sublearn.wordmeaning.dto.WordLookupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WordMeaningServiceTest {

    private WordMeaningCacheRepository cacheRepository;
    private GeminiClient geminiClient;
    private WordMeaningService service;

    @BeforeEach
    void setUp() {
        cacheRepository = mock(WordMeaningCacheRepository.class);
        geminiClient = mock(GeminiClient.class);
        service = new WordMeaningService(cacheRepository, geminiClient);
    }

    @Test
    void cacheMissCallsGeminiThenCacheHitOnSecondCallSkipsGemini() {
        WordLookupRequest request = new WordLookupRequest("run", "en", "vi", "I like to run every morning.", null, null);

        when(cacheRepository.findByNormalizedWordAndSourceLanguageAndTargetLanguageAndContextHash(
                eq("run"), eq("en"), eq("vi"), any())).thenReturn(Optional.empty());
        when(geminiClient.lookupWordMeaning(eq("run"), anyString(), eq("en"), eq("vi")))
                .thenReturn(new GeminiWordMeaningResult("to jog", "to move fast on foot", List.of("She runs daily.")));

        WordLookupResponse first = service.lookup(request);
        assertEquals("to jog", first.contextualMeaning());
        assertEquals("to move fast on foot", first.dictionaryMeaning());
        assertFalse(first.cached());
        verify(geminiClient, times(1)).lookupWordMeaning(anyString(), anyString(), anyString(), anyString());
        verify(cacheRepository, times(1)).save(any());

        WordMeaningCache readyCache = new WordMeaningCache("run", "en", "vi", "some-hash");
        readyCache.applySuccess("to jog", "to move fast on foot", List.of("She runs daily."));
        when(cacheRepository.findByNormalizedWordAndSourceLanguageAndTargetLanguageAndContextHash(
                eq("run"), eq("en"), eq("vi"), any())).thenReturn(Optional.of(readyCache));

        WordLookupResponse second = service.lookup(request);
        assertTrue(second.cached());
        assertEquals("to jog", second.contextualMeaning());
        verify(geminiClient, times(1)).lookupWordMeaning(anyString(), anyString(), anyString(), anyString());
    }
}
