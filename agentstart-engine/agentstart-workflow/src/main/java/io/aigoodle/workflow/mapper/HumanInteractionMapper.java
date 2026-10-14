package io.aigoodle.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.aigoodle.workflow.entity.HumanInteractionEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface HumanInteractionMapper extends BaseMapper<HumanInteractionEntity> {
    @Select("SELECT app_id FROM goodle_workflows WHERE tenant_id=#{tenantId} AND id=#{workflowId} LIMIT 1")
    String findAppId(@Param("tenantId") String tenantId, @Param("workflowId") String workflowId);

    @Select(
            "SELECT * FROM goodle_human_interactions WHERE tenant_id=#{tenantId} AND run_id=#{runId} AND node_id=#{nodeId}")
    HumanInteractionEntity findForNode(
            @Param("tenantId") String tenantId, @Param("runId") String runId, @Param("nodeId") String nodeId);

    @Select("SELECT * FROM goodle_human_interactions WHERE access_token_hash=#{hash}")
    HumanInteractionEntity findByAccessTokenHash(@Param("hash") String hash);

    @Select(
            "SELECT * FROM goodle_human_interactions WHERE tenant_id=#{tenantId} AND conversation_id=#{conversationId} ORDER BY created_at ASC")
    List<HumanInteractionEntity> findByConversation(
            @Param("tenantId") String tenantId, @Param("conversationId") String conversationId);

    @Select(
            "SELECT * FROM goodle_human_interactions WHERE tenant_id=#{tenantId} AND channel_connection_id=#{connectionId} "
                    + "AND channel_target=#{senderId} AND status='PENDING' AND (expires_at IS NULL OR expires_at > #{now}) "
                    + "ORDER BY created_at DESC")
    List<HumanInteractionEntity> findPendingChannel(
            @Param("tenantId") String tenantId,
            @Param("connectionId") String connectionId,
            @Param("senderId") String senderId,
            @Param("now") LocalDateTime now);

    @Select("SELECT * FROM goodle_human_interactions WHERE tenant_id=#{tenantId} AND short_code=#{shortCode} "
            + "AND channel_connection_id=#{connectionId} AND channel_target=#{senderId} AND status='PENDING' "
            + "AND (expires_at IS NULL OR expires_at > #{now}) LIMIT 1")
    HumanInteractionEntity findPendingByShortCode(
            @Param("tenantId") String tenantId,
            @Param("connectionId") String connectionId,
            @Param("senderId") String senderId,
            @Param("shortCode") String shortCode,
            @Param("now") LocalDateTime now);

    @Update("UPDATE goodle_human_interactions SET status='SUBMITTING', submitted_values_json=#{valuesJson}, "
            + "submitted_text=#{submittedText}, submitted_by=#{submittedBy}, submitted_at=#{now}, updated_at=#{now} "
            + "WHERE id=#{id} AND status='PENDING' AND (expires_at IS NULL OR expires_at > #{now})")
    int beginSubmit(
            @Param("id") String id,
            @Param("valuesJson") String valuesJson,
            @Param("submittedText") String submittedText,
            @Param("submittedBy") String submittedBy,
            @Param("now") LocalDateTime now);

    @Update(
            "UPDATE goodle_human_interactions SET status=#{status}, updated_at=#{now} WHERE id=#{id} AND status='SUBMITTING'")
    int finishSubmit(@Param("id") String id, @Param("status") String status, @Param("now") LocalDateTime now);
}
