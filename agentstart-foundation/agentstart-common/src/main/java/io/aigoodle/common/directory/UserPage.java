package io.aigoodle.common.directory;

import java.util.List;

/** 人员目录分页结果。 */
public record UserPage(List<UserInfo> items, long total, int page, int size) {

    public UserPage {
        items = items == null ? List.of() : List.copyOf(items);
        if (total < 0) throw new IllegalArgumentException("total 不能小于 0");
        if (page < 1) throw new IllegalArgumentException("page 必须大于等于 1");
        if (size < 1) throw new IllegalArgumentException("size 必须大于等于 1");
    }

    public static UserPage empty(UserQuery query) {
        return new UserPage(List.of(), 0, query.page(), query.size());
    }
}
