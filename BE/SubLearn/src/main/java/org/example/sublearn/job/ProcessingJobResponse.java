package org.example.sublearn.job;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.domain.JobType;

public record ProcessingJobResponse(
        Long jobId,
        Long videoId,
        JobType jobType,
        JobStatus status,
        String sourceLanguage,
        String targetLanguage,
        int retryCount,
        int maxRetries,
        int progressPercent,
        FailureReason failureReason,
        String failureMessage
) {
}
