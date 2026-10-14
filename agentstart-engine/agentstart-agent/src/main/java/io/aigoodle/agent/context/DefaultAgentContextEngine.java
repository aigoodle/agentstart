package io.aigoodle.agent.context;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentMessage;
import io.aigoodle.agent.config.AgentProperties;
import io.aigoodle.memory.MemoryItem;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.memory.MemoryQuery;
import io.aigoodle.memory.MemoryRole;
import io.aigoodle.memory.MemoryTier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Budget-aware context assembler: relevant durable facts followed by recent dialogue. */
public final class DefaultAgentContextEngine implements AgentContextEngine {

    private final MemoryManager memory;
    private final AgentProperties properties;
    private final AgentContextCompactor compactor;

    public DefaultAgentContextEngine(MemoryManager memory, AgentProperties properties) {
        this(memory, properties, new ExtractiveAgentContextCompactor());
    }

    public DefaultAgentContextEngine(
            MemoryManager memory, AgentProperties properties, AgentContextCompactor compactor) {
        this.memory = memory;
        this.properties = properties;
        this.compactor = compactor;
    }

    @Override
    public AgentContextWindow assemble(AgentContextRequest request) {
        AgentDefinition definition = request.definition();
        if (definition == null || !definition.isMemoryEnabled()) {
            return new AgentContextWindow(List.of(), 0, 0);
        }
        int budget = request.maxCharacters() > 0
                ? request.maxCharacters()
                : Math.max(1, properties.getMaxContextCharacters());
        int window = definition.getMemoryWindow();
        int durableBudget =
                (int) Math.max(0, Math.min(budget, budget * clamp(properties.getLongTermContextShare(), 0, 0.8)));

        List<AgentMessage> durable = memory
                .recall(new MemoryQuery(
                        definition.getTenantId(),
                        request.memoryOwnerId(),
                        null,
                        request.query(),
                        Set.of(MemoryTier.LONG_TERM),
                        Math.max(1, window / 3)))
                .stream()
                .map(item -> AgentMessage.system("[Long-term memory] " + item.content()))
                .toList();
        List<AgentMessage> history =
                memory
                        .history(definition.getTenantId(), request.memoryOwnerId(), request.conversationId(), window)
                        .stream()
                        .map(DefaultAgentContextEngine::toMessage)
                        .toList();

        List<AgentMessage> selectedDurable = takeWithin(durable, durableBudget, false, new HashSet<>());
        Set<String> seen = new HashSet<>();
        selectedDurable.forEach(message -> seen.add(normalize(message.content())));
        int remaining = budget - characters(selectedDurable);
        int summaryBudget =
                history.stream().mapToInt(DefaultAgentContextEngine::cost).sum() > remaining && remaining >= 200
                        ? Math.min(Math.max(0, properties.getContextSummaryCharacters()), remaining / 4)
                        : 0;
        List<AgentMessage> selectedHistory = takeWithin(history, remaining - summaryBudget, true, seen);
        List<AgentMessage> omitted = new ArrayList<>(history);
        omitted.removeAll(selectedHistory);
        AgentMessage summary = compactor == null
                ? null
                : compactor.compact(omitted, Math.max(0, summaryBudget - 12)).orElse(null);
        List<AgentMessage> result = new ArrayList<>(selectedDurable.size() + selectedHistory.size() + 1);
        result.addAll(selectedDurable);
        if (summary != null) result.add(summary);
        result.addAll(selectedHistory);
        return new AgentContextWindow(result, characters(result), omitted.size());
    }

    private static List<AgentMessage> takeWithin(
            List<AgentMessage> source, int budget, boolean preferNewest, Set<String> seen) {
        List<AgentMessage> selected = new ArrayList<>();
        int used = 0;
        for (int offset = 0; offset < source.size(); offset++) {
            int index = preferNewest ? source.size() - 1 - offset : offset;
            AgentMessage message = source.get(index);
            String key = normalize(message.content());
            int cost = cost(message);
            if (key.isEmpty() || !seen.add(key) || cost > budget - used) continue;
            selected.add(message);
            used += cost;
        }
        if (preferNewest) java.util.Collections.reverse(selected);
        return selected;
    }

    private static int characters(List<AgentMessage> messages) {
        return messages.stream().mapToInt(DefaultAgentContextEngine::cost).sum();
    }

    private static int cost(AgentMessage message) {
        return (message.content() == null ? 0 : message.content().length()) + 12;
    }

    private static String normalize(String content) {
        return content == null ? "" : content.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static AgentMessage toMessage(MemoryItem item) {
        return new AgentMessage(toRole(item.role()), item.content());
    }

    private static AgentMessage.Role toRole(MemoryRole role) {
        if (role == null) return AgentMessage.Role.USER;
        try {
            return AgentMessage.Role.valueOf(role.name());
        } catch (IllegalArgumentException ignored) {
            return AgentMessage.Role.SYSTEM;
        }
    }
}
