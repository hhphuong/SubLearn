CREATE TABLE user_video (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    last_position_ms BIGINT NOT NULL DEFAULT 0,
    added_at DATETIME(6) NOT NULL,
    last_watched_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_user_video_user_video UNIQUE (user_id, video_id),
    CONSTRAINT fk_user_video_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_video_video FOREIGN KEY (video_id) REFERENCES video (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_user_video_user_last_watched ON user_video (user_id, last_watched_at);

INSERT INTO user_video (user_id, video_id, last_position_ms, added_at, last_watched_at)
SELECT requested_by_user_id, id, 0, created_at, updated_at
FROM video
WHERE requested_by_user_id IS NOT NULL;
