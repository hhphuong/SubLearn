package org.example.sublearn.domain;

public enum AdminAuditAction {
    LOCK_USER,
    UNLOCK_USER,
    CHANGE_USER_ROLE,
    REVOKE_USER_SESSIONS,
    SEND_PASSWORD_RESET,
    RETRY_JOB,
    BULK_RETRY_JOBS,
    FORCE_FAIL_JOB,
    PURGE_DLQ
}
