-- Agent 能力配置只读审计（MySQL 8）。
-- 本文件只包含 SELECT/CTE，不会修改 agent_profile。

-- 1. 按持久化状态汇总。统一字段非 NULL 时始终是权威字段。
WITH capability_state AS (
    SELECT
        agent_id,
        tenant_id,
        created_by,
        CASE
            WHEN capability_bindings IS NOT NULL THEN
                CASE
                    WHEN TRIM(capability_bindings) = '' THEN 'UNIFIED_INVALID_BLANK'
                    WHEN JSON_VALID(capability_bindings) = 0 THEN 'UNIFIED_INVALID_JSON'
                    WHEN JSON_TYPE(CAST(capability_bindings AS JSON)) <> 'ARRAY'
                        THEN 'UNIFIED_INVALID_NOT_ARRAY'
                    WHEN JSON_LENGTH(CAST(capability_bindings AS JSON)) = 0
                        THEN 'UNIFIED_EXPLICIT_EMPTY'
                    ELSE 'UNIFIED_VALUES_REVIEW_ENTRIES'
                END
            WHEN tools IS NULL THEN 'LEGACY_NULL_DEFAULT_TOOLS'
            WHEN TRIM(tools) = '' THEN 'LEGACY_INVALID_BLANK'
            WHEN JSON_VALID(tools) = 0 THEN 'LEGACY_INVALID_JSON'
            WHEN JSON_TYPE(CAST(tools AS JSON)) <> 'ARRAY' THEN 'LEGACY_INVALID_NOT_ARRAY'
            WHEN JSON_LENGTH(CAST(tools AS JSON)) = 0 THEN 'LEGACY_EXPLICIT_EMPTY'
            ELSE 'LEGACY_VALUES_REVIEW_ENTRIES'
        END AS configuration_state
    FROM agent_profile
)
SELECT configuration_state, COUNT(*) AS profile_count
FROM capability_state
GROUP BY configuration_state
ORDER BY configuration_state;

-- 2. 列出语法或顶层类型损坏的记录，不输出原始 JSON。
WITH capability_state AS (
    SELECT
        agent_id,
        tenant_id,
        created_by,
        CASE
            WHEN capability_bindings IS NOT NULL THEN
                CASE
                    WHEN TRIM(capability_bindings) = '' THEN 'UNIFIED_INVALID_BLANK'
                    WHEN JSON_VALID(capability_bindings) = 0 THEN 'UNIFIED_INVALID_JSON'
                    WHEN JSON_TYPE(CAST(capability_bindings AS JSON)) <> 'ARRAY'
                        THEN 'UNIFIED_INVALID_NOT_ARRAY'
                    ELSE 'UNIFIED_VALID_JSON_ARRAY'
                END
            WHEN tools IS NULL THEN 'LEGACY_NULL_DEFAULT_TOOLS'
            WHEN TRIM(tools) = '' THEN 'LEGACY_INVALID_BLANK'
            WHEN JSON_VALID(tools) = 0 THEN 'LEGACY_INVALID_JSON'
            WHEN JSON_TYPE(CAST(tools AS JSON)) <> 'ARRAY' THEN 'LEGACY_INVALID_NOT_ARRAY'
            ELSE 'LEGACY_VALID_JSON_ARRAY'
        END AS configuration_state
    FROM agent_profile
)
SELECT agent_id, tenant_id, created_by, configuration_state
FROM capability_state
WHERE configuration_state LIKE '%INVALID%'
ORDER BY tenant_id, agent_id;

-- 3. 检查合法 JSON 数组中的非字符串、空值和重复值。
-- JSON_TABLE 只接收预先替换过的合法数组，坏 JSON 不会进入解析器。
WITH sanitized AS (
    SELECT
        agent_id,
        tenant_id,
        'capability_bindings' AS field_name,
        CASE
            WHEN capability_bindings IS NULL THEN JSON_ARRAY()
            WHEN JSON_VALID(capability_bindings) = 0 THEN JSON_ARRAY()
            WHEN JSON_TYPE(CAST(capability_bindings AS JSON)) <> 'ARRAY' THEN JSON_ARRAY()
            ELSE CAST(capability_bindings AS JSON)
        END AS json_value
    FROM agent_profile
    UNION ALL
    SELECT
        agent_id,
        tenant_id,
        'tools' AS field_name,
        CASE
            WHEN capability_bindings IS NOT NULL THEN JSON_ARRAY()
            WHEN tools IS NULL THEN JSON_ARRAY()
            WHEN JSON_VALID(tools) = 0 THEN JSON_ARRAY()
            WHEN JSON_TYPE(CAST(tools AS JSON)) <> 'ARRAY' THEN JSON_ARRAY()
            ELSE CAST(tools AS JSON)
        END AS json_value
    FROM agent_profile
), entries AS (
    SELECT
        sanitized.agent_id,
        sanitized.tenant_id,
        sanitized.field_name,
        item.position,
        JSON_EXTRACT(sanitized.json_value, CONCAT('$[', item.position - 1, ']')) AS entry_value
    FROM sanitized
    JOIN JSON_TABLE(
        sanitized.json_value,
        '$[*]' COLUMNS (position FOR ORDINALITY)
    ) AS item ON TRUE
), invalid_entries AS (
    SELECT agent_id, tenant_id, field_name, position, 'NON_STRING_ENTRY' AS reason
    FROM entries
    WHERE JSON_TYPE(entry_value) <> 'STRING'
    UNION ALL
    SELECT agent_id, tenant_id, field_name, position, 'BLANK_ENTRY' AS reason
    FROM entries
    WHERE JSON_TYPE(entry_value) = 'STRING'
      AND TRIM(JSON_UNQUOTE(entry_value)) = ''
    UNION ALL
    SELECT agent_id, tenant_id, field_name, position, 'UNSUPPORTED_CAPABILITY_IDENTITY' AS reason
    FROM entries
    WHERE field_name = 'capability_bindings'
      AND JSON_TYPE(entry_value) = 'STRING'
      AND TRIM(JSON_UNQUOTE(entry_value)) <> ''
      AND TRIM(JSON_UNQUOTE(entry_value)) NOT REGEXP '^(tool|skill|agent):.+'
), duplicate_entries AS (
    SELECT
        agent_id,
        tenant_id,
        field_name,
        MIN(position) AS position,
        'DUPLICATE_ENTRY' AS reason
    FROM entries
    WHERE JSON_TYPE(entry_value) = 'STRING'
      AND TRIM(JSON_UNQUOTE(entry_value)) <> ''
    GROUP BY agent_id, tenant_id, field_name, TRIM(JSON_UNQUOTE(entry_value))
    HAVING COUNT(*) > 1
), size_violations AS (
    SELECT
        agent_id,
        tenant_id,
        field_name,
        0 AS position,
        'TOO_MANY_ENTRIES' AS reason
    FROM sanitized
    WHERE JSON_LENGTH(json_value) > 64
)
SELECT agent_id, tenant_id, field_name, position, reason
FROM invalid_entries
UNION ALL
SELECT agent_id, tenant_id, field_name, position, reason
FROM duplicate_entries
UNION ALL
SELECT agent_id, tenant_id, field_name, position, reason
FROM size_violations
ORDER BY tenant_id, agent_id, field_name, position;

-- 4. 单独列出仍依赖动态默认 Tool 的 legacy Profile，迁移时必须人工确认具体 Tool 快照。
SELECT agent_id, tenant_id, created_by, name
FROM agent_profile
WHERE capability_bindings IS NULL
  AND tools IS NULL
ORDER BY tenant_id, agent_id;
