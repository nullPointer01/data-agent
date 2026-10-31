package com.ai.api;

import java.util.List;
import java.util.function.Function;

/** 游标页只查询 size + 1 条，不为持续增长的日志强制计算全表总数。 */
public record CursorPageResponse<T>(boolean success, List<T> items, int size,
        boolean hasMore, String nextCursor) {
    /** 用额外一条记录判断下一页，游标取实际返回页的末项。 */
    public static <T> CursorPageResponse<T> fromFetched(List<T> fetched, int size,
            Function<T, RecordCursor> cursor) {
        List<T> items = List.copyOf(fetched.subList(0, Math.min(size, fetched.size())));
        boolean more = fetched.size() > size;
        return new CursorPageResponse<>(true, items, size, more,
                more ? cursor.apply(items.get(items.size() - 1)).encode() : null);
    }

    /** 实体转响应时保留原页边界，不能按转换后的显示字段重新推断位置。 */
    public <R> CursorPageResponse<R> map(Function<T, R> mapper) {
        return new CursorPageResponse<>(success, items.stream().map(mapper).toList(), size, hasMore, nextCursor);
    }
}
