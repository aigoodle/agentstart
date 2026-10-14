package io.aigoodle.api;

import java.util.List;
import lombok.Data;

/**
 * Page envelope for list endpoints. Simple 1-based page number to match what the
 * bundled ant-design frontend sends by default.
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private int page;
    private int pageSize;

    public static <T> PageResult<T> of(List<T> records, long total, int page, int pageSize) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(records);
        r.setTotal(total);
        r.setPage(page);
        r.setPageSize(pageSize);
        return r;
    }
}
