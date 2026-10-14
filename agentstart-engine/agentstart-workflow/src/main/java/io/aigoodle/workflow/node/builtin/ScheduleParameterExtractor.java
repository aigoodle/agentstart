package io.aigoodle.workflow.node.builtin;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.node.ExecutionContext;
import io.aigoodle.workflow.variable.VariableResolver;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;

/** Extracts a trigger schedule from conversational context with a configured LLM. */
final class ScheduleParameterExtractor {

    private static final DateTimeFormatter NOW_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String BUILTIN_PROMPT =
            """
            You manage a user's workflow schedules from a natural-language request.
            Return exactly one JSON object. Do not return Markdown, explanations, or extra text.

            Output schema:
            {
              "action": "CREATE" | "UPDATE" | "DELETE" | "PAUSE" | "RESUME" | "LIST" | "RUN_NOW",
              "selection": {"scope": "EXACT" | "MATCHED" | "ALL", "taskIds": ["exact candidate ids"]},
              "confirmed": false,
              "taskName": "short human-readable schedule name",
              "intent": "short description of what the user wants to do",
              "scheduleType": "ONE" | "CRON",
              "runAt": "yyyy-MM-dd HH:mm:ss",
              "expression": "Spring six-field cron expression",
              "data": {},
              "updateTriggerId": "an exact id from the candidate list",
              "deleteTriggerIds": ["an exact id from the candidate list"]
            }

            Field rules:
            - action is required. LIST shows schedules, PAUSE/RESUME changes availability, RUN_NOW starts tasks now,
              CREATE creates a schedule, UPDATE changes it, and DELETE permanently removes it. A similar subject with a changed time usually means UPDATE when
              the user refers to the existing task; do not create a duplicate unless the user asks for another task.
            - intent is required for both actions.
            - For CREATE or UPDATE, taskName is required. Make it concise and distinctive using the subject/action and timing,
              so the user can recognize and delete it later. Do not use a generic name such as "scheduled task".
            - For CREATE or UPDATE, scheduleType is required. Use ONE for a single future execution and CRON for recurrence.
            - For ONE, runAt is required and expression must be omitted. Resolve relative dates such as
              "tomorrow at 8" against the current server date and time below.
            - For CRON, expression is required and runAt must be omitted. The expression has six fields:
              second minute hour day-of-month month day-of-week.
            - data is a JSON object containing the target workflow's business inputs explicitly present in the
              request. Use the target START-node variable names when they are supplied in the additional rules.
              Do not place schedule metadata, userId, or tenantId in data and never invent business data.
            - timeZone is optional. Omit it unless the user explicitly asks for a time zone. When omitted, the
              scheduler uses the server runtime time zone.
            - For LIST, selection is required but taskIds may be empty when scope is ALL. LIST never changes data.
            - For DELETE, PAUSE, RESUME, or RUN_NOW, selection is required. Use scope ALL for words such as
              "all", "every", or "全部"; otherwise use EXACT for one exact task and MATCHED for several matches.
              taskIds must contain every selected exact id from the candidate list. Never invent an id.
            - confirmed is relevant only to DELETE. Set it true only when the user explicitly confirms a previously
              previewed bulk deletion in the conversational input; otherwise set it false. Never infer confirmation.
            - For UPDATE, selection.scope must be EXACT and selection.taskIds must contain exactly one candidate id. Return the
              complete updated schedule. Preserve the candidate's existing data unless the user explicitly changes it.
            - If a management action matches no candidate, or the request is genuinely ambiguous, return an "error" field
              explaining what is missing. Never guess a schedule, date, recurrence, or trigger id.

            Existing schedules owned by this user (the only schedules a management action may select):
            %s

            Current server date/time: %s
            Current server time zone: %s
            %s
            """;

    private final ModelService modelService;

    ScheduleParameterExtractor(ModelService modelService) {
        this.modelService = modelService;
    }

