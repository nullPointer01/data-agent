-- Agent Eval 版本化数据集、运行与样本级证据。
-- 仅创建缺失表，可在同一 MySQL schema 中重复执行。

CREATE TABLE IF NOT EXISTS agent_eval_dataset (
    dataset_id VARCHAR(64) NOT NULL COMMENT '数据集版本ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    name VARCHAR(128) NOT NULL COMMENT '数据集名称',
    description VARCHAR(512) DEFAULT NULL COMMENT '数据集说明',
    dataset_version INT NOT NULL COMMENT '数据集版本',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/READY/ARCHIVED',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    row_version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (dataset_id),
    UNIQUE KEY uk_agent_eval_dataset_name_version (tenant_id, name, dataset_version),
    KEY idx_agent_eval_dataset_tenant_status (tenant_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent评测数据集版本';

CREATE TABLE IF NOT EXISTS agent_eval_sample (
    sample_id VARCHAR(64) NOT NULL COMMENT '样本ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    dataset_id VARCHAR(64) NOT NULL COMMENT '数据集版本ID',
    dataset_version INT NOT NULL COMMENT '数据集版本快照',
    sample_key VARCHAR(64) NOT NULL COMMENT '版本内稳定样本键',
    question TEXT NOT NULL COMMENT '固定任务输入',
    task_contract_json LONGTEXT NOT NULL COMMENT '任务目标与确定性成功标准JSON',
    expected_tools_json TEXT DEFAULT NULL COMMENT '期望工具名JSON数组',
    expected_approval_required BIT(1) DEFAULT NULL COMMENT '是否期望触发审批',
    invalid_loop_expected BIT(1) DEFAULT NULL COMMENT '是否预期出现无效循环，通常为0',
    rag_expected_references_json TEXT DEFAULT NULL COMMENT 'RAG期望引用ID JSON数组',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '样本顺序',
    enabled BIT(1) NOT NULL DEFAULT 1 COMMENT '是否参与评测',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (sample_id),
    UNIQUE KEY uk_agent_eval_sample_key (dataset_id, sample_key),
    KEY idx_agent_eval_sample_dataset (tenant_id, dataset_id, dataset_version, sort_order),
    CONSTRAINT fk_agent_eval_sample_dataset FOREIGN KEY (dataset_id) REFERENCES agent_eval_dataset (dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent评测样本';

CREATE TABLE IF NOT EXISTS agent_eval_run (
    eval_run_id VARCHAR(64) NOT NULL COMMENT '评测运行ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    dataset_id VARCHAR(64) NOT NULL COMMENT '数据集版本ID',
    dataset_version INT NOT NULL COMMENT '数据集版本快照',
    agent_id VARCHAR(64) NOT NULL COMMENT 'Agent ID',
    agent_profile_version VARCHAR(64) NOT NULL COMMENT 'Agent配置版本快照',
    model_id VARCHAR(64) DEFAULT NULL COMMENT '模型配置ID',
    harness_config_identity VARCHAR(128) NOT NULL COMMENT 'Harness配置身份',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
    total_samples INT NOT NULL DEFAULT 0 COMMENT '样本总数',
    completed_samples INT NOT NULL DEFAULT 0 COMMENT '已执行样本数',
    passed_samples INT NOT NULL DEFAULT 0 COMMENT '目标达成样本数',
    failed_samples INT NOT NULL DEFAULT 0 COMMENT '目标未达成样本数',
    not_evaluated_samples INT NOT NULL DEFAULT 0 COMMENT '无法评估样本数',
    metrics_json LONGTEXT DEFAULT NULL COMMENT '六类聚合指标及不可用原因',
    error_summary VARCHAR(1024) DEFAULT NULL COMMENT '运行级安全错误摘要',
    created_by VARCHAR(64) NOT NULL COMMENT '发起人',
    row_version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    started_at DATETIME(6) NOT NULL COMMENT '开始时间',
    completed_at DATETIME(6) DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (eval_run_id),
    KEY idx_agent_eval_run_tenant_started (tenant_id, started_at),
    KEY idx_agent_eval_run_dataset (tenant_id, dataset_id, dataset_version),
    CONSTRAINT fk_agent_eval_run_dataset FOREIGN KEY (dataset_id) REFERENCES agent_eval_dataset (dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent评测运行';

CREATE TABLE IF NOT EXISTS agent_eval_result (
    result_id VARCHAR(64) NOT NULL COMMENT '样本结果ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    eval_run_id VARCHAR(64) NOT NULL COMMENT '评测运行ID',
    sample_id VARCHAR(64) NOT NULL COMMENT '样本ID',
    sample_key VARCHAR(64) NOT NULL COMMENT '样本稳定键',
    agent_run_id VARCHAR(64) DEFAULT NULL COMMENT '关联Agent Run ID',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT '关联安全Trace ID',
    outcome_status VARCHAR(32) NOT NULL COMMENT 'ACHIEVED/NOT_ACHIEVED/NOT_EVALUATED',
    reason_code VARCHAR(128) DEFAULT NULL COMMENT '稳定判定原因码',
    outcome_evaluation_json LONGTEXT DEFAULT NULL COMMENT '逐标准安全评估JSON',
    observed_tools_json TEXT DEFAULT NULL COMMENT '实际工具名JSON数组',
    approval_observed BIT(1) DEFAULT NULL COMMENT '是否观察到审批',
    invalid_loop_observed BIT(1) DEFAULT NULL COMMENT '是否观察到无效循环',
    duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT 'Agent Run耗时',
    token_usage BIGINT NOT NULL DEFAULT 0 COMMENT 'Token用量',
    token_usage_estimated BIT(1) NOT NULL DEFAULT 0 COMMENT 'Token是否包含估算',
    error_summary VARCHAR(1024) DEFAULT NULL COMMENT '样本级安全错误摘要',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (result_id),
    UNIQUE KEY uk_agent_eval_result_sample (eval_run_id, sample_id),
    KEY idx_agent_eval_result_run (tenant_id, eval_run_id, created_at),
    KEY idx_agent_eval_result_agent_run (tenant_id, agent_run_id),
    CONSTRAINT fk_agent_eval_result_run FOREIGN KEY (eval_run_id) REFERENCES agent_eval_run (eval_run_id),
    CONSTRAINT fk_agent_eval_result_sample FOREIGN KEY (sample_id) REFERENCES agent_eval_sample (sample_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent评测样本结果';
