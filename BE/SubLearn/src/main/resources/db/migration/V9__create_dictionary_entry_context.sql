CREATE TABLE dictionary_entry_context (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dictionary_entry_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    subtitle_line_id BIGINT NULL,
    sentence_snapshot TEXT NOT NULL,
    saved_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_dictionary_entry_context_entry FOREIGN KEY (dictionary_entry_id) REFERENCES dictionary_entry (id) ON DELETE CASCADE,
    CONSTRAINT fk_dictionary_entry_context_video FOREIGN KEY (video_id) REFERENCES video (id) ON DELETE CASCADE,
    CONSTRAINT fk_dictionary_entry_context_line FOREIGN KEY (subtitle_line_id) REFERENCES subtitle_line (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_dictionary_entry_context_entry_id ON dictionary_entry_context (dictionary_entry_id);
