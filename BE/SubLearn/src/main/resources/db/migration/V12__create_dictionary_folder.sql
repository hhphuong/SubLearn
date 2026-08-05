CREATE TABLE dictionary_folder (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_language VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_dictionary_folder_user_lang_name UNIQUE (user_id, source_language, name),
    CONSTRAINT fk_dictionary_folder_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
