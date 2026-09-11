-- Agent legacy 能力绑定显式迁移模板（MySQL 8）。
-- 先执行 audit-agent-capability-bindings.sql，并从 GET /api/v1/my/capabilities 核对候选身份。
-- 默认 @apply_migration=0，即使直接执行本文件也不会更新数据。

SET @target_tenant_id = '__SET_TARGET_TENANT__';
SET @apply_migration = 0;
-- 对历史 tools=NULL（旧语义为动态使用全部工具）的记录，必须人工填入当时允许的 Tool 身份快照。
-- 示例：SET @legacy_default_tool_bindings = JSON_ARRAY('tool:searchKnowledge', 'tool:calculate');
SET @legacy_default_tool_bindings = NULL;

DROP TEMPORARY TABLE IF EXISTS agent_capability_migration_candidates;
CREATE TEMPORARY TABLE agent_capability_migration_candidates (
    agent_id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    generated_bindings JSON NOT NULL
);

-- 只生成非空、合法字符串且无重复项的 legacy Tool 列表。
INSERT INTO agent_capability_migration_candidates (agent_id, tenant_id, generated_bindings)
SELECT
    profile.agent_id,
    profile.tenant_id,
    JSON_MERGE_PRESERVE(
        JSON_ARRAYAGG(CONCAT(
            'tool:',
            CASE
                WHEN TRIM(tool_entry.tool_name) LIKE 'tool:%'
                    THEN TRIM(SUBSTRING(TRIM(tool_entry.tool_name), 6))
                ELSE TRIM(tool_entry.tool_name)
            END)),
        CASE
            WHEN profile.skill_id IS NULL OR TRIM(profile.skill_id) = '' THEN JSON_ARRAY()
            ELSE JSON_ARRAY(CONCAT('skill:', TRIM(profile.skill_id)))
        END
    ) AS generated_bindings
FROM agent_profile AS profile
JOIN JSON_TABLE(
    CASE
        WHEN profile.tools IS NULL THEN JSON_ARRAY()
        WHEN JSON_VALID(profile.tools) = 0 THEN JSON_ARRAY()
        WHEN JSON_TYPE(CAST(profile.tools AS JSON)) <> 'ARRAY' THEN JSON_ARRAY()
        ELSE CAST(profile.tools AS JSON)
    END,
    '$[*]' COLUMNS (
        position FOR ORDINALITY,
        tool_name VARCHAR(255) PATH '$'
    )
) AS tool_entry ON TRUE
WHERE profile.tenant_id = @target_tenant_id
  AND profile.capability_bindings IS NULL
  AND profile.tools IS NOT NULL
  AND JSON_VALID(profile.tools) = 1
  AND CASE
      WHEN JSON_VALID(profile.tools) = 1
          THEN JSON_TYPE(CAST(profile.tools AS JSON)) = 'ARRAY'
      ELSE FALSE
  END
  AND CASE
      WHEN JSON_VALID(profile.tools) = 1
           AND JSON_TYPE(CAST(profile.tools AS JSON)) = 'ARRAY'
          THEN JSON_LENGTH(CAST(profile.tools AS JSON)) BETWEEN 1 AND 64
      ELSE FALSE
  END
  AND NOT EXISTS (
      SELECT 1
      FROM JSON_TABLE(
          CASE
              WHEN profile.tools IS NULL THEN JSON_ARRAY()
              WHEN JSON_VALID(profile.tools) = 0 THEN JSON_ARRAY()
              WHEN JSON_TYPE(CAST(profile.tools AS JSON)) <> 'ARRAY' THEN JSON_ARRAY()
              ELSE CAST(profile.tools AS JSON)
          END,
          '$[*]' COLUMNS (check_position FOR ORDINALITY)
      ) AS checked
      WHERE JSON_TYPE(JSON_EXTRACT(
              profile.tools, CONCAT('$[', checked.check_position - 1, ']'))) <> 'STRING'
         OR TRIM(JSON_UNQUOTE(JSON_EXTRACT(
              profile.tools, CONCAT('$[', checked.check_position - 1, ']')))) = ''
         OR TRIM(JSON_UNQUOTE(JSON_EXTRACT(
              profile.tools, CONCAT('$[', checked.check_position - 1, ']')))) = 'tool:'
  )
GROUP BY profile.agent_id, profile.tenant_id, profile.skill_id
HAVING COUNT(*) = COUNT(DISTINCT CASE
    WHEN TRIM(tool_entry.tool_name) LIKE 'tool:%'
        THEN TRIM(SUBSTRING(TRIM(tool_entry.tool_name), 6))
    ELSE TRIM(tool_entry.tool_name)
END);

