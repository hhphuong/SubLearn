package org.example.sublearn.video.dto;

import java.time.Instant;

public record VideoLibraryItemResponse(
        Long videoId,
        String youtubeVideoId,
        String title,
        String sourceLanguage,
        Integer durationSeconds,
        long lastPositionMs,
        Instant addedAt,
        Instant lastWatchedAt
) {
}
