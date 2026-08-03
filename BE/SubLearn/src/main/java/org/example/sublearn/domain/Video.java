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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "video")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "youtube_video_id", nullable = false, unique = true, length = 20)
    private String youtubeVideoId;

    @Column(name = "original_url", nullable = false, length = 1024)
    private String originalUrl;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "source_language", length = 10)
    private String sourceLanguage;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VideoStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    private FailureReason failureReason;

    @Column(name = "failure_message", length = 1024)
    private String failureMessage;

    @Column(name = "total_lines")
    private Integer totalLines;

    @Column(name = "processed_lines")
    private Integer processedLines;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private AppUser requestedByUser;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Video() {
    }

    public Video(String youtubeVideoId, String originalUrl, AppUser requestedByUser) {
        this.youtubeVideoId = youtubeVideoId;
        this.originalUrl = originalUrl;
        this.requestedByUser = requestedByUser;
        this.status = VideoStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public String getYoutubeVideoId() {
        return youtubeVideoId;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public VideoStatus getStatus() {
        return status;
    }

    public void setStatus(VideoStatus status) {
        this.status = status;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void markFailed(FailureReason reason, String message) {
        this.status = VideoStatus.FAILED;
        this.failureReason = reason;
        this.failureMessage = message;
    }

    public void markReady() {
        this.status = VideoStatus.READY;
        this.failureReason = null;
        this.failureMessage = null;
    }

    public Integer getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(Integer totalLines) {
        this.totalLines = totalLines;
    }

    public Integer getProcessedLines() {
        return processedLines;
    }

    public void setProcessedLines(Integer processedLines) {
        this.processedLines = processedLines;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = Math.max(this.progressPercent, progressPercent);
    }

    public void resetProgress() {
        this.totalLines = null;
        this.processedLines = 0;
        this.progressPercent = 0;
    }

    public AppUser getRequestedByUser() {
        return requestedByUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
