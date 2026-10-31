-- 兼容旧 Hibernate 库：配额、文件哈希和运行模式升级统一由 Flyway 执行，失败时阻止启动。
SET @quota_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'daily_token_limit') = 0,
    'ALTER TABLE sys_user ADD COLUMN daily_token_limit BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE migration_stmt FROM @quota_ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @file_hash_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'file_metadata' AND column_name = 'content_hash') = 0,
    'ALTER TABLE file_metadata ADD COLUMN content_hash VARCHAR(64) DEFAULT NULL',
    'SELECT 1');
PREPARE migration_stmt FROM @file_hash_ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

UPDATE sys_user SET daily_token_limit = 0 WHERE daily_token_limit IS NULL;

-- 新增的 PLANNING/SUPERVISOR 等模式不能受旧 ENUM 值集合限制。
SET @mode_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'agent_run_state' AND column_name = 'mode'
       AND (data_type != 'varchar' OR character_maximum_length < 32)) > 0,
    'ALTER TABLE agent_run_state MODIFY COLUMN mode VARCHAR(32) NOT NULL',
    'SELECT 1');
PREPARE migration_stmt FROM @mode_ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
