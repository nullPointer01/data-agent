package com.ai.repository;

import com.ai.model.FileMetadata;
import com.ai.model.FileProcessingStatus;
import com.ai.model.KnowledgeEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RagFullTextRepositoryTest {

    @Autowired
    private KnowledgeEntryRepository knowledgeEntryRepository;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Test
    void searchByTenantAndKeywordFindsKnowledgeContent() {
        knowledgeEntryRepository.save(knowledgeEntry("knowledge-1", "tenant-1", "客户流失分析",
                "客户流失主要来自售后响应慢。"));
        knowledgeEntryRepository.save(knowledgeEntry("knowledge-2", "tenant-2", "客户流失分析",
                "客户流失主要来自价格变化。"));

        List<KnowledgeEntry> results = knowledgeEntryRepository.searchByTenantAndKeyword(
                "tenant-1", "%售后%", PageRequest.of(0, 10));

        assertEquals(1, results.size());
        assertEquals("knowledge-1", results.get(0).getKnowledgeId());
    }

    @Test
    void searchByTenantAndKeywordFindsCompletedFilesOnly() {
        fileMetadataRepository.save(file("file-1", "tenant-1", FileProcessingStatus.COMPLETED,
                "退款流程要求 24 小时内响应。"));
        fileMetadataRepository.save(file("file-2", "tenant-1", FileProcessingStatus.FAILED,
                "退款流程要求 48 小时内响应。"));

        List<FileMetadata> results = fileMetadataRepository.searchByTenantAndKeyword(
                "tenant-1", FileProcessingStatus.COMPLETED.name(), "%退款%", PageRequest.of(0, 10));

        assertEquals(1, results.size());
        assertEquals(FileProcessingStatus.COMPLETED, results.get(0).getProcessingStatus());
        assertEquals("退款流程要求 24 小时内响应。", results.get(0).getContent());
    }

    private KnowledgeEntry knowledgeEntry(String knowledgeId, String tenantId, String name, String content) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setKnowledgeId(knowledgeId);
        entry.setTenantId(tenantId);
        entry.setName(name);
        entry.setDescription("测试知识");
        entry.setContent(content);
        return entry;
    }

    private FileMetadata file(String fileId, String tenantId, FileProcessingStatus status, String content) {
        FileMetadata metadata = new FileMetadata();
        metadata.setFileId(fileId);
        metadata.setTenantId(tenantId);
        metadata.setFilename("测试文件.txt");
        metadata.setContent(content);
        metadata.setProcessingStatus(status);
        return metadata;
    }
}
