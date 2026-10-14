package io.aigoodle.workflow.node.builtin;

import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.common.trigger.ScheduledTaskGateway;
import io.aigoodle.common.trigger.ScheduledTaskGateway.CreateScheduledTaskCommand;
import io.aigoodle.common.trigger.ScheduledTaskGateway.DeletedScheduledTask;
import io.aigoodle.common.trigger.ScheduledTaskGateway.ScheduledTaskCandidate;
import io.aigoodle.common.trigger.ScheduledTaskGateway.ScheduledTaskResult;
import io.aigoodle.common.trigger.ScheduledTaskGateway.ScheduledTaskRunResult;
import io.aigoodle.common.trigger.ScheduledTaskGateway.UpdateScheduledTaskCommand;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.variable.VariableResolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;

/** Workflow node that manages schedules through an optional shared scheduling capability. */
public class ScheduleTriggerNodeExecutor implements NodeExecutor {

    private static final Pattern EXACT_VARIABLE = Pattern.compile("^\\{\\{#\\s*([a-zA-Z0-9_\\-.]+)\\s*#}}$");

    private final ObjectProvider<ScheduledTaskGateway> gatewayProvider;
    private final ObjectProvider<ModelService> modelServiceProvider;

    public ScheduleTriggerNodeExecutor(
            ObjectProvider<ScheduledTaskGateway> gatewayProvider, ObjectProvider<ModelService> modelServiceProvider) {
        this.gatewayProvider = gatewayProvider;
        this.modelServiceProvider = modelServiceProvider;
    }

    @Override
    public NodeType type() {
        return NodeType.SCHEDULE_TRIGGER;
    }

    @Override
    public NodeResult execute(NodeDef node, ExecutionContext context) {
        try {
            ScheduledTaskGateway gateway = gatewayProvider.getIfAvailable();
            if (gateway == null) {
                throw new IllegalStateException(
                        "Scheduled trigger capability is unavailable; integrate agentstart-trigger");
            }
            String tenantId =
                    context.getTenantId() == null || context.getTenantId().isBlank()
                            ? UserContextHolder.currentTenantId()
                            : context.getTenantId();
            String userId = context.getUserId() == null ? UserContextHolder.currentUserId() : context.getUserId();
            boolean dynamicDecision = hasExtractionConfiguration(node);
            if (dynamicDecision && (userId == null || userId.isBlank())) {
                return rejectedAuthentication(tenantId);
            }
            List<Map<String, Object>> candidates =
                    dynamicDecision ? deletionCandidates(gateway.listUserTasks(tenantId, userId)) : List.of();
            Map<String, Object> schedule = dynamicDecision
                    ? new ScheduleParameterExtractor(modelServiceProvider.getObject())
                            .extract(node, context, candidates)
                    : schedule(node.get("schedule"), context);
            if ("DELETE".equals(schedule.get("action"))) {
                return deleteSchedules(gateway, tenantId, userId, schedule, candidates);
            }
            if ("UPDATE".equals(schedule.get("action"))) {
                return updateSchedule(gateway, tenantId, userId, schedule, candidates);
            }
            if ("LIST".equals(schedule.get("action"))) {
                return listSchedules(tenantId, userId, schedule, candidates);
            }
            if ("PAUSE".equals(schedule.get("action")) || "RESUME".equals(schedule.get("action"))) {
                return setSchedulesEnabled(gateway, tenantId, userId, schedule);
            }
            if ("RUN_NOW".equals(schedule.get("action"))) {
                return runSchedulesNow(gateway, tenantId, userId, schedule);
            }
            String targetWorkflowId = required(node.getString("targetWorkflowId"), "targetWorkflowId");
            Object configuredData = node.get("data");
            if (configuredData == null) configuredData = node.get("payload");
            if (configuredData instanceof Map<?, ?> map) schedule.put("data", stringKeyMap(map));
            Map<String, Object> workflowData = workflowData(schedule);
            Map<String, Object> parsedResult = new LinkedHashMap<>(schedule);
            schedule.putIfAbsent("conversationId", context.getConversationId());
            schedule.put("data", workflowData);
            String taskName = text(schedule.get("taskName"));
            if (taskName == null) taskName = node.getString("name", "Workflow scheduled task");
            ScheduledTaskResult created = gateway.createTask(
                    new CreateScheduledTaskCommand(tenantId, userId, taskName, targetWorkflowId, schedule));
            return NodeResult.empty()
                    .output("success", true)
                    .output("triggerId", created.id())
                    .output("nextFireAt", created.nextFireAt())
                    .output("targetWorkflowId", created.targetWorkflowId())
                    .output("action", "CREATE")
                    .output("taskName", created.name())
                    .output("message", "定时任务创建成功")
                    .output("result", parsedResult)
                    .output("data", workflowData)
                    .output("intent", parsedResult.get("intent"))
                    .output("userId", userId)
                    .output("tenantId", tenantId);
        } catch (RuntimeException exception) {
            return NodeResult.failure(exception.getMessage());
        }
    }

