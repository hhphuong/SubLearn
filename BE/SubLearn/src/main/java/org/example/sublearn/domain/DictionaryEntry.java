package org.example.sublearn.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dictionary_entry")
public class DictionaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "normalized_word", nullable = false)
    private String normalizedWord;

    @Column(name = "source_language", nullable = false, length = 10)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage;

    @Column(name = "display_word", nullable = false)
    private String displayWord;

    @Lob
    @Column(name = "contextual_meaning")
    private String contextualMeaning;

    @Lob
    @Column(name = "dictionary_meaning")
    private String dictionaryMeaning;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "examples")
    private List<String> examples;

    @OneToMany(mappedBy = "dictionaryEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DictionaryEntryContext> contexts = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DictionaryEntry() {
    }

    public DictionaryEntry(AppUser user, String normalizedWord, String sourceLanguage, String targetLanguage,
                            String displayWord, String contextualMeaning, String dictionaryMeaning, List<String> examples) {
        this.user = user;
        this.normalizedWord = normalizedWord;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.displayWord = displayWord;
        this.contextualMeaning = contextualMeaning;
        this.dictionaryMeaning = dictionaryMeaning;
        this.examples = examples;
    }

    public void refreshMeaning(String displayWord, String contextualMeaning, String dictionaryMeaning, List<String> examples) {
        this.displayWord = displayWord;
        this.contextualMeaning = contextualMeaning;
        this.dictionaryMeaning = dictionaryMeaning;
        this.examples = examples;
    }

    public void addContext(DictionaryEntryContext context) {
        contexts.add(context);
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
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

    public String getDisplayWord() {
        return displayWord;
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

    public List<DictionaryEntryContext> getContexts() {
        return contexts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
