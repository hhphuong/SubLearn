package org.example.sublearn.video.dto;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.VideoStatus;

public record VideoStatusResponse(
        VideoStatus status,
        FailureReason failureReason,
        String failureMessage,
        String title,
        String sourceLanguage,
        Integer durationSeconds,
        String youtubeVideoId,
        Integer totalLines,
        Integer processedLines,
        Integer progressPercent
) {
}
