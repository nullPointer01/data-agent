-- 已有数据库升级：将 assistant 消息与 canonical Agent Run 关联。
-- run_id 保持可空且不增加外键，兼容历史消息、Trace 写入失败和 durable 关闭场景。
DELIMITER $$

DROP PROCEDURE IF EXISTS upgrade_agent_run_evidence$$
CREATE PROCEDURE upgrade_agent_run_evidence()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'conversation_message'
          AND COLUMN_NAME = 'run_id'
    ) THEN
        ALTER TABLE conversation_message
            ADD COLUMN run_id VARCHAR(64) DEFAULT NULL COMMENT '关联的Agent Run ID' AFTER model_used;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'conversation_message'
          AND INDEX_NAME = 'idx_conversation_message_run_id'
    ) THEN
        ALTER TABLE conversation_message
            ADD KEY idx_conversation_message_run_id (run_id);
    END IF;
END$$

CALL upgrade_agent_run_evidence()$$
DROP PROCEDURE upgrade_agent_run_evidence$$

DELIMITER ;
