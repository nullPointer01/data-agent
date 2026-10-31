-- 日配额必须先预占再发送模型请求；不修改 V1～V6，不删除历史 Token 明细。
ALTER TABLE token_usage
    MODIFY COLUMN model_id VARCHAR(255) NULL COMMENT '模型配置ID，与 model_config 主键长度一致',
    ADD COLUMN invocation_id VARCHAR(36) NULL COMMENT '实际模型尝试ID；唯一结算身份',
    ADD COLUMN quota_date DATE NULL COMMENT '调用开始时所属配额日，跨午夜仍结算原日',
    ADD COLUMN accounting_status VARCHAR(16) NOT NULL DEFAULT 'LEGACY' COMMENT 'ACTUAL/ESTIMATED/UNKNOWN/LEGACY',
    ADD UNIQUE KEY uk_token_usage_invocation (invocation_id);

CREATE TABLE token_daily_quota (
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    quota_date DATE NOT NULL,
    charged_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '已记账，包含 UNKNOWN 保守扣额',
    reserved_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '尚未结算的预占',
    uncertain_tokens BIGINT NOT NULL DEFAULT 0 COMMENT 'charged 中 UNKNOWN 的部分',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tenant_id, user_id, quota_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户每日Token配额账本';

CREATE TABLE token_reservation (
    invocation_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    quota_date DATE NOT NULL,
    estimated_input_tokens BIGINT NOT NULL,
    reserved_tokens BIGINT NOT NULL,
    max_output_tokens INT NOT NULL,
    model_id VARCHAR(255) NULL,
    model_name VARCHAR(255) NULL,
    session_id VARCHAR(255) NULL,
    run_id VARCHAR(64) NULL,
    state VARCHAR(16) NOT NULL COMMENT 'PENDING/DISPATCHED/SETTLED/UNKNOWN/RELEASED',
    charged_tokens BIGINT NOT NULL DEFAULT 0,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (invocation_id),
    KEY idx_token_reservation_owner_expiry (tenant_id, user_id, state, expires_at, quota_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实际模型调用Token预占与幂等结算';

-- 旧记录没有厂商/估算标识，保留 LEGACY，不能伪称其全部为真实用量。
-- DATE 沿用旧 created_at 的本地日期口径；新调用使用配置的固定配额时区。
UPDATE token_usage SET quota_date = DATE(created_at) WHERE created_at IS NOT NULL;
INSERT INTO token_daily_quota(tenant_id, user_id, quota_date, charged_tokens)
    SELECT tenant_id, user_id, quota_date, SUM(GREATEST(total_tokens, 0)) FROM token_usage
    WHERE tenant_id IS NOT NULL AND user_id IS NOT NULL AND quota_date IS NOT NULL
    GROUP BY tenant_id, user_id, quota_date;
