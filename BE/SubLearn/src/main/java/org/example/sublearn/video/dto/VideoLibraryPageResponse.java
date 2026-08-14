package org.example.sublearn.video.dto;

import java.util.List;

public record VideoLibraryPageResponse(
        int page,
        int size,
        long totalElements,
        List<VideoLibraryItemResponse> videos
) {
}