    private static NodeResult deleteSchedules(
            ScheduledTaskGateway gateway,
            String tenantId,
            String userId,
            Map<String, Object> decision,
            List<Map<String, Object>> candidates) {
        List<String> ids = selectedIds(decision);
        if (ids.size() > 1 && !Boolean.TRUE.equals(decision.get("confirmed"))) {
            List<Map<String, Object>> preview = candidates.stream()
                    .filter(candidate -> ids.contains(text(candidate.get("id"))))
                    .toList();
            return NodeResult.empty()
                    .output("success", false)
                    .output("action", "DELETE")
                    .output("status", "PENDING_CONFIRMATION")
                    .output("requiresConfirmation", true)
                    .output("affectedCount", ids.size())
                    .output("affectedTasks", preview)
                    .output("message", "即将删除 " + ids.size() + " 个定时任务，请确认后再执行")
                    .output("result", new LinkedHashMap<>(decision))
                    .output("userId", userId)
                    .output("tenantId", tenantId);
        }
        List<DeletedScheduledTask> deleted = gateway.deleteOwnedTasks(tenantId, userId, ids);
        List<Map<String, Object>> deletedItems = deleted.stream()
                .map(task -> Map.<String, Object>of("id", task.id(), "name", task.name() == null ? "" : task.name()))
                .toList();
        return NodeResult.empty()
                .output("success", true)
                .output("action", "DELETE")
                .output("intent", decision.get("intent"))
                .output("deletedCount", deleted.size())
                .output("deletedTriggers", deletedItems)
                .output("message", "已删除 " + deleted.size() + " 个定时任务")
                .output("result", new LinkedHashMap<>(decision))
                .output("userId", userId)
                .output("tenantId", tenantId);
    }

    private static NodeResult listSchedules(
            String tenantId, String userId, Map<String, Object> decision, List<Map<String, Object>> candidates) {
        List<String> ids = selectedIds(decision);
        List<Map<String, Object>> selected = ids.isEmpty()
                ? candidates
                : candidates.stream()
                        .filter(candidate -> ids.contains(text(candidate.get("id"))))
                        .toList();
        return NodeResult.empty()
                .output("success", true)
                .output("action", "LIST")
                .output("taskCount", selected.size())
                .output("tasks", selected)
                .output("message", "共找到 " + selected.size() + " 个定时任务")
                .output("result", new LinkedHashMap<>(decision))
                .output("userId", userId)
                .output("tenantId", tenantId);
    }

    private static NodeResult setSchedulesEnabled(
            ScheduledTaskGateway gateway, String tenantId, String userId, Map<String, Object> decision) {
        boolean enabled = "RESUME".equals(decision.get("action"));
        List<ScheduledTaskResult> changed =
                gateway.setOwnedTasksEnabled(tenantId, userId, selectedIds(decision), enabled);
        return NodeResult.empty()
                .output("success", true)
                .output("action", decision.get("action"))
                .output("affectedCount", changed.size())
                .output("tasks", changed)
                .output("message", "已" + (enabled ? "恢复 " : "暂停 ") + changed.size() + " 个定时任务")
                .output("result", new LinkedHashMap<>(decision))
                .output("userId", userId)
                .output("tenantId", tenantId);
    }

    private static NodeResult runSchedulesNow(
            ScheduledTaskGateway gateway, String tenantId, String userId, Map<String, Object> decision) {
        List<ScheduledTaskRunResult> runs = gateway.runOwnedTasksNow(tenantId, userId, selectedIds(decision));
        return NodeResult.empty()
                .output("success", true)
                .output("action", "RUN_NOW")
                .output("startedCount", runs.size())
                .output("runs", runs)
                .output("message", "已启动 " + runs.size() + " 个定时任务")
                .output("result", new LinkedHashMap<>(decision))
                .output("userId", userId)
                .output("tenantId", tenantId);
    }

    @SuppressWarnings("unchecked")
    private static List<String> selectedIds(Map<String, Object> decision) {
        Object rawSelection = decision.get("selection");
        if (!(rawSelection instanceof Map<?, ?> selection)) return List.of();
        Object rawIds = selection.get("taskIds");
        return rawIds instanceof List<?> ids
                ? ids.stream()
                        .map(ScheduleTriggerNodeExecutor::text)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList()
                : List.of();
    }

