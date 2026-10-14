package io.aigoodle.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentMessage;
import io.aigoodle.agent.config.AgentProperties;
import io.aigoodle.memory.MemoryItem;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.memory.MemoryQuery;
import io.aigoodle.memory.MemoryRole;
import io.aigoodle.memory.MemoryTier;
import io.aigoodle.memory.MemoryWrite;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultAgentContextEngineTest {

    @Test
    void combinesRelevantFactsWithChronologicalRecentHistory() {
        MemoryManager memory = memory(
                List.of(item("fact", MemoryTier.LONG_TERM, MemoryRole.FACT, "Likes Java")),
                List.of(
                        item("old", MemoryTier.SHORT_TERM, MemoryRole.USER, "old turn"),
                        item("new", MemoryTier.SHORT_TERM, MemoryRole.ASSISTANT, "new turn")));
        AgentProperties properties = new AgentProperties();
        properties.setMaxContextCharacters(500);
        properties.setLongTermContextShare(.4);
        var engine = new DefaultAgentContextEngine(memory, properties);

        AgentContextWindow result = engine.assemble(new AgentContextRequest(definition(), "c1", "Java", 0));

        assertThat(result.messages())
                .extracting(AgentMessage::content)
                .containsExactly("[Long-term memory] Likes Java", "old turn", "new turn");
        assertThat(result.droppedMessages()).isZero();
    }

    @Test
    void keepsNewestDialogueWithinBudgetAndReportsDrops() {
        MemoryManager memory = memory(
                List.of(),
                List.of(
                        item("1", MemoryTier.SHORT_TERM, MemoryRole.USER, "first message"),
                        item("2", MemoryTier.SHORT_TERM, MemoryRole.ASSISTANT, "latest")));
        var engine = new DefaultAgentContextEngine(memory, new AgentProperties());

        AgentContextWindow result = engine.assemble(new AgentContextRequest(definition(), "c1", "question", 20));

        assertThat(result.messages()).extracting(AgentMessage::content).containsExactly("latest");
        assertThat(result.droppedMessages()).isEqualTo(1);
        assertThat(result.usedCharacters()).isLessThanOrEqualTo(20);
    }

    @Test
    void bypassesMemoryForStatelessAgents() {
        AgentDefinition definition = definition();
        definition.setMemoryEnabled(false);
        var engine = new DefaultAgentContextEngine(memory(List.of(), List.of()), new AgentProperties());

        assertThat(engine.assemble(new AgentContextRequest(definition, "c", "q", 100))
                        .messages())
                .isEmpty();
    }

    @Test
    void usesExplicitTrustedMemoryOwnerInsteadOfSharingByAgentId() {
        MemoryManager memory = mock(MemoryManager.class);
        when(memory.recall(any())).thenReturn(List.of());
        when(memory.history(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of());
        var engine = new DefaultAgentContextEngine(memory, new AgentProperties());

        engine.assemble(new AgentContextRequest(definition(), "employee-1:sender-9", "conversation-1", "hi", 100));

        verify(memory).recall(argThat(query -> "employee-1:sender-9".equals(query.ownerId())));
        verify(memory).history("default", "employee-1:sender-9", "conversation-1", 10);
    }

    @Test
    void compactsOlderDialogueWhenTheBudgetIsLargeEnoughForASummary() {
        String old = "old ".repeat(40);
        String latest = "latest ".repeat(15);
        MemoryManager memory = memory(
                List.of(),
                List.of(
                        item("1", MemoryTier.SHORT_TERM, MemoryRole.USER, old),
                        item("2", MemoryTier.SHORT_TERM, MemoryRole.ASSISTANT, latest)));
        var engine = new DefaultAgentContextEngine(memory, new AgentProperties());

        AgentContextWindow result = engine.assemble(new AgentContextRequest(definition(), "c1", "question", 240));

        assertThat(result.messages())
                .extracting(AgentMessage::content)
                .anyMatch(content -> content.startsWith("[Earlier conversation summary]"))
                .anyMatch(content -> content.startsWith("latest"));
        assertThat(result.usedCharacters()).isLessThanOrEqualTo(240);
        assertThat(result.droppedMessages()).isEqualTo(1);
    }

    private static AgentDefinition definition() {
        return AgentDefinition.builder()
                .id("a1")
                .tenantId("default")
                .memoryEnabled(true)
                .memoryWindow(10)
                .build();
    }

    private static MemoryItem item(String id, MemoryTier tier, MemoryRole role, String content) {
        return new MemoryItem(id, "default", "a1", "c1", tier, role, content, .8, Instant.now(), null, 0, Map.of());
    }

    private static MemoryManager memory(List<MemoryItem> recalled, List<MemoryItem> history) {
        return new MemoryManager() {
            @Override
            public MemoryItem remember(MemoryWrite write) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<MemoryItem> recall(MemoryQuery query) {
                return recalled;
            }

            @Override
            public List<MemoryItem> history(String tenantId, String ownerId, String conversationId, int limit) {
                return history;
            }

            @Override
            public void clearWorkingMemory(String conversationId) {}

            @Override
            public void forgetConversation(String tenantId, String ownerId, String conversationId) {}
        };
    }
}
