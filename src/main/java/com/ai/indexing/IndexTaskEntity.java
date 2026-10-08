package com.ai.indexing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/** 索引 Outbox 的建表映射；状态写入集中在 IndexTaskStore 的条件 SQL，避免无条件覆盖租约。 */
@Entity
@Table(name = "index_task", indexes = @Index(name = "idx_index_task_ready", columnList = "status,next_attempt_at"))
public class IndexTaskEntity {
    @Id
    @Column(length = 64)
    private String taskId;
    @Column(nullable = false, length = 24)
    private String kind;
    @Column(nullable = false, length = 100)
    private String tenantId;
    @Column(nullable = false, length = 100)
    private String userId;
    @Column(nullable = false, length = 128)
    private String sourceId;
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;
    @Column(nullable = false)
    private long desiredVersion;
    @Column(nullable = false)
    private long completedVersion;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private Instant nextAttemptAt;
    @Column(length = 64)
    private String leaseOwner;
    private Instant leaseUntil;
    @Column(length = 100)
    private String lastError;
    @Column(nullable = false)
    private Instant updatedAt;
}