    Map<String, Object> extract(NodeDef node, ExecutionContext context, List<Map<String, Object>> deletionCandidates) {
        String input = input(node, context);
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Schedule trigger context is empty");
        }
        ChatClient client = NodeModelResolver.resolve(node, context, modelService);
        String supplement = supplement(node, context);
        String targetInputs = targetInputRules(node);
        if (!targetInputs.isBlank()) {
            supplement = supplement.isBlank() ? targetInputs : targetInputs + "\n" + supplement;
        }
        String systemPrompt = BUILTIN_PROMPT.formatted(
                JsonUtils.toJson(deletionCandidates == null ? List.of() : deletionCandidates),
                LocalDateTime.now().format(NOW_FORMAT),
                ZoneId.systemDefault().getId(),
                supplement.isBlank() ? "" : "Additional extraction rules:\n" + supplement);
        ChatClient.ChatClientRequestSpec request =
                client.prompt().messages(List.of(new SystemMessage(systemPrompt), new UserMessage(input)));
        ChatOptions options = NodeModelResolver.perNodeOptions(node);
        if (options != null) request = request.options(options.mutate());
        String response = request.call().content();
        Map<String, Object> schedule =
                new LinkedHashMap<>(JsonUtils.parseMap(ScheduleTriggerNodeExecutor.stripCodeFence(response)));
        validate(schedule, deletionCandidates);
        return schedule;
    }

    static String promptForTest(String supplement) {
        return BUILTIN_PROMPT.formatted(
                "[]",
                "2026-08-18 12:00:00",
                "Asia/Shanghai",
                supplement == null || supplement.isBlank() ? "" : "Additional extraction rules:\n" + supplement);
    }

    private static String input(NodeDef node, ExecutionContext context) {
        Object selector = node.get("inputVariableSelector");
        if (selector instanceof List<?> parts && !parts.isEmpty()) {
            String path = parts.stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "." + b)
                    .orElse("");
            Object value = context.getPool().get(path);
            if (value == null) return null;
            return value instanceof String text ? text : JsonUtils.toJson(value);
        }
        String template = node.getString("query");
        return template == null ? null : VariableResolver.render(template, context.getPool());
    }

    private static String supplement(NodeDef node, ExecutionContext context) {
        Object configured = node.get("extractionPrompt");
        if (configured instanceof Map<?, ?> prompt) configured = prompt.get("text");
        return configured == null ? "" : VariableResolver.render(String.valueOf(configured), context.getPool());
    }

    private static String targetInputRules(NodeDef node) {
        Object variables = node.get("targetWorkflowInputs");
        if (!(variables instanceof List<?> list) || list.isEmpty()) return "";
        return "Target workflow START-node input definitions (use these names in data):\n" + JsonUtils.toJson(list);
    }

    static void validate(Map<String, Object> schedule, List<Map<String, Object>> deletionCandidates) {
        String error = text(schedule.get("error"));
        if (error != null) throw new IllegalArgumentException(error);
        String action = text(schedule.get("action"));
        // Compatibility with the previous prompt, whose output always meant CREATE.
        if (action == null && schedule.get("scheduleType") != null) action = "CREATE";
        if (action == null) throw new IllegalArgumentException("LLM output is missing action");
        action = action.toUpperCase();
        schedule.put("action", action);
        String intent = text(schedule.get("intent"));
        if (intent == null) throw new IllegalArgumentException("LLM output is missing intent");
        schedule.put("intent", intent);
        if (Set.of("DELETE", "PAUSE", "RESUME", "RUN_NOW").contains(action)) {
            validateSelection(schedule, deletionCandidates, false, false);
            return;
        }
        if ("LIST".equals(action)) {
            validateSelection(schedule, deletionCandidates, true, false);
            return;
        }
        if ("UPDATE".equals(action)) {
            validateSelection(schedule, deletionCandidates, false, true);
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) ((Map<String, Object>) schedule.get("selection")).get("taskIds");
            if (ids.size() != 1) throw new IllegalArgumentException("UPDATE requires exactly one schedule id");
            schedule.put("updateTriggerId", ids.get(0));
        } else if (!"CREATE".equals(action)) {
            throw new IllegalArgumentException("LLM output contains an unsupported schedule action");
        }
        String taskName = text(schedule.get("taskName"));
        if (taskName == null) taskName = intent;
        if (taskName.length() > 120) taskName = taskName.substring(0, 120);
        schedule.put("taskName", taskName);
        String type = text(schedule.get("scheduleType"));
        if (type == null) throw new IllegalArgumentException("LLM output is missing scheduleType");
        if ("ONE".equalsIgnoreCase(type) || "ONCE".equalsIgnoreCase(type)) {
            schedule.put("scheduleType", "ONE");
            if (text(schedule.get("runAt")) == null) {
                throw new IllegalArgumentException("LLM output is missing runAt for ONE schedule");
            }
            schedule.remove("expression");
        } else if ("CRON".equalsIgnoreCase(type)) {
            schedule.put("scheduleType", "CRON");
            if (text(schedule.get("expression")) == null) {
                throw new IllegalArgumentException("LLM output is missing expression for CRON schedule");
            }
            schedule.remove("runAt");
        } else {
            throw new IllegalArgumentException("LLM output scheduleType must be ONE or CRON");
        }
        Object data = schedule.get("data");
        // Compatibility with responses generated using the previous built-in prompt.
        if (data == null && schedule.get("payload") instanceof Map<?, ?>) {
            data = schedule.remove("payload");
            schedule.put("data", data);
        }
        if (data == null) schedule.put("data", Map.of());
        else if (!(data instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("LLM output data must be a JSON object");
        }
    }

    private static void validateSelection(
            Map<String, Object> decision,
            List<Map<String, Object>> candidates,
            boolean emptyAllowed,
            boolean retainSchedule) {
        Set<String> allowedIds = (candidates == null ? List.<Map<String, Object>>of() : candidates)
                .stream()
                        .map(candidate -> text(candidate.get("id")))
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());
        Object rawSelection = decision.get("selection");
        Map<String, Object> selection = rawSelection instanceof Map<?, ?> map
                ? map.entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> String.valueOf(entry.getKey()), Map.Entry::getValue,
                                (left, right) -> right, LinkedHashMap::new))
                : new LinkedHashMap<>();
        Object rawIds = selection.get("taskIds");
        // Compatibility with the previous extractor contract.
        if (!(rawIds instanceof List<?>)) rawIds = decision.get("deleteTriggerIds");
        if (!(rawIds instanceof List<?>) && text(decision.get("updateTriggerId")) != null) {
            rawIds = List.of(text(decision.get("updateTriggerId")));
        }
        List<?> ids = rawIds instanceof List<?> list ? list : List.of();
        List<String> selected = ids.stream()
                .map(ScheduleParameterExtractor::text)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        String scope = text(selection.get("scope"));
        if (scope == null) scope = selected.size() == 1 ? "EXACT" : "MATCHED";
        scope = scope.toUpperCase();
        if (!Set.of("EXACT", "MATCHED", "ALL").contains(scope)) {
            throw new IllegalArgumentException("LLM output contains an unsupported selection scope");
        }
        if ("ALL".equals(scope) && !emptyAllowed) {
            // Resolve ALL deterministically from the server-owned candidate snapshot.
            selected = (candidates == null ? List.<Map<String, Object>>of() : candidates)
                    .stream()
                            .map(candidate -> text(candidate.get("id")))
                            .filter(java.util.Objects::nonNull)
                            .toList();
        }
        if ((!emptyAllowed && selected.isEmpty()) || !allowedIds.containsAll(selected)) {
            throw new IllegalArgumentException("LLM selected an unknown schedule id");
        }
        selection.put("scope", scope);
        selection.put("taskIds", selected);
        decision.put("selection", selection);
        decision.remove("deleteTriggerIds");
        if (!retainSchedule) {
            decision.remove("scheduleType");
            decision.remove("runAt");
            decision.remove("expression");
            decision.remove("data");
            decision.remove("payload");
        }
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank()
                ? null
                : String.valueOf(value).trim();
    }
}
