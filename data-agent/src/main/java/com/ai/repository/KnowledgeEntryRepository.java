package com.ai.repository;

import com.ai.model.KnowledgeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntry, String> {
    List<KnowledgeEntry> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
