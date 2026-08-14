package org.example.sublearn.video.dto;

import org.example.sublearn.domain.VideoStatus;

public record VideoSummaryResponse(
        Long videoId,
        String title,
        String sourceLanguage,
        Integer durationSeconds,
        VideoStatus status,
        String youtubeVideoId,
        long lastPositionMs
) {
}
