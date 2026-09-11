-- 已有数据库第一阶段升级：添加统一能力字段。
-- 此时暂时允许 NULL，仅用于离线审计和迁移；应用运行前必须完成迁移及收口脚本。
DELIMITER $$

DROP PROCEDURE IF EXISTS upgrade_agent_capability_bindings$$
CREATE PROCEDURE upgrade_agent_capability_bindings()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'agent_profile'
          AND COLUMN_NAME = 'capability_bindings'
    ) THEN
        ALTER TABLE agent_profile
            ADD COLUMN capability_bindings TEXT DEFAULT NULL
                COMMENT '统一能力稳定身份JSON数组；NULL表示尚未迁移'
                AFTER tools;
    END IF;
END$$

CALL upgrade_agent_capability_bindings()$$
DROP PROCEDURE upgrade_agent_capability_bindings$$

DELIMITER ;
