package com.ai.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 用户长期画像快照。
 *
 * @author data-agent
 */
@Entity
@Table(name = "user_profile")
public class UserProfileMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "profile_id", length = 64)
    private String profileId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(length = 100)
    private String role;

    @Column(length = 100)
    private String company;

    @Column(length = 100)
    private String industry;

    @Column(name = "communication_style", length = 32)
    private String communicationStyle;

    @Column(name = "preferred_format", length = 32)
    private String preferredFormat;

    @Column(name = "expertise_areas_json", columnDefinition = "TEXT")
    private String expertiseAreasJson;

    @Column(name = "frequently_asked_topics_json", columnDefinition = "TEXT")
    private String frequentlyAskedTopicsJson;

    @Column(name = "data_sources_json", columnDefinition = "TEXT")
    private String dataSourcesJson;

    @Column(name = "confidence")
    private double confidence;

    @Column(name = "evidence_count")
    private int evidenceCount;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        lastActiveAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
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
