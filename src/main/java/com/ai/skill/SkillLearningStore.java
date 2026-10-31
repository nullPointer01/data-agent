package com.ai.skill;

import com.ai.conversation.ConversationAccessGuard;
import com.ai.model.SkillConfig;
import com.ai.repository.SkillConfigRepository;
import com.ai.agent.durable.AgentRunStateRepository;
import com.ai.agent.runtime.AgentRunStatus;
import com.ai.security.SecurityContextHelper;
import com.ai.skill.dto.SkillResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 候选写入的短事务边界；模型调用在事务外，按用户与来源 Run 去重。 */
@Service
public class SkillLearningStore {
    private final SkillConfigRepository skills;
    private final AgentRunStateRepository runs;
    private final ConversationAccessGuard conversations;
    private final SecurityContextHelper security;

    public SkillLearningStore(SkillConfigRepository skills, AgentRunStateRepository runs,
            ConversationAccessGuard conversations, SecurityContextHelper security) {
        this.skills = skills;
        this.runs = runs;
        this.conversations = conversations;
        this.security = security;
    }

    /** 写入前重新校验来源归属与会话生命周期，候选始终不启用、不共享。 */
    @Transactional
    public SkillResponse save(String runId, SkillDraftGenerator.Draft draft) {
        String tenant = security.getCurrentTenantId(), user = security.getCurrentUserId();
        var run = runs.findByRunIdAndTenantIdAndUserId(runId, tenant, user)
                .orElseThrow(() -> new SecurityException("来源任务不存在或无权限"));
        if (!conversations.allowContentWrite(run.getSessionId(), tenant, user)) {
            throw new IllegalStateException("来源会话已删除");
        }
        var existing = skills.findByLearningRun(tenant, user, runId);
        if (existing.isPresent()) return SkillResponse.from(existing.get());
        if (!draft.reusable()) throw new IllegalArgumentException("本次内容没有可复用的操作方法");
        if (skills.countPending(tenant, user) >= 50) throw new IllegalStateException("待确认技能已达 50 条，请先处理候选");
        SkillConfig config = new SkillConfig();
        config.setName(draft.name()); config.setDescription(draft.description());
        config.setPromptTemplate(draft.instructions()); config.setSteps(draft.steps());
        config.setToolDependencies(SkillToolDependencies.normalize(String.join("\n", draft.dependencies())));
        config.setTenantId(tenant); config.setCreatedBy(user); config.setPrivateSkill(true);
        config.setEnabled(false); config.setReviewStatus("PENDING"); config.setSource("experience");
        config.setSourceRunId(runId); config.setEvidence(draft.evidence());
        skills.save(config);
        return SkillResponse.from(config);
    }

    /** 发布必须由本人通过页面确认；失败、等待审批或已删除来源都不能发布。 */
    public void requirePublishableSource(SkillConfig skill) {
        var run = runs.findByRunIdAndTenantIdAndUserId(skill.getSourceRunId(),
                        security.getCurrentTenantId(), security.getCurrentUserId())
                .filter(value -> value.getStatus() == AgentRunStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("来源任务尚未成功完成，不能确认技能"));
        if (!conversations.allowContentWrite(run.getSessionId(), run.getTenantId(), run.getUserId())) {
            throw new IllegalStateException("来源会话已删除，不能确认技能");
        }
    }
}
