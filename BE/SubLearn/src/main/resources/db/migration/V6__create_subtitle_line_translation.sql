CREATE TABLE subtitle_line_translation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subtitle_line_id BIGINT NOT NULL,
    target_language VARCHAR(10) NOT NULL,
    translated_text TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_subtitle_line_translation_line_lang UNIQUE (subtitle_line_id, target_language),
    CONSTRAINT fk_subtitle_line_translation_line FOREIGN KEY (subtitle_line_id) REFERENCES subtitle_line (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
