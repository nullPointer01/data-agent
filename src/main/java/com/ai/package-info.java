/**
 * Data Agent 后端源码功能导航。
 *
 * <p><strong>功能边界：</strong></p>
 * <ul>
 *   <li>正式功能必须有当前产品入口、真实执行链和可验收结果。</li>
 *   <li>只有审批沙箱是明确保留的隔离演示：工程流程真实，不写入外部业务系统。</li>
 *   <li>没有产品入口、只返回伪造数据或仅为历史兼容的功能链应删除，不作注释保留。</li>
 * </ul>
 *
 * <p><strong>核心功能包：</strong></p>
 * <ul>
 *   <li>{@code com.ai.agent}：Agent 运行、ReAct 循环、能力绑定、工具治理、审批恢复和子 Agent 委派。</li>
 *   <li>{@code com.ai.rag}、{@code com.ai.vector}、{@code com.ai.knowledge}：知识入库、混合检索、重排、引用和向量索引。</li>
 *   <li>{@code com.ai.memory}、{@code com.ai.conversation}：会话持久化、短期/长期记忆和用户画像。</li>
 *   <li>{@code com.ai.datasource}：外部数据源连接、预览、Schema 查询和只读 SQL 执行。</li>
 *   <li>{@code com.ai.file}、{@code com.ai.service.file}：文件上传、存储、解析和异步处理。</li>
 *   <li>{@code com.ai.skill}：Skill 配置、版本和动态执行。</li>
 *   <li>{@code com.ai.security}：JWT 认证、RBAC、租户边界和用户管理。</li>
 *   <li>{@code com.ai.modelconfig}、{@code com.ai.mcp}：模型配置、客户端构建与调用；{@code mcp} 是历史命名，不等于 MCP 协议实现。</li>
 *   <li>{@code com.ai.controller}：HTTP API 入口；具体业务通常委托给 Service。</li>
 * </ul>
 *
 * <p><strong>演示边界：</strong>
 * {@code com.ai.agent.sandbox} 只验证酒店改价的审批、恢复和幂等流程；
 * 文件入库只接受已实现真实文本提取的类型，不支持的类型直接拒绝。</p>
 */
package com.ai;
