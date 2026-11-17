package com.group.exam.common.core;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一分页响应结构（API 文档 2.6）
 */
@Data
public class PageResult<T> implements Serializable {

    private long total;
    private List<T> list;
    private long pageNum;
    private long pageSize;

    public PageResult() {
    }

    public PageResult(long total, List<T> list, long pageNum, long pageSize) {
        this.total = total;
        this.list = list;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    public static <T> PageResult<T> of(long total, List<T> list, long pageNum, long pageSize) {
        return new PageResult<>(total, list, pageNum, pageSize);
    }

    public <R> PageResult<R> map(Function<T, R> fn) {
        List<R> mapped = list == null ? List.of() : list.stream().map(fn).collect(Collectors.toList());
        return new PageResult<>(total, mapped, pageNum, pageSize);
    }
}
