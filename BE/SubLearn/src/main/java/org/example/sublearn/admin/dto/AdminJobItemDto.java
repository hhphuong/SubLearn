package org.example.sublearn.admin.dto;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.domain.JobType;

import java.time.Instant;

public record AdminJobItemDto(
        Long jobId,
        Long videoId,
        String youtubeVideoId,
        String videoTitle,
        Long requestedByUserId,
        String requestedByEmail,
        JobType jobType,
        JobStatus status,
        String sourceLanguage,
        String targetLanguage,
        int retryCount,
        int maxRetries,
        int progressPercent,
        FailureReason failureReason,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
