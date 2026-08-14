package org.example.sublearn.wordmeaning;

import org.example.sublearn.common.Sha256;
import org.example.sublearn.domain.WordMeaningCache;
import org.example.sublearn.domain.WordMeaningStatus;
import org.example.sublearn.gemini.GeminiClient;
import org.example.sublearn.gemini.GeminiUnavailableException;
import org.example.sublearn.gemini.GeminiWordMeaningResult;
import org.example.sublearn.repository.WordMeaningCacheRepository;
import org.example.sublearn.wordmeaning.dto.WordLookupRequest;
import org.example.sublearn.wordmeaning.dto.WordLookupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class WordMeaningService {

    private static final Logger log = LoggerFactory.getLogger(WordMeaningService.class);

    private final WordMeaningCacheRepository cacheRepository;
    private final GeminiClient geminiClient;

    public WordMeaningService(WordMeaningCacheRepository cacheRepository, GeminiClient geminiClient) {
        this.cacheRepository = cacheRepository;
        this.geminiClient = geminiClient;
    }

    @Transactional
    public WordLookupResponse lookup(WordLookupRequest request) {
        String normalizedWord = request.word().trim().toLowerCase(Locale.ROOT);
        String contextHash = Sha256.hash(request.sentenceContext().trim());

        Optional<WordMeaningCache> existing = cacheRepository
                .findByNormalizedWordAndSourceLanguageAndTargetLanguageAndContextHash(
                        normalizedWord, request.sourceLanguage(), request.targetLanguage(), contextHash);

        if (existing.isPresent() && existing.get().getStatus() == WordMeaningStatus.READY) {
            WordMeaningCache cache = existing.get();
            return new WordLookupResponse(request.word(), cache.getContextualMeaning(), cache.getDictionaryMeaning(),
                    cache.getExamples(), true, null);
        }

        WordMeaningCache cache = existing.orElseGet(
                () -> new WordMeaningCache(normalizedWord, request.sourceLanguage(), request.targetLanguage(), contextHash));

        try {
            GeminiWordMeaningResult result = geminiClient.lookupWordMeaning(
                    request.word(), request.sentenceContext(), request.sourceLanguage(), request.targetLanguage());
            cache.applySuccess(result.contextualMeaning(), result.dictionaryMeaning(), result.examples());
            cacheRepository.save(cache);
            return new WordLookupResponse(request.word(), result.contextualMeaning(), result.dictionaryMeaning(),
                    result.examples(), false, null);
        } catch (GeminiUnavailableException e) {
            log.warn("Word meaning lookup failed for '{}' ({} -> {})", normalizedWord, request.sourceLanguage(),
                    request.targetLanguage(), e);
            cache.applyFailure();
            cacheRepository.save(cache);
            return new WordLookupResponse(request.word(), null, null, List.of(), false, "TRANSLATION_SERVICE_UNAVAILABLE");
        }
    }
}
