package org.example.sublearn.admin.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkRetryRequest(@NotEmpty List<Long> jobIds) {
}
