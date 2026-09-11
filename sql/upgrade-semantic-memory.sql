-- 已有数据库升级：为长期语义记忆增加稳定去重键和独立置信度。
DELIMITER $$

DROP PROCEDURE IF EXISTS upgrade_semantic_memory$$
CREATE PROCEDURE upgrade_semantic_memory()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'memory_entry'
          AND COLUMN_NAME = 'semantic_key'
    ) THEN
        ALTER TABLE memory_entry
            ADD COLUMN semantic_key VARCHAR(160) DEFAULT NULL COMMENT '语义去重键' AFTER metadata_json;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'memory_entry'
          AND COLUMN_NAME = 'confidence'
    ) THEN
        ALTER TABLE memory_entry
            ADD COLUMN confidence DOUBLE DEFAULT 0 COMMENT '语义提取置信度' AFTER semantic_key;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'memory_entry'
          AND INDEX_NAME = 'uk_memory_semantic'
    ) THEN
        ALTER TABLE memory_entry
            ADD UNIQUE KEY uk_memory_semantic (tenant_id, user_id, type, semantic_key);
    END IF;
END$$

CALL upgrade_semantic_memory()$$
DROP PROCEDURE upgrade_semantic_memory$$

DELIMITER ;
