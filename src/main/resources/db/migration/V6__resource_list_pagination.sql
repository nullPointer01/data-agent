-- 第二批列表分页：沿用已有表和已有主键。未修改 V1～V5，也不新增业务表。
CREATE INDEX idx_agent_tenant_page ON agent_profile (tenant_id, updated_at, agent_id);
CREATE INDEX idx_agent_owner_page ON agent_profile (tenant_id, created_by, updated_at, agent_id);
CREATE INDEX idx_agent_owner_role_page ON agent_profile (tenant_id, created_by, default_agent, updated_at, agent_id);
CREATE INDEX idx_model_tenant_page ON model_config (tenant_id, created_at, model_id);
CREATE INDEX idx_skill_tenant_page ON skill_config (tenant_id, created_at, skill_id);
CREATE INDEX idx_skill_history_page ON skill_prompt_history (tenant_id, skill_id, version, id);
CREATE INDEX idx_datasource_tenant_page ON datasource_config (tenant_id, created_at, datasource_id);
CREATE INDEX idx_knowledge_owner_page ON knowledge_entry (tenant_id, created_by, created_at, knowledge_id);
CREATE INDEX idx_knowledge_tenant_page ON knowledge_entry (tenant_id, created_at, knowledge_id);
CREATE INDEX idx_file_tenant_page ON file_metadata (tenant_id, uploaded_at, file_id);
CREATE INDEX idx_approval_tenant_page ON agent_tool_approval (tenant_id, requested_at, approval_id);
CREATE INDEX idx_token_tenant_page ON token_usage (tenant_id, created_at, id);
CREATE INDEX idx_eval_sample_page ON agent_eval_sample
    (tenant_id, dataset_id, dataset_version, enabled, sort_order, sample_key, sample_id);
-- READY 数据集、按状态审批、评测运行沿用 V1 索引；InnoDB 二级索引末尾已包含主键。
