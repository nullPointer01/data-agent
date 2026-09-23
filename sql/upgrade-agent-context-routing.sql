-- 已有数据库升级：为 Agent Eval 增加路由与上下文计划标签。
DELIMITER $$

DROP PROCEDURE IF EXISTS upgrade_agent_context_routing$$
CREATE PROCEDURE upgrade_agent_context_routing()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_eval_sample'
          AND COLUMN_NAME = 'expected_intent'
    ) THEN
        ALTER TABLE agent_eval_sample
            ADD COLUMN expected_intent VARCHAR(64) DEFAULT NULL COMMENT '期望请求意图' AFTER question;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_eval_sample'
          AND COLUMN_NAME = 'expected_mode'
    ) THEN
        ALTER TABLE agent_eval_sample
            ADD COLUMN expected_mode VARCHAR(32) DEFAULT NULL COMMENT '期望执行模式' AFTER expected_intent;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_eval_sample'
          AND COLUMN_NAME = 'expected_context_plan_json'
    ) THEN
        ALTER TABLE agent_eval_sample
            ADD COLUMN expected_context_plan_json TEXT DEFAULT NULL COMMENT '期望三态上下文计划JSON'
            AFTER expected_mode;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_eval_result'
          AND COLUMN_NAME = 'observed_intent'
    ) THEN
        ALTER TABLE agent_eval_result
            ADD COLUMN observed_intent VARCHAR(64) DEFAULT NULL COMMENT '实际请求意图' AFTER trace_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_eval_result'
          AND COLUMN_NAME = 'observed_mode'
    ) THEN
        ALTER TABLE agent_eval_result
            ADD COLUMN observed_mode VARCHAR(32) DEFAULT NULL COMMENT '实际执行模式' AFTER observed_intent;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_eval_result'
          AND COLUMN_NAME = 'observed_context_plan_json'
    ) THEN
        ALTER TABLE agent_eval_result
            ADD COLUMN observed_context_plan_json TEXT DEFAULT NULL COMMENT '实际三态上下文计划JSON'
            AFTER observed_mode;
    END IF;
END$$

CALL upgrade_agent_context_routing()$$
DROP PROCEDURE upgrade_agent_context_routing$$

DELIMITER ;
