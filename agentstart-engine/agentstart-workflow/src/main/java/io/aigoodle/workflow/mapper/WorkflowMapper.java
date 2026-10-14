package io.aigoodle.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aigoodle.workflow.entity.WorkflowEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowMapper extends BaseMapper<WorkflowEntity> {
    /** Only the currently published binding of an unrestricted shared application. */
    @org.apache.ibatis.annotations.ResultMap("mybatis-plus_WorkflowEntity")
    @org.apache.ibatis.annotations.Select(
            """
            SELECT w.* FROM goodle_workflows w JOIN goodle_apps a
              ON a.id = w.app_id AND a.tenant_id = w.tenant_id AND a.workflow_id = w.id
            WHERE a.id = #{appId} AND a.tenant_id = #{ownerTenantId}
              AND a.visibility = 'GLOBAL' AND a.published = true
              AND (a.status IS NULL OR a.status <> 'disabled')
              AND (a.data_access_mode IS NULL OR a.data_access_mode = 'ALL')
              AND w.version <> 'draft'
            """)
    WorkflowEntity selectSharedPublished(
            @org.apache.ibatis.annotations.Param("appId") String appId,
            @org.apache.ibatis.annotations.Param("ownerTenantId") String ownerTenantId);
}
