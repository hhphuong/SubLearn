CREATE TABLE video (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    youtube_video_id VARCHAR(20) NOT NULL,
    original_url VARCHAR(1024) NOT NULL,
    title VARCHAR(512) NULL,
    source_language VARCHAR(10) NULL,
    duration_seconds INT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(40) NULL,
    failure_message VARCHAR(1024) NULL,
    requested_by_user_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_video_youtube_video_id UNIQUE (youtube_video_id),
    CONSTRAINT fk_video_requested_by_user FOREIGN KEY (requested_by_user_id) REFERENCES app_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
