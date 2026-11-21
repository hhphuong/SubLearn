package org.example.sublearn.admin.dto;

import java.time.Instant;

public record AdminUserRecentVideoDto(
        Long videoId,
        String youtubeVideoId,
        String title,
        String sourceLanguage,
        Instant lastWatchedAt
) {
}
