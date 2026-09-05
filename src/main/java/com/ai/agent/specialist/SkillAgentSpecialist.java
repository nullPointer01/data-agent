package com.ai.agent.specialist;

import com.ai.model.AgentProfile;
import com.ai.model.AnalysisResponse;
import com.ai.service.connector.DataConnectorService;
import com.ai.skill.Skill;
import com.ai.skill.SkillManager;
import org.springframework.stereotype.Component;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.AgentPromptComposer;
import com.ai.agent.AgentType;

/**
 * 技能专家，绑定技能配置执行固定业务流程。
 *
 * <p>根据 profile 中配置的 skillId 查找对应的技能实例，
 * 然后委托技能进行处理。支持注入数据源预览作为额外上下文。</p>
 *
 * @author data-agent
 */
@Component
public class SkillAgentSpecialist implements AgentSpecialist {

    private static final int DATASOURCE_PREVIEW_LIMIT = 50;

    private final SkillManager skillManager;
    private final AgentPromptComposer promptComposer;
    private final DataConnectorService dataConnectorService;

    /**
     * 构造技能专家。
     *
     * @param skillManager 技能管理器
     * @param promptComposer Prompt 组装器
     * @param dataConnectorService 数据源连接服务
     */
    public SkillAgentSpecialist(SkillManager skillManager,
            AgentPromptComposer promptComposer,
            DataConnectorService dataConnectorService) {
        this.skillManager = skillManager;
        this.promptComposer = promptComposer;
        this.dataConnectorService = dataConnectorService;
    }

    @Override
    public AgentType type() {
        return AgentType.SKILL;
    }

    /**
     * 执行技能请求。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从 profile 获取 skillId，查找对应技能</li>
     *   <li>技能未找到时返回错误</li>
     *   <li>如果 profile 配置了数据源，获取数据源预览</li>
     *   <li>调用技能的 processWithContext 方法</li>
     * </ol>
     * </p>
     *
     * @param request 执行请求
     * @return 技能执行结果
     */
    @Override
    public AnalysisResponse execute(AgentExecutionRequest request) {
        AgentProfile profile = request.profile();
        String skillId = profile != null ? profile.getSkillId() : null;

        if (skillId == null || skillId.isBlank()) {
            return AnalysisResponse.fail("Skill Agent 未配置技能");
        }

        // 查找技能
        Skill skill = skillManager.findSkillByName(skillId);
        if (skill == null) {
            return AnalysisResponse.fail("未找到技能: " + skillId);
        }

        String question = request.request().getQuestion();
        String fileContent = request.fileContent();
        String sessionId = request.request().getSessionId();

        // 获取数据源预览（如果配置了数据源）
        String datasourcePreview = null;
        if (profile != null && profile.getDatasourceId() != null
                && !profile.getDatasourceId().isBlank()) {
            try {
                datasourcePreview = dataConnectorService.preview(
                        profile.getDatasourceId(), DATASOURCE_PREVIEW_LIMIT);
            } catch (Exception e) {
                // 数据源预览失败不阻断技能执行
                datasourcePreview = null;
            }
        }

        // 合并文件内容与数据源预览
        String mergedContent = promptComposer.mergeFileContent(fileContent, datasourcePreview);

        // 调用技能处理
        try {
            String result = skill.processWithContext(question, mergedContent, sessionId, null);
            return AnalysisResponse.ok(result);
        } catch (Exception e) {
            return AnalysisResponse.fail("技能执行失败: " + e.getMessage());
        }
    }
}