    private static NodeResult updateSchedule(
            ScheduledTaskGateway gateway,
            String tenantId,
            String userId,
            Map<String, Object> schedule,
            List<Map<String, Object>> candidates) {
        String taskId = text(schedule.get("updateTriggerId"));
        Map<String, Object> candidate = candidates.stream()
                .filter(item -> taskId != null && taskId.equals(text(item.get("id"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Schedule selected for update is unavailable"));
        Object data = schedule.get("data");
        if (!(data instanceof Map<?, ?> map) || map.isEmpty()) {
            Object existingData = candidate.get("data");
            schedule.put("data", existingData instanceof Map<?, ?> existing ? stringKeyMap(existing) : Map.of());
        }
        String targetWorkflowId = required(text(candidate.get("targetWorkflowId")), "targetWorkflowId");
        String taskName = text(schedule.get("taskName"));
        if (taskName == null) taskName = text(candidate.get("name"));
        schedule.put("taskName", taskName);
        ScheduledTaskResult updated = gateway.updateOwnedTask(
                new UpdateScheduledTaskCommand(tenantId, userId, taskId, taskName, targetWorkflowId, schedule));
        Map<String, Object> workflowData = workflowData(schedule);
        return NodeResult.empty()
                .output("success", true)
                .output("action", "UPDATE")
                .output("triggerId", updated.id())
                .output("nextFireAt", updated.nextFireAt())
                .output("targetWorkflowId", updated.targetWorkflowId())
                .output("taskName", updated.name())
                .output("intent", schedule.get("intent"))
                .output("data", workflowData)
                .output("message", "定时任务修改成功")
                .output("result", new LinkedHashMap<>(schedule))
                .output("userId", userId)
                .output("tenantId", tenantId);
    }

    private static NodeResult rejectedAuthentication(String tenantId) {
        String message = "定时任务管理需要用户先完成登录认证";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("action", "REJECTED");
        result.put("errorCode", "AUTHENTICATION_REQUIRED");
        result.put("message", message);
        return NodeResult.empty()
                .output("success", false)
                .output("action", "REJECTED")
                .output("errorCode", "AUTHENTICATION_REQUIRED")
                .output("message", message)
                .output("result", result)
                .output("userId", null)
                .output("tenantId", tenantId);
    }

    private static List<Map<String, Object>> deletionCandidates(List<ScheduledTaskCandidate> tasks) {
        return tasks.stream()
                .map(task -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", task.id());
                    item.put("name", task.name());
                    item.put("scheduleType", task.scheduleType());
                    item.put("runAt", task.runAt());
                    item.put("expression", task.expression());
                    item.put("nextFireAt", task.nextFireAt());
                    item.put("lastFireAt", task.lastFireAt());
                    item.put("enabled", task.enabled());
                    item.put("targetWorkflowId", task.targetWorkflowId());
                    item.put("data", task.data());
                    return item;
                })
                .toList();
    }

    private static boolean hasExtractionConfiguration(NodeDef node) {
        Object selector = node.get("inputVariableSelector");
        return selector instanceof List<?> list && !list.isEmpty();
    }

    private static Map<String, Object> schedule(Object value, ExecutionContext context) {
        if (value instanceof Map<?, ?> map) return stringKeyMap(map);
        if (value instanceof String json) {
            Matcher matcher = EXACT_VARIABLE.matcher(json.trim());
            Object resolved = matcher.matches()
                    ? context.getPool().get(matcher.group(1))
                    : VariableResolver.render(json, context.getPool());
            if (resolved instanceof Map<?, ?> map) return stringKeyMap(map);
            return new LinkedHashMap<>(JsonUtils.parseMap(stripCodeFence(String.valueOf(resolved))));
        }
        throw new IllegalArgumentException("schedule must be a JSON object or JSON string");
    }

    static String stripCodeFence(String value) {
        String text = value == null ? "" : value.trim();
        if (!text.startsWith("```")) return text;
        int firstLineEnd = text.indexOf('\n');
        int closingFence = text.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) return text;
        return text.substring(firstLineEnd + 1, closingFence).trim();
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Map<String, Object> workflowData(Map<String, Object> schedule) {
        Object data = schedule.get("data");
        if (data == null) data = schedule.remove("payload");
        return data instanceof Map<?, ?> map ? stringKeyMap(map) : new LinkedHashMap<>();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank()
                ? null
                : String.valueOf(value).trim();
    }
}
