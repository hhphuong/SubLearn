ALTER TABLE video
    ADD COLUMN total_lines INT NULL,
    ADD COLUMN processed_lines INT NULL DEFAULT 0;
