package com.ai.mcp;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MCPContext {

    private final String contextId;
    private final String skillId;
    private final String modelType;
    private final Date createTime;
    private Date lastAccessTime;
    private final Map<String, Object> metadata;

    public MCPContext(String contextId, String skillId, String modelType) {
        this.contextId = contextId;
        this.skillId = skillId;
        this.modelType = modelType;
        this.createTime = new Date();
        this.lastAccessTime = new Date();
        this.metadata = new ConcurrentHashMap<>();
    }

    public String getContextId() {
        return contextId;
    }

    public String getSkillId() {
        return skillId;
    }

    public String getModelType() {
        return modelType;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public Date getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(Date lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "MCPContext{" +
                "contextId='" + contextId + '\'' +
                ", skillId='" + skillId + '\'' +
                ", modelType='" + modelType + '\'' +
                ", createTime=" + createTime +
                ", lastAccessTime=" + lastAccessTime +
                ", metadata=" + metadata +
                '}';
    }
}
