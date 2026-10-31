package com.ai.indexing;

import com.ai.security.SecurityContextHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.HashSet;

/** 维护管理仅列举和提交当前管理员自己的来源；不会自动遍历其他用户或清空集合。 */
@Service
public class IndexRebuildService {
    private final IndexTaskMapper mapper;
    private final IndexTaskStore tasks;
    private final SecurityContextHelper security;
    private final RagIndexMaintenance maintenance;

    public IndexRebuildService(IndexTaskMapper mapper, IndexTaskStore tasks, SecurityContextHelper security,
            RagIndexMaintenance maintenance) {
        this.mapper = mapper;
        this.tasks = tasks;
        this.security = security;
        this.maintenance = maintenance;
    }

    /** 按主键游标列出本人来源，长期记忆只包含当前仍有效的条目。 */
    @Transactional(readOnly = true)
    public Page<Source> sources(IndexTask.Kind kind, String after, int limit) {
        var scope = scope();
        validateKind(kind);
        validatePage(after, limit);
        List<Source> rows = mapper.sources(kind, scope.tenant, scope.user, after, limit + 1);
        return page(rows, limit, Source::sourceId);
    }

    /** 分页返回版本、执行状态和错误类型，不暴露来源正文或加密任务载荷。 */
    @Transactional(readOnly = true)
    public Page<TaskView> taskPage(IndexTask.Kind kind, String after, int limit) {
        var scope = scope();
        validateKind(kind);
        validatePage(after, limit);
        List<TaskView> rows = mapper.taskPage(kind, scope.tenant, scope.user, after, limit + 1).stream()
                .map(IndexTaskMapper.StateRow::view).toList();
        return page(rows, limit, TaskView::taskId);
    }

    /** 全批先验证并锁定来源，任一越权/无效项导致事务回滚，不能部分提交。 */
    @Transactional(rollbackFor = Exception.class)
    public List<String> rebuild(IndexTask.Kind kind, List<String> sourceIds) {
        maintenance.requireEnabled();
        var scope = scope();
        validateKind(kind);
        if (sourceIds == null || sourceIds.isEmpty() || sourceIds.size() > 50
                || sourceIds.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 128)
                || new HashSet<>(sourceIds).size() != sourceIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每批需要 1–50 个不重复的合法来源 ID");
        }
        for (String id : sourceIds) {
            var visible = mapper.lockSource(kind, scope.tenant, scope.user, id);
            if (visible.size() != 1) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "来源不存在或当前用户无权重建");
            }
        }
        sourceIds.forEach(id -> tasks.enqueue(kind, scope.tenant, scope.user, id));
        return List.copyOf(sourceIds);
    }

    /** 维护期间重试本人失败任务，运行中或已经成功的任务不重置。 */
    @Transactional
    public boolean retry(IndexTask.Kind kind, String sourceId) {
        maintenance.requireEnabled();
        validateKind(kind);
        var scope = scope();
        return tasks.retry(kind, scope.tenant, scope.user, sourceId);
    }

    private Scope scope() {
        String tenant = security.getCurrentTenantId(), user = security.getCurrentUserId();
        if (tenant == null || tenant.isBlank() || user == null || user.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "索引管理需要完整登录身份");
        }
        return new Scope(tenant, user);
    }

    private static void validateKind(IndexTask.Kind kind) {
        if (kind == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "本入口只重建知识、文件和长期记忆");
        }
    }

    private static void validatePage(String after, int limit) {
        if (after == null || after.length() > 128 || limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页 limit 必须为 1–50，游标不得超过 128 字符");
        }
    }

    private static <T> Page<T> page(List<T> rows, int limit, java.util.function.Function<T, String> id) {
        boolean more = rows.size() > limit;
        var items = List.copyOf(rows.subList(0, Math.min(rows.size(), limit)));
        return new Page<>(items, more, more ? id.apply(items.get(items.size() - 1)) : null);
    }

    private record Scope(String tenant, String user) { }
    public record Source(String sourceId, String title) { }
    public record TaskView(String taskId, String sourceId, IndexTask.Status status, String errorType) { }
    public record Page<T>(List<T> items, boolean hasMore, String nextCursor) { }
}
