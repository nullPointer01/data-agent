package com.ai.security.rbac;

/**
 * 管理员角色申请状态。
 *
 * @author data-agent
 */
public enum AdminRoleRequestStatus {

    /**
     * 待管理员审核。
     */
    PENDING,

    /**
     * 已通过并授予管理员角色。
     */
    APPROVED,

    /**
     * 已拒绝。
     */
    REJECTED
}
