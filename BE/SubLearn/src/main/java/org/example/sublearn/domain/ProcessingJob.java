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
@Table(name = "processing_job")
public class ProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private AppUser requestedByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private JobType jobType;

    @Column(name = "source_language", length = 10)
    private String sourceLanguage;

    @Column(name = "target_language", length = 10)
    private String targetLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    private FailureReason failureReason;

    @Column(name = "failure_message", length = 1024)
    private String failureMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProcessingJob() {
    }

    public ProcessingJob(Video video, AppUser requestedByUser, JobType jobType,
                         String sourceLanguage, String targetLanguage, int maxRetries) {
        this.video = video;
        this.requestedByUser = requestedByUser;
        this.jobType = jobType;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.status = JobStatus.PENDING;
        this.retryCount = 0;
        this.maxRetries = maxRetries;
        this.progressPercent = 0;
    }

    public Long getId() {
        return id;
    }

    public Video getVideo() {
        return video;
    }

    public AppUser getRequestedByUser() {
        return requestedByUser;
    }

    public JobType getJobType() {
        return jobType;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markProcessing() {
        this.status = JobStatus.PROCESSING;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = Math.max(0, Math.min(100, progressPercent));
    }

    public void markCompleted() {
        this.status = JobStatus.COMPLETED;
        this.progressPercent = 100;
        this.failureReason = null;
        this.failureMessage = null;
    }

    public void recordRetryAttempt(FailureReason reason, String message) {
        this.retryCount++;
        this.status = JobStatus.PENDING;
        this.failureReason = reason;
        this.failureMessage = truncate(message);
    }

    public void markFailed(FailureReason reason, String message) {
        this.status = JobStatus.FAILED;
        this.failureReason = reason;
        this.failureMessage = truncate(message);
    }

    public void resetForManualRetry() {
        this.status = JobStatus.PENDING;
        this.retryCount = 0;
        this.progressPercent = 0;
        this.failureReason = null;
        this.failureMessage = null;
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= 1024) {
            return message;
        }
        return message.substring(0, 1024);
    }
}
