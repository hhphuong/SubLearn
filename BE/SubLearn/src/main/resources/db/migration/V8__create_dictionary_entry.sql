CREATE TABLE dictionary_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    normalized_word VARCHAR(255) NOT NULL,
    source_language VARCHAR(10) NOT NULL,
    target_language VARCHAR(10) NOT NULL,
    display_word VARCHAR(255) NOT NULL,
    contextual_meaning TEXT NULL,
    dictionary_meaning TEXT NULL,
    examples JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_dictionary_entry_user_word UNIQUE (user_id, normalized_word, source_language, target_language),
    CONSTRAINT fk_dictionary_entry_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
