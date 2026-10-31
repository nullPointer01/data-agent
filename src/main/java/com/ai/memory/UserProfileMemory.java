package com.ai.memory;

import java.util.UUID;

import java.time.LocalDateTime;

/**
 * 用户长期画像快照。
 *
 * @author data-agent
 */
public class UserProfileMemory {

    private String profileId;

    private String tenantId;

    private String userId;

    private String displayName;

    private String role;

    private String company;

    private String industry;

    private String communicationStyle;

    private String preferredFormat;

    private String expertiseAreasJson;

    private String frequentlyAskedTopicsJson;

    private String dataSourcesJson;

    private double confidence;

    private int evidenceCount;

    private LocalDateTime lastActiveAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public void onCreate() {
        if (profileId == null) {
            profileId = UUID.randomUUID().toString();
        }
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        lastActiveAt = now;
    }

    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getCommunicationStyle() {
        return communicationStyle;
    }

    public void setCommunicationStyle(String communicationStyle) {
        this.communicationStyle = communicationStyle;
    }

    public String getPreferredFormat() {
        return preferredFormat;
    }

    public void setPreferredFormat(String preferredFormat) {
        this.preferredFormat = preferredFormat;
    }

    public String getExpertiseAreasJson() {
        return expertiseAreasJson;
    }

    public void setExpertiseAreasJson(String expertiseAreasJson) {
        this.expertiseAreasJson = expertiseAreasJson;
    }

    public String getFrequentlyAskedTopicsJson() {
        return frequentlyAskedTopicsJson;
    }

    public void setFrequentlyAskedTopicsJson(String frequentlyAskedTopicsJson) {
        this.frequentlyAskedTopicsJson = frequentlyAskedTopicsJson;
    }

    public String getDataSourcesJson() {
        return dataSourcesJson;
    }

    public void setDataSourcesJson(String dataSourcesJson) {
        this.dataSourcesJson = dataSourcesJson;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(int evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
