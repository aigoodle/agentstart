package io.aigoodle.common.directory;

/** 宿主租户目录的最小只读视图。ID 是跨模块关联使用的稳定业务主键。 */
public record TenantInfo(String id, String code, String name, String status) {}
