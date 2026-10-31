package com.ai.model;

import java.util.Date;

/**
 * 租户技能的历史提示词快照。
 *
 * @author data-agent
 */
public class SkillPromptHistory {

    private Long id;

    private String skillId;

    private int version;

    private String promptTemplate;

    private String steps;

    private String toolDependencies;

    public String getToolDependencies() { return toolDependencies; }
    public void setToolDependencies(String value) { toolDependencies = value; }

    private String remark;

    private String tenantId;

    private Date createdAt;

    public void onCreate() {
        createdAt = new Date();
    }

    public Long getId() {
        return id;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public String getSteps() {
        return steps;
    }

    public void setSteps(String steps) {
        this.steps = steps;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }
}
