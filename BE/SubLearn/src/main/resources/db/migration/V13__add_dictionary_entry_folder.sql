ALTER TABLE dictionary_entry
    ADD COLUMN folder_id BIGINT NULL AFTER target_language,
    ADD CONSTRAINT fk_dictionary_entry_folder FOREIGN KEY (folder_id) REFERENCES dictionary_folder (id) ON DELETE SET NULL;

CREATE INDEX idx_dictionary_entry_folder_id ON dictionary_entry (folder_id);
