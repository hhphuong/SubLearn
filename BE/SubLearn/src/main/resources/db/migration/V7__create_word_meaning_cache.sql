CREATE TABLE word_meaning_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    normalized_word VARCHAR(255) NOT NULL,
    source_language VARCHAR(10) NOT NULL,
    target_language VARCHAR(10) NOT NULL,
    context_hash VARCHAR(64) NOT NULL,
    contextual_meaning TEXT NULL,
    dictionary_meaning TEXT NULL,
    examples JSON NULL,
    status VARCHAR(20) NOT NULL,
    last_fetched_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NULL,
    CONSTRAINT uk_word_meaning_cache_key UNIQUE (normalized_word, source_language, target_language, context_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
