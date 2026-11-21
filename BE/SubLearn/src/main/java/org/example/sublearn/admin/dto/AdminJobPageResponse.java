package org.example.sublearn.admin.dto;

import java.util.List;

public record AdminJobPageResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<AdminJobItemDto> jobs
) {
}
