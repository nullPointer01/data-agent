package com.ai.indexing;

import com.ai.security.SecurityContextHelper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.HashSet;

/** 维护管理仅列举和提交当前管理员自己的来源；不会自动遍历其他用户或清空集合。 */
@Service
public class IndexRebuildService {
    private final JdbcTemplate jdbc;
    private final IndexTaskStore tasks;
    private final SecurityContextHelper security;
    private final RagIndexMaintenance maintenance;

    public IndexRebuildService(JdbcTemplate jdbc, IndexTaskStore tasks, SecurityContextHelper security,
            RagIndexMaintenance maintenance) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.security = security;
        this.maintenance = maintenance;
    }

    /** 按主键游标列出本人来源，长期记忆只包含当前仍有效的条目。 */
    @Transactional(readOnly = true)
    public Page<Source> sources(IndexTask.Kind kind, String after, int limit) {
        var scope = scope();
        var spec = spec(kind);
        validatePage(after, limit);
        List<Source> rows = jdbc.query("select " + spec.id + "," + spec.title + " from " + spec.table
                        + " where tenant_id=? and " + spec.owner + "=? and " + spec.id + ">?"
                        + spec.condition + " order by " + spec.id + " limit ?",
                (rs, n) -> new Source(rs.getString(1), rs.getString(2)), scope.tenant, scope.user, after, limit + 1);
        return page(rows, limit, Source::sourceId);
    }

    /** 分页返回版本、执行状态和错误类型，不暴露来源正文或加密任务载荷。 */
    @Transactional(readOnly = true)
    public Page<TaskView> taskPage(IndexTask.Kind kind, String after, int limit) {
        var scope = scope();
        spec(kind);
        validatePage(after, limit);
        List<TaskView> rows = jdbc.query("""
                select task_id,source_id,status,desired_version,completed_version,attempts,last_error
                from index_task where kind=? and tenant_id=? and user_id=? and task_id>?
                order by task_id limit ?
                """, (rs, n) -> new TaskView(rs.getString(1), rs.getString(2),
                        new IndexTask.Status(rs.getString(3), rs.getLong(4), rs.getLong(5), rs.getInt(6)), rs.getString(7)),
                kind.name(), scope.tenant, scope.user, after, limit + 1);
        return page(rows, limit, TaskView::taskId);
    }

    /** 全批先验证并锁定来源，任一越权/无效项导致事务回滚，不能部分提交。 */
    @Transactional(rollbackFor = Exception.class)
    public List<String> rebuild(IndexTask.Kind kind, List<String> sourceIds) {
        maintenance.requireEnabled();
        var scope = scope();
        var spec = spec(kind);
        if (sourceIds == null || sourceIds.isEmpty() || sourceIds.size() > 50
                || sourceIds.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 128)
                || new HashSet<>(sourceIds).size() != sourceIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每批需要 1–50 个不重复的合法来源 ID");
        }
        for (String id : sourceIds) {
            var visible = jdbc.queryForList("select " + spec.id + " from " + spec.table
                            + " where " + spec.id + "=? and tenant_id=? and " + spec.owner + "=?"
                            + spec.condition + " for update", String.class, id, scope.tenant, scope.user);
            if (visible.size() != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "来源不存在或当前用户无权重建");
        }
        sourceIds.forEach(id -> tasks.enqueue(kind, scope.tenant, scope.user, id, null));
        return List.copyOf(sourceIds);
    }

    /** 维护期间重试本人失败任务，运行中或已经成功的任务不重置。 */
    @Transactional
    public boolean retry(IndexTask.Kind kind, String sourceId) {
        maintenance.requireEnabled();
        spec(kind);
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

    private static Spec spec(IndexTask.Kind kind) {
        if (kind == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须指定来源类型");
        // 表名/列名只来自服务端枚举，用户输入仅作为 JDBC 参数。
        return switch (kind) {
            case KNOWLEDGE -> new Spec("knowledge_entry", "knowledge_id", "created_by", "name", "");
            case FILE -> new Spec("file_metadata", "file_id", "uploaded_by", "filename", "");
            case MEMORY -> new Spec("memory_entry", "memory_id", "user_id", "type",
                    " and tier='LONG_TERM' and (expires_at is null or expires_at>CURRENT_TIMESTAMP)");
            case CONVERSATION -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "本入口只重建知识、文件和长期记忆");
        };
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
    private record Spec(String table, String id, String owner, String title, String condition) { }
    public record Source(String sourceId, String title) { }
    public record TaskView(String taskId, String sourceId, IndexTask.Status status, String errorType) { }
    public record Page<T>(List<T> items, boolean hasMore, String nextCursor) { }
}
