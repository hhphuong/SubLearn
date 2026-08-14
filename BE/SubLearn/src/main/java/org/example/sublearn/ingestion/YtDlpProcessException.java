package org.example.sublearn.ingestion;

import org.example.sublearn.domain.FailureReason;

public class YtDlpProcessException extends RuntimeException {

    private final FailureReason failureReason;

    public YtDlpProcessException(FailureReason failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }
}
