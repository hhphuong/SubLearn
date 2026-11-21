package org.example.sublearn.admin.dto;

import java.util.List;

public record BulkRetryResponse(
        int requestedCount,
        int retriedCount,
        List<Long> skippedIds,
        List<Long> createdJobIds
) {
}
