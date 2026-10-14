package io.aigoodle.web.dto.dify;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Dify 列表端点通用响应壳 —— {@code /conversations}、{@code /messages} 等分页接口
 * 统一返回 {@code { limit, has_more, data }} 三字段。字段名保持 snake_case 与
 * Dify 官方响应一致，避免前端做二次适配。
 */
public class DifyPage<T> {

    private int limit;

    @JsonProperty("has_more")
    private boolean hasMore;

    private List<T> data;

    public DifyPage() {}

    public DifyPage(int limit, boolean hasMore, List<T> data) {
        this.limit = limit;
        this.hasMore = hasMore;
        this.data = data;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }
}