-- tools=[] 是明确零 Tool；仍保留单 Skill（如存在）。
INSERT INTO agent_capability_migration_candidates (agent_id, tenant_id, generated_bindings)
SELECT
    profile.agent_id,
    profile.tenant_id,
    CASE
        WHEN profile.skill_id IS NULL OR TRIM(profile.skill_id) = '' THEN JSON_ARRAY()
        ELSE JSON_ARRAY(CONCAT('skill:', TRIM(profile.skill_id)))
    END
FROM agent_profile AS profile
WHERE profile.tenant_id = @target_tenant_id
  AND profile.capability_bindings IS NULL
  AND profile.tools IS NOT NULL
  AND JSON_VALID(profile.tools) = 1
  AND JSON_TYPE(CAST(profile.tools AS JSON)) = 'ARRAY'
  AND JSON_LENGTH(CAST(profile.tools AS JSON)) = 0;

-- tools=NULL 只有在操作者显式提供权限快照时才进入候选，禁止用当前注册表静默扩权。
INSERT INTO agent_capability_migration_candidates (agent_id, tenant_id, generated_bindings)
SELECT
    profile.agent_id,
    profile.tenant_id,
    JSON_MERGE_PRESERVE(
        CAST(@legacy_default_tool_bindings AS JSON),
        CASE
            WHEN profile.skill_id IS NULL OR TRIM(profile.skill_id) = '' THEN JSON_ARRAY()
            ELSE JSON_ARRAY(CONCAT('skill:', TRIM(profile.skill_id)))
        END
    )
FROM agent_profile AS profile
WHERE profile.tenant_id = @target_tenant_id
  AND profile.capability_bindings IS NULL
  AND profile.tools IS NULL
  AND @legacy_default_tool_bindings IS NOT NULL
  AND JSON_VALID(@legacy_default_tool_bindings) = 1
  AND JSON_TYPE(CAST(@legacy_default_tool_bindings AS JSON)) = 'ARRAY'
  AND JSON_LENGTH(CAST(@legacy_default_tool_bindings AS JSON))
      <= 64 - CASE WHEN profile.skill_id IS NULL OR TRIM(profile.skill_id) = '' THEN 0 ELSE 1 END
  AND NOT EXISTS (
      SELECT 1
      FROM JSON_TABLE(
          CAST(@legacy_default_tool_bindings AS JSON),
          '$[*]' COLUMNS (position FOR ORDINALITY)
      ) AS item
      WHERE JSON_TYPE(JSON_EXTRACT(
              @legacy_default_tool_bindings, CONCAT('$[', item.position - 1, ']'))) <> 'STRING'
         OR TRIM(JSON_UNQUOTE(JSON_EXTRACT(
              @legacy_default_tool_bindings, CONCAT('$[', item.position - 1, ']'))))
              NOT REGEXP '^tool:.+'
  )
  AND JSON_LENGTH(CAST(@legacy_default_tool_bindings AS JSON)) = (
      SELECT COUNT(DISTINCT TRIM(JSON_UNQUOTE(JSON_EXTRACT(
          @legacy_default_tool_bindings, CONCAT('$[', item.position - 1, ']')))))
      FROM JSON_TABLE(
          CAST(@legacy_default_tool_bindings AS JSON),
          '$[*]' COLUMNS (position FOR ORDINALITY)
      ) AS item
  );

-- 必须先人工核对预览；没有提供快照的 tools=NULL 记录不会进入候选表。
SELECT agent_id, tenant_id, generated_bindings
FROM agent_capability_migration_candidates
ORDER BY agent_id;

-- 只有操作者显式把 @apply_migration 改为 1 才会更新；并发变化时 WHERE 条件阻止覆盖。
UPDATE agent_profile AS profile
JOIN agent_capability_migration_candidates AS candidate
  ON candidate.agent_id = profile.agent_id
 AND candidate.tenant_id = profile.tenant_id
SET profile.capability_bindings = CAST(candidate.generated_bindings AS CHAR)
WHERE @apply_migration = 1
  AND profile.tenant_id = @target_tenant_id
  AND profile.capability_bindings IS NULL;

SELECT ROW_COUNT() AS migrated_profile_count, @apply_migration AS apply_migration;

DROP TEMPORARY TABLE agent_capability_migration_candidates;
