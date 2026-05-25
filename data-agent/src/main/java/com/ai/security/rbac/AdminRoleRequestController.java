package com.ai.security.rbac;
import com.ai.security.rbac.AdminRoleRequestStatus;
import com.ai.security.rbac.AdminRoleRequestService;

import com.ai.api.ApiResponse;
import com.ai.security.dto.AdminRoleRequestCreateRequest;
import com.ai.security.dto.AdminRoleRequestReviewRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员角色申请接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/admin-role-requests")
public class AdminRoleRequestController {

    private static final String KEY_REQUEST = "request";
    private static final String KEY_REQUESTS = "requests";

    private final AdminRoleRequestService requestService;

    public AdminRoleRequestController(AdminRoleRequestService requestService) {
        this.requestService = requestService;
    }

    /**
     * 普通用户提交管理员角色申请。
     *
     * @param request 申请内容
     * @return 申请记录
     */
    @PostMapping
    public Map<String, Object> submit(@Valid @RequestBody AdminRoleRequestCreateRequest request) {
        Map<String, Object> response = ApiResponse.success();
        response.put(KEY_REQUEST, requestService.submit(request));
        return response;
    }

    /**
     * 查询当前用户自己的申请记录。
     *
     * @return 申请列表
     */
    @GetMapping("/mine")
    public Map<String, Object> listMine() {
        Map<String, Object> response = ApiResponse.success();
        response.put(KEY_REQUESTS, requestService.listMine());
        return response;
    }

    /**
     * 管理员查询本租户待审核或全部申请。
     *
     * @param status 可选状态
     * @return 申请列表
     */
    @GetMapping
    @PreAuthorize("hasRole(T(com.ai.security.SecurityConstants).ROLE_ADMIN)")
    public Map<String, Object> listForReview(
            @RequestParam(value = "status", required = false) AdminRoleRequestStatus status) {
        Map<String, Object> response = ApiResponse.success();
        response.put(KEY_REQUESTS, requestService.listForReview(status));
        return response;
    }

    /**
     * 管理员通过申请。
     *
     * @param requestId 申请编号
     * @param request 审核备注
     * @return 申请记录
     */
    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasRole(T(com.ai.security.SecurityConstants).ROLE_ADMIN)")
    public Map<String, Object> approve(@PathVariable String requestId,
            @Valid @RequestBody AdminRoleRequestReviewRequest request) {
        Map<String, Object> response = ApiResponse.success();
        response.put(KEY_REQUEST, requestService.approve(requestId, request));
        return response;
    }

    /**
     * 管理员拒绝申请。
     *
     * @param requestId 申请编号
     * @param request 审核备注
     * @return 申请记录
     */
    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasRole(T(com.ai.security.SecurityConstants).ROLE_ADMIN)")
    public Map<String, Object> reject(@PathVariable String requestId,
            @Valid @RequestBody AdminRoleRequestReviewRequest request) {
        Map<String, Object> response = ApiResponse.success();
        response.put(KEY_REQUEST, requestService.reject(requestId, request));
        return response;
    }
}
