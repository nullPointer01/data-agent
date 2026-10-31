-- 列表查询的索引随版本交付；保留原索引，不修改已有迁移或历史记录。
CREATE INDEX idx_sys_user_list_page ON sys_user (created_at, id);
CREATE INDEX idx_audit_tenant_user_page ON audit_log (tenant_id, user_id, created_at, id);
CREATE INDEX idx_trace_tenant_page ON agent_execution_trace (tenant_id, created_at, trace_id);
CREATE INDEX idx_trace_tenant_user_page ON agent_execution_trace (tenant_id, user_id, created_at, trace_id);
CREATE INDEX idx_memory_user_page ON memory_entry (tenant_id, user_id, created_at, memory_id);
CREATE INDEX idx_memory_user_tier_page ON memory_entry (tenant_id, user_id, tier, created_at, memory_id);
