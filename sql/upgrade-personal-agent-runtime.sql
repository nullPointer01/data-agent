-- 已有数据库升级：个人 Agent 默认配置与 AUTO 路由所需字段。
-- application-local.yml 使用 ddl-auto=update 时会自动补列；生产环境请执行本脚本后再启动。
DELIMITER $$

DROP PROCEDURE IF EXISTS upgrade_personal_agent_runtime$$
CREATE PROCEDURE upgrade_personal_agent_runtime()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_profile' AND COLUMN_NAME = 'execution_mode'
    ) THEN
        ALTER TABLE agent_profile
            ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'auto'
                COMMENT 'auto/chat/react/orchestrated执行模式';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_profile' AND COLUMN_NAME = 'default_agent'
    ) THEN
        ALTER TABLE agent_profile
            ADD COLUMN default_agent BIT(1) NOT NULL DEFAULT 0 COMMENT '是否为用户默认Agent' AFTER execution_mode;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_profile'
          AND INDEX_NAME = 'idx_agent_profile_user_default'
    ) THEN
        ALTER TABLE agent_profile
            ADD KEY idx_agent_profile_user_default (tenant_id, created_by, default_agent);
    END IF;
END$$

CALL upgrade_personal_agent_runtime()$$
DROP PROCEDURE upgrade_personal_agent_runtime$$

DELIMITER ;
