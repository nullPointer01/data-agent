package com.ai.repository;

import com.ai.memory.UserProfileMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户画像仓储。
 *
 * @author data-agent
 */
public interface UserProfileMemoryRepository extends JpaRepository<UserProfileMemory, String> {

    /**
     * 按租户和用户查询画像。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 用户画像
     */
    Optional<UserProfileMemory> findByTenantIdAndUserId(String tenantId, String userId);
}
