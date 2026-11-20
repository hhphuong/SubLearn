CREATE TABLE processing_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NULL,
    job_type VARCHAR(30) NOT NULL,
    source_language VARCHAR(10) NULL,
    target_language VARCHAR(10) NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 5,
    progress_percent INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(40) NULL,
    failure_message VARCHAR(1024) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_processing_job_video FOREIGN KEY (video_id) REFERENCES video (id) ON DELETE CASCADE,
    CONSTRAINT fk_processing_job_user FOREIGN KEY (requested_by_user_id) REFERENCES app_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_processing_job_video_target_status ON processing_job (video_id, target_language, status);
CREATE INDEX idx_processing_job_video_created ON processing_job (video_id, created_at);
