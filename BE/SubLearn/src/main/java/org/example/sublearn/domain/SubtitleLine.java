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

@Entity
@Table(name = "subtitle_line")
public class SubtitleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "start_ms", nullable = false)
    private long startMs;

    @Column(name = "end_ms", nullable = false)
    private long endMs;

    @Lob
    @Column(name = "source_text", nullable = false, length = 65535)
    private String sourceText;

    protected SubtitleLine() {
    }

    public SubtitleLine(Video video, int sequence, long startMs, long endMs, String sourceText) {
        this.video = video;
        this.sequence = sequence;
        this.startMs = startMs;
        this.endMs = endMs;
        this.sourceText = sourceText;
    }

    public Long getId() {
        return id;
    }

    public Video getVideo() {
        return video;
    }

    public int getSequence() {
        return sequence;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public String getSourceText() {
        return sourceText;
    }
}
