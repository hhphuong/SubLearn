package org.example.sublearn.admin.dto;

import org.example.sublearn.domain.FailureReason;

public record FailureReasonCountDto(FailureReason reason, long count) {
}
