CREATE TABLE app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_app_user_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
