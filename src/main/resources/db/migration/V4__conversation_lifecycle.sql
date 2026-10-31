-- 归档不释放内容；删除时间独立于更新时间，晚到回调不能延长保留期。
ALTER TABLE conversation_session
    ADD COLUMN archived_at DATETIME(6) NULL,
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD INDEX idx_session_owner_state_access (tenant_id, user_id, status, last_access_at, session_id),
    ADD INDEX idx_session_deleted (status, deleted_at);

-- 历史 CLOSED 就是旧版删除：从迁移当天起算保留期，避免升级后立即清空历史。
UPDATE conversation_session SET status='DELETED', deleted_at=CURRENT_TIMESTAMP(6)
WHERE status='CLOSED';

ALTER TABLE conversation_message ADD INDEX idx_message_session_cursor (session_id, id);
ALTER TABLE agent_run_state ADD INDEX idx_run_session_status (session_id, status);
ALTER TABLE memory_extraction_task ADD INDEX idx_extraction_session (session_id);
ALTER TABLE memory_entry ADD INDEX idx_memory_session_tier (session_id, tier);

-- 旧版已删除会话同样撤销挂起任务，不改变已完成操作或审批决定。
UPDATE agent_tool_approval a JOIN agent_run_state r ON r.run_id=a.run_id
    JOIN conversation_session s ON s.session_id=r.session_id
SET a.decision_status=CASE WHEN a.decision_status='PENDING' THEN 'CANCELLED' ELSE a.decision_status END,
    a.execution_status='CANCELLED', a.execution_completed_at=CURRENT_TIMESTAMP(6),
    a.updated_at=CURRENT_TIMESTAMP(6), a.version=a.version+1
WHERE s.status='DELETED' AND a.execution_status IN ('WAITING_DECISION','READY','RUNNING');

UPDATE agent_run_state r JOIN conversation_session s ON s.session_id=r.session_id
SET r.status='CANCELLED', r.termination_reason='CANCELLED', r.error_summary='所属会话已删除',
    r.lease_owner=NULL, r.lease_until=NULL, r.completed_at=CURRENT_TIMESTAMP(6),
    r.updated_at=CURRENT_TIMESTAMP(6), r.version=r.version+1
WHERE s.status='DELETED' AND r.status IN ('CREATED','RUNNING','WAITING_APPROVAL','RESUMING');

UPDATE memory_extraction_task t JOIN conversation_session s ON s.session_id=t.session_id
SET t.status='CANCELLED', t.payload=NULL, t.lease_owner=NULL, t.lease_until=NULL,
    t.last_error='source-session-deleted', t.updated_at=CURRENT_TIMESTAMP(6)
WHERE s.status='DELETED' AND (t.status IN ('PENDING','RETRY','RUNNING') OR t.payload IS NOT NULL);
