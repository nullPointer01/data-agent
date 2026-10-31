-- 普通 Run 的提交载荷与事件日志；业务状态仍以 agent_run_state 为唯一依据。
CREATE TABLE agent_run_submission (
    run_id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    request_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    payload MEDIUMTEXT NOT NULL,
    started BOOLEAN NOT NULL DEFAULT FALSE,
    lease_owner VARCHAR(64),
    lease_until DATETIME(6),
    event_count INT NOT NULL DEFAULT 0,
    event_bytes BIGINT NOT NULL DEFAULT 0,
    events_expires_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_run_request (tenant_id,user_id,request_key),
    KEY idx_run_dispatch (started,created_at,run_id),
    KEY idx_run_execution_lease (lease_until,run_id),
    KEY idx_run_event_expiry (events_expires_at,run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE agent_run_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    payload MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_run_event_cursor (run_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
