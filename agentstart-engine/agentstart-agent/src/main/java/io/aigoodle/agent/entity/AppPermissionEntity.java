package io.aigoodle.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.aigoodle.persistence.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 关联业务系统主体 ID，不复制宿主的用户、角色、部门表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodle_app_permissions")
public class AppPermissionEntity extends BaseEntity {
    private String appId;
    private String subjectType;
    private String subjectId;
    private Boolean includeDescendants;
}
