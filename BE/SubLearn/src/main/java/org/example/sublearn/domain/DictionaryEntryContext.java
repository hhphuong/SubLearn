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
@Table(name = "dictionary_entry_context")
public class DictionaryEntryContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dictionary_entry_id", nullable = false)
    private DictionaryEntry dictionaryEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subtitle_line_id")
    private SubtitleLine subtitleLine;

    @Lob
    @Column(name = "sentence_snapshot", nullable = false, length = 65535)
    private String sentenceSnapshot;

    @CreationTimestamp
    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt;

    protected DictionaryEntryContext() {
    }

    public DictionaryEntryContext(DictionaryEntry dictionaryEntry, Video video, SubtitleLine subtitleLine, String sentenceSnapshot) {
        this.dictionaryEntry = dictionaryEntry;
        this.video = video;
        this.subtitleLine = subtitleLine;
        this.sentenceSnapshot = sentenceSnapshot;
    }

    public Long getId() {
        return id;
    }

    public DictionaryEntry getDictionaryEntry() {
        return dictionaryEntry;
    }

    public Video getVideo() {
        return video;
    }

    public SubtitleLine getSubtitleLine() {
        return subtitleLine;
    }

    public String getSentenceSnapshot() {
        return sentenceSnapshot;
    }

    public Instant getSavedAt() {
        return savedAt;
    }
}
