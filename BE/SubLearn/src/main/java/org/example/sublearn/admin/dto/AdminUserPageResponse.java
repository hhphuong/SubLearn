package org.example.sublearn.admin.dto;

import java.util.List;

public record AdminUserPageResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<AdminUserSummaryDto> users
) {
}
