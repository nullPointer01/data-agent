package com.ai.api;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Function;

/** 管理列表的统一页码响应；删除末页记录后自动退到最后一个有效页。 */
public record ListPageResponse<T>(boolean success, List<T> items, int page, int size, long total) {
    public static <T> ListPageResponse<T> fetch(int page, int size, long total,
            Function<Pageable, List<T>> query) {
        ListPaging.page(page, size);
        int effectivePage = (int) Math.min(page, Math.max(0L, (total - 1) / size));
        List<T> items = total == 0 ? List.of() : query.apply(ListPaging.page(effectivePage, size));
        return new ListPageResponse<>(true, items, effectivePage, size, total);
    }

    public <R> ListPageResponse<R> map(Function<T, R> mapper) {
        return new ListPageResponse<>(success, items.stream().map(mapper).toList(), page, size, total);
    }
}
