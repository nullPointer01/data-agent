package com.ai.security.rbac;
import com.ai.security.rbac.AdminRoleRequestStatus;
import com.ai.security.rbac.AdminRoleRequest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 管理员角色申请仓储。
 *
 * @author data-agent
 */
public interface AdminRoleRequestRepository extends JpaRepository<AdminRoleRequest, String> {

    /**
     * 查询用户指定状态的最新申请。
     *
     * @param userId 用户编号
     * @param status 申请状态
     * @return 最新申请
     */
    Optional<AdminRoleRequest> findFirstByUserIdAndStatusOrderByCreatedAtDesc(
            String userId, AdminRoleRequestStatus status);

    /**
     * 查询用户全部申请。
     *
     * @param userId 用户编号
     * @return 申请列表
     */
    List<AdminRoleRequest> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * 查询租户下指定状态的申请。
     *
     * @param tenantId 租户编号
     * @param status 申请状态
     * @return 申请列表
     */
    List<AdminRoleRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(
            String tenantId, AdminRoleRequestStatus status);

    /**
     * 查询租户下全部申请。
     *
     * @param tenantId 租户编号
     * @return 申请列表
     */
    List<AdminRoleRequest> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
