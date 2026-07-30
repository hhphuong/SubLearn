CREATE TABLE subtitle_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    sequence INT NOT NULL,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    source_text TEXT NOT NULL,
    CONSTRAINT uk_subtitle_line_video_sequence UNIQUE (video_id, sequence),
    CONSTRAINT fk_subtitle_line_video FOREIGN KEY (video_id) REFERENCES video (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_subtitle_line_video_start_ms ON subtitle_line (video_id, start_ms);
