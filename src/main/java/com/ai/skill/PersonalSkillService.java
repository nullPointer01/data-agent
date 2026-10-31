package com.ai.skill;

import com.ai.api.ListPageResponse;
import com.ai.api.ListPaging;
import com.ai.model.SkillConfig;
import com.ai.repository.SkillConfigRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.skill.dto.SkillRequest;
import com.ai.skill.dto.SkillResponse;
import com.ai.agent.tool.governance.AgentToolRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 本人候选的确认、拒绝和分页；不提供普通用户修改租户共享技能的入口。 */
@Service
public class PersonalSkillService {
    private final SkillConfigRepository skills;
    private final SkillLearningStore learning;
    private final SecurityContextHelper security;
    private final AgentToolRegistry tools;

    public PersonalSkillService(SkillConfigRepository skills, SkillLearningStore learning,
            SecurityContextHelper security, AgentToolRegistry tools) {
        this.skills = skills; this.learning = learning; this.security = security; this.tools = tools;
    }

    /** 个人候选与已发布技能共用分页列表，默认最多20项。 */
    public ListPageResponse<SkillResponse> list(int page, int size) {
        ListPaging.page(page, size);
        String tenant = security.getCurrentTenantId(), user = security.getCurrentUserId();
        return ListPageResponse.fetch(page, size, skills.countOwned(tenant, user),
                pageable -> skills.findOwnedPage(tenant, user, pageable)).map(SkillResponse::from);
    }

    /** 编辑后的确认是发布授权；技能本身不会自动给任何 Agent 绑定工具。 */
    @Transactional
    public SkillResponse confirm(String id, SkillRequest request) {
        SkillConfig config = ownedForUpdate(id);
        if ("PUBLISHED".equals(config.getReviewStatus())) return SkillResponse.from(config);
        if (!"PENDING".equals(config.getReviewStatus())) throw new IllegalStateException("候选已拒绝");
        learning.requirePublishableSource(config);
        if (request.name() == null || request.name().isBlank() || request.name().length() > 60) {
            throw new IllegalArgumentException("技能名称需为1到60字");
        }
        config.setName(request.name().strip()); config.setDescription(request.description());
        config.setPromptTemplate(request.promptTemplate()); config.setSteps(request.steps());
        config.setToolDependencies(SkillToolDependencies.normalize(request.toolDependencies()));
        for (String name : SkillToolDependencies.parse(config.getToolDependencies())) {
            if (!tools.isEnabled(name) || !tools.isVisibleToTenant(name, config.getTenantId())) {
                throw new IllegalArgumentException("技能依赖的工具当前不存在、停用或租户不可见");
            }
        }
        config.setReviewStatus("PUBLISHED"); config.setEnabled(true);
        String issue = SkillCatalog.configurationIssue(config);
        if (!issue.isEmpty()) throw new IllegalArgumentException(issue);
        skills.save(config);
        return SkillResponse.from(config);
    }

    /** 明确拒绝保留状态供用户查看，不会自动重新生成同一 Run 的候选。 */
    @Transactional
    public SkillResponse reject(String id) {
        SkillConfig config = ownedForUpdate(id);
        if (!"PENDING".equals(config.getReviewStatus())) throw new IllegalStateException("只能拒绝待确认候选");
        config.setReviewStatus("REJECTED"); config.setEnabled(false); skills.save(config);
        return SkillResponse.from(config);
    }

    /** 已发布个人技能编辑、版本和删除入口调用前复核私有归属。 */
    public void requireOwned(String id) {
        skills.findBySkillIdAndTenantId(id, security.getCurrentTenantId())
                .filter(config -> config.isPrivateSkill() && security.getCurrentUserId().equals(config.getCreatedBy()))
                .orElseThrow(() -> new SecurityException("个人技能不存在或无权限"));
    }

    private SkillConfig ownedForUpdate(String id) {
        return skills.findOwnedForUpdate(id, security.getCurrentTenantId(), security.getCurrentUserId())
                .orElseThrow(() -> new SecurityException("个人技能不存在或无权限"));
    }
}
