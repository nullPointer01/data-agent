-- ============================================================
-- Data Agent MySQL 初始化脚本
-- 数据库: data_agent
-- 字符集: utf8mb4
--
-- 说明:
-- 1. 本脚本不会 DROP DATABASE，适合本地和已有开发库反复执行。
-- 2. 表结构以当前 JPA 实体为准，生产环境建议使用迁移工具接管后续变更。
-- 3. 密钥、模型 API Key、管理员密码不写入脚本，由环境变量或接口初始化。
-- ============================================================

CREATE DATABASE IF NOT EXISTS data_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE data_agent;

-- ============================================================
-- 1. 用户与 RBAC
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id VARCHAR(64) NOT NULL COMMENT '用户ID',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt密码',
    nickname VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    enabled BIT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    daily_token_limit BIGINT NOT NULL DEFAULT 0 COMMENT '每日Token上限: 0=全局默认, -1=不限量, >0=个人上限',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_sys_user_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS sys_role (
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    name VARCHAR(64) NOT NULL COMMENT '角色名称',
    description VARCHAR(512) DEFAULT NULL COMMENT '角色说明',
    system_role BIT(1) NOT NULL DEFAULT 1 COMMENT '是否系统内置角色',
    enabled BIT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sys_permission (
    permission_code VARCHAR(64) NOT NULL COMMENT '权限编码',
    name VARCHAR(64) NOT NULL COMMENT '权限名称',
    description VARCHAR(512) DEFAULT NULL COMMENT '权限说明',
    resource_type VARCHAR(64) DEFAULT NULL COMMENT '资源类型',
    action VARCHAR(128) DEFAULT NULL COMMENT '动作',
    enabled BIT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    PRIMARY KEY (user_id, role_code),
    KEY idx_sys_user_role_role (role_code),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_code) REFERENCES sys_role (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    permission_code VARCHAR(64) NOT NULL COMMENT '权限编码',
    PRIMARY KEY (role_code, permission_code),
    KEY idx_sys_role_permission_permission (permission_code),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_code) REFERENCES sys_role (role_code) ON DELETE CASCADE,
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_code) REFERENCES sys_permission (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

CREATE TABLE IF NOT EXISTS sys_user_roles (
    user_id VARCHAR(255) NOT NULL COMMENT '用户ID',
    role VARCHAR(255) DEFAULT NULL COMMENT '旧角色标识',
    KEY idx_sys_user_roles_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='旧用户角色关联表，仅用于旧库迁移';

CREATE TABLE IF NOT EXISTS admin_role_request (
    request_id VARCHAR(64) NOT NULL COMMENT '申请ID',
    user_id VARCHAR(64) NOT NULL COMMENT '申请用户ID',
    username VARCHAR(64) NOT NULL COMMENT '申请用户名',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    reason VARCHAR(512) NOT NULL COMMENT '申请原因',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    reviewer_id VARCHAR(64) DEFAULT NULL COMMENT '审核人ID',
    reviewer_name VARCHAR(64) DEFAULT NULL COMMENT '审核人名称',
    review_comment VARCHAR(512) DEFAULT NULL COMMENT '审核备注',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    reviewed_at DATETIME(6) DEFAULT NULL COMMENT '审核时间',
    PRIMARY KEY (request_id),
    KEY idx_admin_role_request_user_status (user_id, status),
    KEY idx_admin_role_request_tenant_status_created (tenant_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员角色申请表';

-- ============================================================
-- 2. 配置类资源
-- ============================================================
CREATE TABLE IF NOT EXISTS model_config (
    model_id VARCHAR(255) NOT NULL COMMENT '模型ID',
    name VARCHAR(64) NOT NULL COMMENT '模型名称',
    provider VARCHAR(32) DEFAULT NULL COMMENT '模型提供商',
    api_key VARCHAR(512) DEFAULT NULL COMMENT 'API密钥',
    base_url VARCHAR(256) DEFAULT NULL COMMENT 'API基础URL',
    model_name VARCHAR(128) DEFAULT NULL COMMENT '模型标识名',
    temperature DOUBLE DEFAULT NULL COMMENT '温度参数',
    max_tokens INT DEFAULT NULL COMMENT '最大Token数',
    enabled BIT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    is_default BIT(1) DEFAULT 0 COMMENT '是否默认模型',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (model_id),
    KEY idx_model_config_tenant_id (tenant_id),
    KEY idx_model_config_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置表';

CREATE TABLE IF NOT EXISTS skill_config (
    skill_id VARCHAR(255) NOT NULL COMMENT '技能ID',
    name VARCHAR(64) NOT NULL COMMENT '技能名称',
    description VARCHAR(512) DEFAULT NULL COMMENT '技能描述',
    version VARCHAR(16) DEFAULT '1.0' COMMENT '版本号',
    api_url VARCHAR(256) DEFAULT NULL COMMENT '外部API地址',
    api_method VARCHAR(8) DEFAULT 'POST' COMMENT 'API方法',
    api_headers TEXT DEFAULT NULL COMMENT 'API请求头JSON',
    prompt_template TEXT DEFAULT NULL COMMENT '提示词模板',
    response_template TEXT DEFAULT NULL COMMENT '响应模板',
    keywords VARCHAR(512) DEFAULT NULL COMMENT '匹配关键词',
    steps TEXT DEFAULT NULL COMMENT '执行步骤',
    auto_attach VARCHAR(64) DEFAULT NULL COMMENT '自动挂载策略',
    source VARCHAR(32) DEFAULT 'manual' COMMENT '来源',
    feedback_count INT DEFAULT 0 COMMENT '反馈次数',
    positive_count INT DEFAULT 0 COMMENT '正向反馈次数',
    enabled BIT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    is_default BIT(1) DEFAULT 0 COMMENT '是否默认技能',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (skill_id),
    KEY idx_skill_config_tenant_id (tenant_id),
    KEY idx_skill_config_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能配置表';

CREATE TABLE IF NOT EXISTS skill_prompt_history (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    skill_id VARCHAR(64) NOT NULL COMMENT '技能ID',
    version INT NOT NULL COMMENT '版本号',
    prompt_template TEXT DEFAULT NULL COMMENT '提示词模板',
    steps TEXT DEFAULT NULL COMMENT '执行步骤',
    remark VARCHAR(256) DEFAULT NULL COMMENT '备注',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_skill_prompt_history_skill (skill_id),
    KEY idx_skill_prompt_history_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能提示词历史表';

CREATE TABLE IF NOT EXISTS agent_profile (
    agent_id VARCHAR(64) NOT NULL COMMENT 'Agent ID',
    name VARCHAR(64) NOT NULL COMMENT 'Agent名称',
    type VARCHAR(32) DEFAULT 'REACT' COMMENT '类型',
    description VARCHAR(512) DEFAULT NULL COMMENT '描述',
    system_prompt TEXT DEFAULT NULL COMMENT '系统提示词',
    model_id VARCHAR(64) DEFAULT NULL COMMENT '绑定模型ID',
    skill_id VARCHAR(64) DEFAULT NULL COMMENT '绑定技能ID',
    datasource_id VARCHAR(64) DEFAULT NULL COMMENT '绑定数据源ID',
    enabled BIT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (agent_id),
    KEY idx_agent_profile_tenant (tenant_id),
    KEY idx_agent_profile_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent配置表';

CREATE TABLE IF NOT EXISTS datasource_config (
    datasource_id VARCHAR(64) NOT NULL COMMENT '数据源ID',
    name VARCHAR(128) NOT NULL COMMENT '数据源名称',
    type VARCHAR(32) NOT NULL COMMENT '数据源类型',
    host VARCHAR(256) DEFAULT NULL COMMENT '主机',
    port INT DEFAULT NULL COMMENT '端口',
    db_name VARCHAR(128) DEFAULT NULL COMMENT '数据库名',
    username VARCHAR(128) DEFAULT NULL COMMENT '用户名',
    password VARCHAR(512) DEFAULT NULL COMMENT '加密密码',
    description VARCHAR(512) DEFAULT NULL COMMENT '描述',
    enabled BIT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '创建用户ID',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (datasource_id),
    KEY idx_datasource_config_tenant (tenant_id),
    KEY idx_datasource_config_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部数据源配置表';

-- ============================================================
-- 3. 文件、知识库、RAG 与记忆
-- ============================================================
CREATE TABLE IF NOT EXISTS file_metadata (
    file_id VARCHAR(255) NOT NULL COMMENT '文件ID',
    filename VARCHAR(256) NOT NULL COMMENT '原始文件名',
    content_type VARCHAR(128) DEFAULT NULL COMMENT '文件MIME类型',
    size BIGINT NOT NULL COMMENT '文件大小',
    path VARCHAR(512) DEFAULT NULL COMMENT '存储路径',
    content MEDIUMTEXT DEFAULT NULL COMMENT '文件解析内容',
    processing_status VARCHAR(32) DEFAULT 'QUEUED' COMMENT '处理状态',
    processing_error VARCHAR(1024) DEFAULT NULL COMMENT '处理失败原因',
    processed_at DATETIME(6) DEFAULT NULL COMMENT '处理完成时间',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    uploaded_by VARCHAR(64) DEFAULT NULL COMMENT '上传人',
    uploaded_at DATETIME(6) DEFAULT NULL COMMENT '上传时间',
    PRIMARY KEY (file_id),
    KEY idx_file_metadata_tenant_id (tenant_id),
    KEY idx_file_metadata_uploaded_by (uploaded_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据表';

CREATE TABLE IF NOT EXISTS knowledge_entry (
    knowledge_id VARCHAR(64) NOT NULL COMMENT '知识ID',
    name VARCHAR(200) NOT NULL COMMENT '知识名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '描述',
    source_type VARCHAR(50) DEFAULT NULL COMMENT '来源类型',
    source_filename VARCHAR(500) DEFAULT NULL COMMENT '来源文件名',
    content TEXT DEFAULT NULL COMMENT '内容',
    chunk_count INT NOT NULL DEFAULT 0 COMMENT '分块数量',
    content_length BIGINT NOT NULL DEFAULT 0 COMMENT '内容长度',
    tenant_id VARCHAR(100) DEFAULT NULL COMMENT '租户ID',
    created_by VARCHAR(100) DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (knowledge_id),
    KEY idx_knowledge_entry_tenant (tenant_id),
    KEY idx_knowledge_entry_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识条目表';

CREATE TABLE IF NOT EXISTS knowledge_sync_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    knowledge_id VARCHAR(64) NOT NULL COMMENT '知识ID',
    sync_type VARCHAR(32) NOT NULL COMMENT '同步类型',
    source_url VARCHAR(1024) DEFAULT NULL COMMENT '来源URL',
    cron_expression VARCHAR(64) DEFAULT NULL COMMENT 'Cron表达式',
    enabled BIT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    last_sync_at DATETIME(6) DEFAULT NULL COMMENT '最近同步时间',
    last_sync_status VARCHAR(64) DEFAULT NULL COMMENT '最近同步状态',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_knowledge_sync_config_knowledge (knowledge_id),
    KEY idx_knowledge_sync_config_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识同步配置表';

CREATE TABLE IF NOT EXISTS memory_entry (
    memory_id VARCHAR(64) NOT NULL COMMENT '记忆ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    session_id VARCHAR(64) DEFAULT NULL COMMENT '所属会话ID',
    tier VARCHAR(32) NOT NULL COMMENT '记忆层级',
    type VARCHAR(32) NOT NULL COMMENT '记忆类型',
    source VARCHAR(32) NOT NULL COMMENT '记忆来源',
    content TEXT DEFAULT NULL COMMENT '长期记忆原始内容；短期记忆只保留压缩摘要',
    compressed_content TEXT DEFAULT NULL COMMENT '压缩内容',
    source_content_length BIGINT DEFAULT 0 COMMENT '原始文本长度',
    stored_content_length BIGINT DEFAULT 0 COMMENT '入库存储长度',
    metadata_json TEXT DEFAULT NULL COMMENT '元数据JSON',
    key_entities_json TEXT DEFAULT NULL COMMENT '关键实体JSON',
    topic_tags_json TEXT DEFAULT NULL COMMENT '主题标签JSON',
    vector_id VARCHAR(128) DEFAULT NULL COMMENT '向量索引ID',
    relevance_score DOUBLE DEFAULT 0 COMMENT '关联度分数',
    access_count INT DEFAULT 0 COMMENT '访问次数',
    decay_weight DOUBLE DEFAULT 0 COMMENT '衰减权重',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    last_accessed_at DATETIME(6) DEFAULT NULL COMMENT '最后访问时间',
    expires_at DATETIME(6) DEFAULT NULL COMMENT '过期时间',
    PRIMARY KEY (memory_id),
    KEY idx_memory_tenant_user (tenant_id, user_id),
    KEY idx_memory_tenant_user_tier (tenant_id, user_id, tier),
    KEY idx_memory_expire (expires_at),
    KEY idx_memory_decay (tier, decay_weight, access_count),
    KEY idx_memory_updated (tier, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆条目表';

CREATE TABLE IF NOT EXISTS user_profile (
    profile_id VARCHAR(64) NOT NULL COMMENT '画像ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    display_name VARCHAR(100) DEFAULT NULL COMMENT '用户称呼',
    role VARCHAR(100) DEFAULT NULL COMMENT '角色或职位',
    company VARCHAR(100) DEFAULT NULL COMMENT '所属公司',
    industry VARCHAR(100) DEFAULT NULL COMMENT '所属行业',
    communication_style VARCHAR(32) DEFAULT NULL COMMENT '沟通风格',
    preferred_format VARCHAR(32) DEFAULT NULL COMMENT '偏好输出格式',
    expertise_areas_json TEXT DEFAULT NULL COMMENT '专业领域JSON',
    frequently_asked_topics_json TEXT DEFAULT NULL COMMENT '高频话题JSON',
    data_sources_json TEXT DEFAULT NULL COMMENT '常用数据源JSON',
    confidence DOUBLE DEFAULT 0 COMMENT '画像置信度',
    evidence_count INT DEFAULT 0 COMMENT '证据数量',
    last_active_at DATETIME(6) DEFAULT NULL COMMENT '最后活跃时间',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (profile_id),
    UNIQUE KEY uk_user_profile_tenant_user (tenant_id, user_id),
    KEY idx_user_profile_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户画像表';

-- ============================================================
-- 4. 会话、审计、质量反馈
-- ============================================================
CREATE TABLE IF NOT EXISTS conversation_session (
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    model_id VARCHAR(64) DEFAULT NULL COMMENT '使用的模型ID',
    skill_id VARCHAR(64) DEFAULT NULL COMMENT '使用的技能ID',
    title VARCHAR(256) DEFAULT NULL COMMENT '会话标题',
    status VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '会话状态',
    message_count INT DEFAULT 0 COMMENT '消息数量',
    total_tokens BIGINT DEFAULT 0 COMMENT '总消耗Token',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    last_access_at DATETIME(6) DEFAULT NULL COMMENT '最后访问时间',
    PRIMARY KEY (session_id),
    KEY idx_conversation_session_user_id (user_id),
    KEY idx_conversation_session_tenant_id (tenant_id),
    KEY idx_conversation_session_status (status),
    KEY idx_conversation_session_last_access (last_access_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';

CREATE TABLE IF NOT EXISTS conversation_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    role VARCHAR(16) NOT NULL COMMENT '角色',
    content MEDIUMTEXT DEFAULT NULL COMMENT '消息内容',
    tokens BIGINT DEFAULT 0 COMMENT '本条消息消耗Token',
    skill_used VARCHAR(64) DEFAULT NULL COMMENT '使用的技能',
    model_used VARCHAR(64) DEFAULT NULL COMMENT '使用的模型',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_conversation_message_session_id (session_id),
    KEY idx_conversation_message_role (role),
    KEY idx_conversation_message_created_at (created_at),
    CONSTRAINT fk_conversation_message_session FOREIGN KEY (session_id) REFERENCES conversation_session (session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息表';

CREATE TABLE IF NOT EXISTS token_usage (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    model_id VARCHAR(64) DEFAULT NULL COMMENT '模型ID',
    model_name VARCHAR(128) DEFAULT NULL COMMENT '模型名称',
    skill_id VARCHAR(64) DEFAULT NULL COMMENT '技能ID',
    skill_name VARCHAR(64) DEFAULT NULL COMMENT '技能名称',
    prompt_tokens BIGINT DEFAULT 0 COMMENT '输入Token数',
    completion_tokens BIGINT DEFAULT 0 COMMENT '输出Token数',
    total_tokens BIGINT DEFAULT 0 COMMENT '总Token数',
    session_id VARCHAR(64) DEFAULT NULL COMMENT '会话ID',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_token_usage_user_id (user_id),
    KEY idx_token_usage_tenant_id (tenant_id),
    KEY idx_token_usage_model_id (model_id),
    KEY idx_token_usage_skill_id (skill_id),
    KEY idx_token_usage_session_id (session_id),
    KEY idx_token_usage_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Token使用记录表';

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
    username VARCHAR(64) DEFAULT NULL COMMENT '用户名',
    action VARCHAR(64) NOT NULL COMMENT '操作',
    resource_type VARCHAR(64) DEFAULT NULL COMMENT '资源类型',
    resource_id VARCHAR(128) DEFAULT NULL COMMENT '资源ID',
    status VARCHAR(32) DEFAULT NULL COMMENT '状态',
    message VARCHAR(1024) DEFAULT NULL COMMENT '说明',
    client_ip VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_audit_tenant_created (tenant_id, created_at),
    KEY idx_audit_user_created (user_id, created_at),
    KEY idx_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

CREATE TABLE IF NOT EXISTS agent_execution_trace (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trace_id VARCHAR(64) NOT NULL COMMENT '执行轨迹ID',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
    session_id VARCHAR(128) DEFAULT NULL COMMENT '会话ID',
    selected_agent VARCHAR(128) DEFAULT NULL COMMENT '选中的Agent',
    selected_type VARCHAR(32) DEFAULT NULL COMMENT '选中的Agent类型',
    intent VARCHAR(32) DEFAULT NULL COMMENT '意图',
    complexity VARCHAR(32) DEFAULT NULL COMMENT '复杂度',
    success BIT(1) NOT NULL DEFAULT 0 COMMENT '是否成功',
    fallback_used BIT(1) NOT NULL DEFAULT 0 COMMENT '是否使用降级',
    task_count INT NOT NULL DEFAULT 0 COMMENT '任务数量',
    duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    question VARCHAR(1024) DEFAULT NULL COMMENT '用户问题',
    reason VARCHAR(1024) DEFAULT NULL COMMENT '选择原因',
    error VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    plan_json TEXT DEFAULT NULL COMMENT '执行计划JSON',
    task_results_json TEXT DEFAULT NULL COMMENT '任务结果JSON',
    shared_context_json TEXT DEFAULT NULL COMMENT '共享上下文JSON',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_execution_trace_trace_id (trace_id),
    KEY idx_agent_execution_trace_tenant_created (tenant_id, created_at),
    KEY idx_agent_execution_trace_user_created (user_id, created_at),
    KEY idx_agent_execution_trace_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent执行轨迹表';

CREATE TABLE IF NOT EXISTS agent_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    feedback_id VARCHAR(64) NOT NULL COMMENT '反馈ID',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户ID',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
    session_id VARCHAR(128) DEFAULT NULL COMMENT '会话ID',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT '执行轨迹ID',
    rating VARCHAR(16) NOT NULL COMMENT '评分',
    question VARCHAR(1024) DEFAULT NULL COMMENT '问题',
    answer VARCHAR(2048) DEFAULT NULL COMMENT '答案摘要',
    comment VARCHAR(1024) DEFAULT NULL COMMENT '反馈备注',
    created_at DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    updated_at DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_feedback_feedback_id (feedback_id),
    UNIQUE KEY uk_agent_feedback_trace_user (trace_id, tenant_id, user_id),
    KEY idx_agent_feedback_tenant_created (tenant_id, created_at),
    KEY idx_agent_feedback_trace_user (trace_id, tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent回答质量反馈表';

-- ============================================================
-- 5. 持久化 Agent Run、人工审批与隔离沙箱
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_run_state (
    run_id VARCHAR(64) NOT NULL COMMENT 'Agent Run ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    user_id VARCHAR(64) NOT NULL COMMENT '发起用户ID',
    session_id VARCHAR(128) DEFAULT NULL COMMENT '会话ID',
    agent_id VARCHAR(128) DEFAULT NULL COMMENT 'Agent ID',
    mode VARCHAR(32) NOT NULL COMMENT '执行模式',
    status VARCHAR(32) NOT NULL COMMENT 'Run状态',
    termination_reason VARCHAR(64) NOT NULL DEFAULT 'NONE' COMMENT '稳定终止原因',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    checkpoint_ciphertext LONGTEXT DEFAULT NULL COMMENT 'AES-GCM加密Checkpoint',
    checkpoint_schema_version INT DEFAULT NULL COMMENT 'Checkpoint合同版本',
    timeout_ms BIGINT NOT NULL COMMENT '初始active timeout',
    max_iterations INT NOT NULL COMMENT '最大迭代数',
    max_model_calls INT NOT NULL COMMENT '最大模型调用数',
    max_tool_calls INT NOT NULL COMMENT '最大工具调用数',
    max_tokens BIGINT NOT NULL COMMENT '最大Token数',
    used_iterations INT NOT NULL DEFAULT 0 COMMENT '已用迭代数',
    used_model_calls INT NOT NULL DEFAULT 0 COMMENT '已用模型调用数',
    used_tool_calls INT NOT NULL DEFAULT 0 COMMENT '已用工具调用数',
    used_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '已用Token数',
    token_usage_estimated BIT(1) NOT NULL DEFAULT 0 COMMENT 'Token是否包含估算',
    remaining_active_timeout_ms BIGINT NOT NULL COMMENT '剩余active execution时间',
    resume_attempts INT NOT NULL DEFAULT 0 COMMENT '恢复尝试数',
    lease_owner VARCHAR(128) DEFAULT NULL COMMENT '恢复租约持有节点',
    lease_until DATETIME(6) DEFAULT NULL COMMENT '恢复租约截止时间',
    approval_id VARCHAR(64) DEFAULT NULL COMMENT '当前审批ID',
    approval_expires_at DATETIME(6) DEFAULT NULL COMMENT '当前审批过期时间',
    result_summary TEXT DEFAULT NULL COMMENT '安全结果',
    error_summary VARCHAR(1024) DEFAULT NULL COMMENT '安全错误摘要',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    completed_at DATETIME(6) DEFAULT NULL COMMENT '终态时间',
    PRIMARY KEY (run_id),
    KEY idx_agent_run_tenant_owner_created (tenant_id, user_id, created_at),
    KEY idx_agent_run_status_lease (status, lease_until),
    KEY idx_agent_run_approval (approval_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='持久化Agent Run状态';

CREATE TABLE IF NOT EXISTS agent_tool_approval (
    approval_id VARCHAR(64) NOT NULL COMMENT '审批ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    run_id VARCHAR(64) NOT NULL COMMENT 'Agent Run ID',
    tool_call_id VARCHAR(128) NOT NULL COMMENT '稳定工具调用ID',
    tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
    risk_level VARCHAR(16) NOT NULL COMMENT '风险等级',
    requester_user_id VARCHAR(64) NOT NULL COMMENT '发起用户ID',
    approval_permission VARCHAR(128) NOT NULL COMMENT '审批所需权限',
    safe_argument_summary VARCHAR(2048) NOT NULL COMMENT '脱敏参数摘要',
    request_ciphertext LONGTEXT NOT NULL COMMENT 'AES-GCM加密原始请求',
    decision_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '不可变审批决定',
    execution_status VARCHAR(32) NOT NULL DEFAULT 'WAITING_DECISION' COMMENT '动作执行状态',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    reviewer_user_id VARCHAR(64) DEFAULT NULL COMMENT '审批用户ID',
    decision_comment VARCHAR(512) DEFAULT NULL COMMENT '审批备注',
    requested_at DATETIME(6) NOT NULL COMMENT '申请时间',
    expires_at DATETIME(6) NOT NULL COMMENT '审批过期时间',
    decided_at DATETIME(6) DEFAULT NULL COMMENT '决策时间',
    execution_started_at DATETIME(6) DEFAULT NULL COMMENT '执行开始时间',
    execution_completed_at DATETIME(6) DEFAULT NULL COMMENT '执行结束时间',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (approval_id),
    UNIQUE KEY uk_agent_approval_run_tool_call (tenant_id, run_id, tool_call_id),
    KEY idx_agent_approval_tenant_status_created (tenant_id, decision_status, requested_at),
    KEY idx_agent_approval_pending_expiry (decision_status, expires_at),
    KEY idx_agent_approval_run (tenant_id, run_id),
    CONSTRAINT fk_agent_approval_run FOREIGN KEY (run_id) REFERENCES agent_run_state (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent工具人工审批';

CREATE TABLE IF NOT EXISTS hotel_rate_sandbox (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '沙箱流水ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    hotel_id VARCHAR(64) NOT NULL COMMENT '演示酒店ID',
    room_type VARCHAR(64) NOT NULL COMMENT '演示房型',
    stay_date DATE NOT NULL COMMENT '入住日期',
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    before_price DECIMAL(12,2) NOT NULL COMMENT '变更前价格',
    after_price DECIMAL(12,2) NOT NULL COMMENT '变更后价格',
    action_key VARCHAR(255) NOT NULL COMMENT '稳定业务幂等键',
    approval_id VARCHAR(64) DEFAULT NULL COMMENT '审批ID',
    tool_call_id VARCHAR(128) DEFAULT NULL COMMENT '工具调用ID',
    demo BIT(1) NOT NULL DEFAULT 1 COMMENT '固定为隔离演示数据',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hotel_rate_sandbox_action (tenant_id, action_key),
    KEY idx_hotel_rate_sandbox_current (tenant_id, hotel_id, room_type, stay_date, created_at),
    KEY idx_hotel_rate_sandbox_approval (tenant_id, approval_id, tool_call_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='隔离酒店价格演示流水';

-- ============================================================
-- 6. 默认数据
-- ============================================================
INSERT INTO sys_permission (permission_code, name, description, resource_type, action, enabled, created_at, updated_at)
VALUES
    ('app:use', '应用使用', '登录后使用普通功能', 'APP', 'USE', 1, NOW(6), NOW(6)),
    ('agent:approval:review', 'Agent动作审批', '审核需要人工确认的Agent工具动作',
        'AGENT_APPROVAL', 'REVIEW', 1, NOW(6), NOW(6)),
    ('*:*', '全部权限', '系统管理员拥有全部管理权限', 'SYSTEM', '*', 1, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    resource_type = VALUES(resource_type),
    action = VALUES(action),
    enabled = VALUES(enabled),
    updated_at = NOW(6);

INSERT INTO sys_role (role_code, name, description, system_role, enabled, created_at, updated_at)
VALUES
    ('USER', '普通用户', '可使用对话、资料、知识库等基础功能', 1, 1, NOW(6), NOW(6)),
    ('ADMIN', '系统管理员', '可管理用户、角色、模型、技能、Agent、数据源和审计', 1, 1, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    system_role = VALUES(system_role),
    enabled = VALUES(enabled),
    updated_at = NOW(6);

INSERT IGNORE INTO sys_role_permission (role_code, permission_code)
VALUES
    ('USER', 'app:use'),
    ('ADMIN', 'app:use'),
    ('ADMIN', 'agent:approval:review'),
    ('ADMIN', '*:*');

INSERT INTO skill_config (
    skill_id, name, description, version, api_url, api_method, prompt_template, keywords,
    enabled, is_default, tenant_id, created_by, created_at, updated_at
)
VALUES (
    'default-data-analysis',
    '通用数据分析',
    '默认数据分析技能，支持通用数据查询、统计分析和趋势预测',
    '1.0',
    '',
    'POST',
    '你是数据分析助手。基于{{data}}回答{{query}}。规则:1.有数据时分析数据 2.无数据时提示用户上传 3.绝不编造数据 4.简洁专业',
    '分析,数据,统计,查询,报表,趋势,预测,对比,汇总',
    1,
    1,
    'default',
    NULL,
    NOW(6),
    NOW(6)
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    prompt_template = VALUES(prompt_template),
    keywords = VALUES(keywords),
    enabled = VALUES(enabled),
    is_default = VALUES(is_default),
    updated_at = NOW(6);
