-- Agent 能力字段最终收口（MySQL 8）。
-- 必须先执行 audit-agent-capability-bindings.sql 和 migrate-agent-capability-bindings.sql。
-- 任一记录未迁移或 JSON 合同损坏时，本脚本会中止，不会删除旧列。

DELIMITER $$

DROP PROCEDURE IF EXISTS finalize_agent_capability_bindings$$
CREATE PROCEDURE finalize_agent_capability_bindings()
BEGIN
    DECLARE invalid_profile_count BIGINT DEFAULT 0;
    DECLARE invalid_entry_count BIGINT DEFAULT 0;
    DECLARE duplicate_entry_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO invalid_profile_count
    FROM agent_profile
    WHERE capability_bindings IS NULL
       OR CASE
           WHEN JSON_VALID(capability_bindings) = 1
               THEN JSON_TYPE(CAST(capability_bindings AS JSON)) <> 'ARRAY'
           ELSE TRUE
       END;

    IF invalid_profile_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Agent capability migration incomplete; inspect audit results first';
    END IF;

    SELECT COUNT(*) INTO invalid_entry_count
    FROM agent_profile AS profile
    WHERE JSON_LENGTH(CAST(profile.capability_bindings AS JSON)) > 64
       OR EXISTS (
           SELECT 1
           FROM JSON_TABLE(
               CAST(profile.capability_bindings AS JSON),
               '$[*]' COLUMNS (position FOR ORDINALITY)
           ) AS item
           WHERE JSON_TYPE(JSON_EXTRACT(
                   profile.capability_bindings, CONCAT('$[', item.position - 1, ']'))) <> 'STRING'
              OR TRIM(JSON_UNQUOTE(JSON_EXTRACT(
                   profile.capability_bindings, CONCAT('$[', item.position - 1, ']')))) = ''
              OR TRIM(JSON_UNQUOTE(JSON_EXTRACT(
                   profile.capability_bindings, CONCAT('$[', item.position - 1, ']'))))
                   NOT REGEXP '^(tool|skill|agent):.+'
       );

    SELECT COUNT(*) INTO duplicate_entry_count
    FROM (
        SELECT
            profile.agent_id,
            TRIM(JSON_UNQUOTE(JSON_EXTRACT(
                profile.capability_bindings, CONCAT('$[', item.position - 1, ']')))) AS binding_identity
        FROM agent_profile AS profile
        JOIN JSON_TABLE(
            CAST(profile.capability_bindings AS JSON),
            '$[*]' COLUMNS (position FOR ORDINALITY)
        ) AS item ON TRUE
        GROUP BY profile.agent_id, binding_identity
        HAVING COUNT(*) > 1
    ) AS duplicate_bindings;

    IF invalid_entry_count > 0 OR duplicate_entry_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Agent capability entries are invalid; inspect audit results first';
    END IF;

    ALTER TABLE agent_profile
        MODIFY COLUMN capability_bindings TEXT NOT NULL
            COMMENT '统一能力稳定身份JSON数组；[]表示明确无能力';

    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'agent_profile'
          AND COLUMN_NAME = 'skill_id'
    ) THEN
        ALTER TABLE agent_profile DROP COLUMN skill_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'agent_profile'
          AND COLUMN_NAME = 'datasource_id'
    ) THEN
        ALTER TABLE agent_profile DROP COLUMN datasource_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'agent_profile'
          AND COLUMN_NAME = 'tools'
    ) THEN
        ALTER TABLE agent_profile DROP COLUMN tools;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'agent_profile'
          AND COLUMN_NAME = 'type'
    ) THEN
        ALTER TABLE agent_profile DROP COLUMN type;
    END IF;
END$$

CALL finalize_agent_capability_bindings()$$
DROP PROCEDURE finalize_agent_capability_bindings$$

DELIMITER ;
