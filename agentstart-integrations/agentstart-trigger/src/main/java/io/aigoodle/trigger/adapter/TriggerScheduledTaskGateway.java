package io.aigoodle.trigger.adapter;

import io.aigoodle.common.trigger.ScheduledTaskGateway;
import io.aigoodle.trigger.api.TriggerType;
import io.aigoodle.trigger.entity.TriggerEntity;
import io.aigoodle.trigger.service.CreateTriggerRequest;
import io.aigoodle.trigger.service.TriggerService;
import java.util.List;
import java.util.Map;

/** Adapts the trigger module's persistence and scheduler to the shared scheduling contract. */
public class TriggerScheduledTaskGateway implements ScheduledTaskGateway {

    private final TriggerService triggerService;

    public TriggerScheduledTaskGateway(TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @Override
    public List<ScheduledTaskCandidate> listUserTasks(String tenantId, String userId) {
        return triggerService.listUserSchedules(tenantId, userId).stream()
                .map(this::candidate)
                .toList();
    }

    @Override
    public ScheduledTaskResult createTask(CreateScheduledTaskCommand command) {
        TriggerEntity created = triggerService.create(CreateTriggerRequest.builder()
                .tenantId(command.tenantId())
                .userId(command.userId())
                .name(command.name())
                .type(TriggerType.CRON)
                .targetType("workflow")
                .targetId(command.targetWorkflowId())
                .config(command.config())
                .enabled(true)
                .build());
        return new ScheduledTaskResult(
                created.getId(), created.getName(), created.getTargetId(), created.getNextFireAt());
    }

    @Override
    public ScheduledTaskResult updateOwnedTask(UpdateScheduledTaskCommand command) {
        TriggerEntity updated = triggerService.updateOwnedSchedule(
                command.tenantId(),
                command.userId(),
                command.taskId(),
                command.name(),
                command.targetWorkflowId(),
                command.config());
        return new ScheduledTaskResult(
                updated.getId(), updated.getName(), updated.getTargetId(), updated.getNextFireAt());
    }

    @Override
    public List<DeletedScheduledTask> deleteOwnedTasks(String tenantId, String userId, List<String> taskIds) {
        return triggerService.deleteOwnedSchedules(tenantId, userId, taskIds).stream()
                .map(task -> new DeletedScheduledTask(task.getId(), task.getName()))
                .toList();
    }

    @Override
    public List<ScheduledTaskResult> setOwnedTasksEnabled(
            String tenantId, String userId, List<String> taskIds, boolean enabled) {
        return triggerService.setOwnedSchedulesEnabled(tenantId, userId, taskIds, enabled).stream()
                .map(task ->
                        new ScheduledTaskResult(task.getId(), task.getName(), task.getTargetId(), task.getNextFireAt()))
                .toList();
    }

    @Override
    public List<ScheduledTaskRunResult> runOwnedTasksNow(String tenantId, String userId, List<String> taskIds) {
        return triggerService.runOwnedSchedulesNow(tenantId, userId, taskIds).entrySet().stream()
                .map(entry -> new ScheduledTaskRunResult(
                        entry.getKey().getId(), entry.getKey().getName(), entry.getValue()))
                .toList();
    }

    private ScheduledTaskCandidate candidate(TriggerEntity task) {
        Map<String, Object> config = triggerService.config(task);
        return new ScheduledTaskCandidate(
                task.getId(),
                task.getName(),
                text(config.get("scheduleType")),
                text(config.get("runAt")),
                text(config.get("expression")),
                task.getNextFireAt(),
                task.getLastFireAt(),
                Boolean.TRUE.equals(task.getEnabled()),
                task.getTargetId(),
                config.get("data") instanceof Map<?, ?> data ? stringKeyMap(data) : Map.of());
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
