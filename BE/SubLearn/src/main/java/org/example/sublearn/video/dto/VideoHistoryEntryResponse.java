package org.example.sublearn.video.dto;

import java.time.Instant;

public record VideoHistoryEntryResponse(
        Long videoId,
        String youtubeVideoId,
        String title,
        String sourceLanguage,
        Integer durationSeconds,
        Instant lastWatchedAt
) {
}
