package org.example.sublearn.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "subtitle_line_translation")
public class SubtitleLineTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subtitle_line_id", nullable = false)
    private SubtitleLine subtitleLine;

    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage;

    @Lob
    @Column(name = "translated_text", nullable = false, length = 65535)
    private String translatedText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SubtitleLineTranslation() {
    }

    public SubtitleLineTranslation(SubtitleLine subtitleLine, String targetLanguage, String translatedText) {
        this.subtitleLine = subtitleLine;
        this.targetLanguage = targetLanguage;
        this.translatedText = translatedText;
    }

    public Long getId() {
        return id;
    }

    public SubtitleLine getSubtitleLine() {
        return subtitleLine;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public String getTranslatedText() {
        return translatedText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
