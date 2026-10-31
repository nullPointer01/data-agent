-- Skill 是程序性记忆；候选和发布版本仍共用业务主库，不新增轮询任务。
ALTER TABLE skill_config
    ADD COLUMN tool_dependencies TEXT NULL,
    ADD COLUMN private_skill BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN review_status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN source_run_id VARCHAR(64) NULL,
    ADD COLUMN evidence TEXT NULL;
ALTER TABLE skill_prompt_history ADD COLUMN tool_dependencies TEXT NULL;
CREATE INDEX idx_skill_personal_review ON skill_config (tenant_id, created_by, private_skill, review_status, created_at);
-- 每个用户每个 Run 最多形成一个候选，重试不产生重复技能。
CREATE UNIQUE INDEX uk_skill_learning_run ON skill_config (tenant_id, created_by, source_run_id);
