package com.civicflow.model;

import java.util.List;

/**
 * Generic paginated query response container.
 */
public class PageResult<T> {
    private final List<T> items;
    private final long totalCount;
    private final int pageNumber;
    private final int pageSize;

    public PageResult(List<T> items, long totalCount, int pageNumber, int pageSize) {
        this.items = items;
        this.totalCount = totalCount;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    public List<T> getItems() { return items; }
    public long getTotalCount() { return totalCount; }
    public int getPageNumber() { return pageNumber; }
    public int getPageSize() { return pageSize; }

    public int getTotalPages() {
        if (pageSize <= 0) return 1;
        return Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
    }
}
