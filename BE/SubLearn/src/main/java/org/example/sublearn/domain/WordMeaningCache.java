package org.example.sublearn.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "word_meaning_cache")
public class WordMeaningCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "normalized_word", nullable = false)
    private String normalizedWord;

    @Column(name = "source_language", nullable = false, length = 10)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage;

    @Column(name = "context_hash", nullable = false, length = 64)
    private String contextHash;

    @Lob
    @Column(name = "contextual_meaning", length = 65535)
    private String contextualMeaning;

    @Lob
    @Column(name = "dictionary_meaning", length = 65535)
    private String dictionaryMeaning;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "examples")
    private List<String> examples;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WordMeaningStatus status;

    @Column(name = "last_fetched_at", nullable = false)
    private Instant lastFetchedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected WordMeaningCache() {
    }

    public WordMeaningCache(String normalizedWord, String sourceLanguage, String targetLanguage, String contextHash) {
        this.normalizedWord = normalizedWord;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.contextHash = contextHash;
        this.lastFetchedAt = Instant.now();
    }

    public void applySuccess(String contextualMeaning, String dictionaryMeaning, List<String> examples) {
        this.contextualMeaning = contextualMeaning;
        this.dictionaryMeaning = dictionaryMeaning;
        this.examples = examples;
        this.status = WordMeaningStatus.READY;
        this.lastFetchedAt = Instant.now();
    }

    public void applyFailure() {
        this.status = WordMeaningStatus.FAILED;
        this.lastFetchedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getNormalizedWord() {
        return normalizedWord;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public String getContextHash() {
        return contextHash;
    }

    public String getContextualMeaning() {
        return contextualMeaning;
    }

    public String getDictionaryMeaning() {
        return dictionaryMeaning;
    }

    public List<String> getExamples() {
        return examples;
    }

    public WordMeaningStatus getStatus() {
        return status;
    }

    public Instant getLastFetchedAt() {
        return lastFetchedAt;
    }
}
