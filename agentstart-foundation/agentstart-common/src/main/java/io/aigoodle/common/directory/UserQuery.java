package io.aigoodle.common.directory;

/** 人员目录分页查询条件。页码从 1 开始；每页最多 200 条。 */
public record UserQuery(String keyword, String status, int page, int size) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

    public UserQuery {
        if (page < 1) throw new IllegalArgumentException("page 必须大于等于 1");
        if (size < 1 || size > MAX_PAGE_SIZE)
            throw new IllegalArgumentException("size 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
    }

    public static UserQuery firstPage(String keyword) {
        return new UserQuery(keyword, null, 1, DEFAULT_PAGE_SIZE);
    }
}
