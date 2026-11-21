package org.example.sublearn.repository;

import org.example.sublearn.domain.WordMeaningCache;
import org.example.sublearn.domain.WordMeaningStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WordMeaningCacheRepository extends JpaRepository<WordMeaningCache, Long> {

    Optional<WordMeaningCache> findByNormalizedWordAndSourceLanguageAndTargetLanguageAndContextHash(
            String normalizedWord, String sourceLanguage, String targetLanguage, String contextHash);

    long countByStatus(WordMeaningStatus status);
}
