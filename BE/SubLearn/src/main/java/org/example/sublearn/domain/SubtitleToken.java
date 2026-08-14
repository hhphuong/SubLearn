package org.example.sublearn.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "subtitle_token")
public class SubtitleToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subtitle_line_id", nullable = false)
    private SubtitleLine subtitleLine;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "start_char_offset", nullable = false)
    private int startCharOffset;

    @Column(name = "end_char_offset", nullable = false)
    private int endCharOffset;

    @Column(name = "surface_text", nullable = false)
    private String surfaceText;

    @Column(name = "normalized_text", nullable = false)
    private String normalizedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 20)
    private TokenType tokenType;

    @Column(name = "part_of_speech", length = 20)
    private String partOfSpeech;

    protected SubtitleToken() {
    }

    public SubtitleToken(SubtitleLine subtitleLine, int sequence, int startCharOffset, int endCharOffset,
                          String surfaceText, String normalizedText, TokenType tokenType, String partOfSpeech) {
        this.subtitleLine = subtitleLine;
        this.sequence = sequence;
        this.startCharOffset = startCharOffset;
        this.endCharOffset = endCharOffset;
        this.surfaceText = surfaceText;
        this.normalizedText = normalizedText;
        this.tokenType = tokenType;
        this.partOfSpeech = partOfSpeech;
    }

    public Long getId() {
        return id;
    }

    public SubtitleLine getSubtitleLine() {
        return subtitleLine;
    }

    public int getSequence() {
        return sequence;
    }

    public int getStartCharOffset() {
        return startCharOffset;
    }

    public int getEndCharOffset() {
        return endCharOffset;
    }

    public String getSurfaceText() {
        return surfaceText;
    }

    public String getNormalizedText() {
        return normalizedText;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public String getPartOfSpeech() {
        return partOfSpeech;
    }
}
