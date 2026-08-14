package org.example.sublearn.video.dto;

import java.util.List;

public record VideoHistoryPageResponse(
        int page,
        int size,
        int totalElements,
        List<VideoHistoryEntryResponse> entries
) {
}
