CREATE TABLE admin_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(64) NULL,
    summary VARCHAR(512) NOT NULL,
    details_json TEXT NULL,
    ip_address VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_admin_audit_log_user FOREIGN KEY (admin_user_id) REFERENCES app_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log (created_at);
CREATE INDEX idx_admin_audit_log_admin_created ON admin_audit_log (admin_user_id, created_at);
CREATE INDEX idx_admin_audit_log_action_created ON admin_audit_log (action, created_at);
CREATE INDEX idx_admin_audit_log_target ON admin_audit_log (target_type, target_id);
