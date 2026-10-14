package io.aigoodle.common.trigger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Optional scheduling capability consumed by workflow nodes.
 *
 * <p>The workflow module depends only on this contract. A scheduling module such as
 * {@code agent-start-trigger} supplies the implementation when it is integrated.</p>
 */
public interface ScheduledTaskGateway {

    List<ScheduledTaskCandidate> listUserTasks(String tenantId, String userId);

    ScheduledTaskResult createTask(CreateScheduledTaskCommand command);

    ScheduledTaskResult updateOwnedTask(UpdateScheduledTaskCommand command);

    List<DeletedScheduledTask> deleteOwnedTasks(String tenantId, String userId, List<String> taskIds);

    List<ScheduledTaskResult> setOwnedTasksEnabled(
            String tenantId, String userId, List<String> taskIds, boolean enabled);

    List<ScheduledTaskRunResult> runOwnedTasksNow(String tenantId, String userId, List<String> taskIds);

    record CreateScheduledTaskCommand(
            String tenantId, String userId, String name, String targetWorkflowId, Map<String, Object> config) {}

    record UpdateScheduledTaskCommand(
            String tenantId,
            String userId,
            String taskId,
            String name,
            String targetWorkflowId,
            Map<String, Object> config) {}

    record ScheduledTaskCandidate(
            String id,
            String name,
            String scheduleType,
            String runAt,
            String expression,
            LocalDateTime nextFireAt,
            LocalDateTime lastFireAt,
            boolean enabled,
            String targetWorkflowId,
            Map<String, Object> data) {}

    record ScheduledTaskResult(String id, String name, String targetWorkflowId, LocalDateTime nextFireAt) {}

    record DeletedScheduledTask(String id, String name) {}

    record ScheduledTaskRunResult(String id, String name, String invocationId) {}
}
