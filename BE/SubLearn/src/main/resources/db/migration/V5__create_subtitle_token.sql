CREATE TABLE subtitle_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subtitle_line_id BIGINT NOT NULL,
    sequence INT NOT NULL,
    start_char_offset INT NOT NULL,
    end_char_offset INT NOT NULL,
    surface_text VARCHAR(255) NOT NULL,
    normalized_text VARCHAR(255) NOT NULL,
    token_type VARCHAR(20) NOT NULL,
    part_of_speech VARCHAR(20) NULL,
    CONSTRAINT fk_subtitle_token_subtitle_line FOREIGN KEY (subtitle_line_id) REFERENCES subtitle_line (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_subtitle_token_line_sequence ON subtitle_token (subtitle_line_id, sequence);
CREATE INDEX idx_subtitle_token_normalized_text ON subtitle_token (normalized_text);
