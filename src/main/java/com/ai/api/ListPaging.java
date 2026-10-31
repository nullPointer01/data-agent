package com.ai.api;

import org.springframework.data.domain.PageRequest;

/** 列表接口统一限制页大小和搜索长度，避免客户端触发无界查询。 */
public final class ListPaging {
    private ListPaging() { }

    /** 校验每页数量，不接受零、负数或超大页。 */
    public static int size(int size) {
        if (size < 1 || size > 100) throw new IllegalArgumentException("每页数量必须在 1 到 100 之间");
        return size;
    }

    /** 普通管理列表使用零起始页码。 */
    public static PageRequest page(int page, int size) {
        if (page < 0 || page > 1_000_000) throw new IllegalArgumentException("页码无效");
        return PageRequest.of(page, size(size));
    }

    /** 搜索值只作为绑定参数，不允许无限长查询。 */
    public static String keyword(String query) {
        String value = query == null ? "" : query.trim();
        if (value.length() > 200) throw new IllegalArgumentException("搜索内容不能超过 200 字");
        return value;
    }
}
