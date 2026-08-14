CREATE TABLE video_watch_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    last_watched_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_video_watch_history_user_video UNIQUE (user_id, video_id),
    CONSTRAINT fk_video_watch_history_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_video_watch_history_video FOREIGN KEY (video_id) REFERENCES video (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_video_watch_history_user_last_watched ON video_watch_history (user_id, last_watched_at DESC);
