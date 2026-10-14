package io.aigoodle.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.entity.AppEntity;
import io.aigoodle.agent.runtime.AgentRuntime;
import io.aigoodle.agent.runtime.AgentRuntimeRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentExecutionServiceTest {

    @Test
    void productionExecutionUsesImmutablePublishedDefinition() {
        AgentService drafts = mock(AgentService.class);
        AgentVersionService versions = mock(AgentVersionService.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(runtime, List.of());
        AgentDefinition published = new AgentDefinition();
        published.setId("agent-1");
        published.setInstructions("published");
        AgentResponse expected = AgentResponse.forConversation("conversation-1").complete("ok");
        when(versions.definition("tenant-a", "agent-1", null)).thenReturn(published);
        when(runtime.run(eq(published), any(), isNull(), isNull())).thenReturn(expected);

        AgentResponse actual = new AgentExecutionService(drafts, versions, registry)
                .runPublished(
                        "tenant-a",
                        "agent-1",
                        null,
                        AgentRequest.builder().query("hello").build(),
                        null,
                        null);

        assertThat(actual).isSameAs(expected);
        verify(drafts).require("tenant-a", "agent-1");
    }

    @Test
    void draftPreviewLoadsTheDraftThroughTenantScopedLookup() {
        AgentService drafts = mock(AgentService.class);
        AgentVersionService versions = mock(AgentVersionService.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(runtime, List.of());
        AppEntity draft = new AppEntity();
        draft.setId("agent-1");
        draft.setTenantId("tenant-a");
        AgentDefinition definition = new AgentDefinition();
        when(drafts.require("tenant-a", "agent-1")).thenReturn(draft);
        when(drafts.toDefinition(draft)).thenReturn(definition);
        when(runtime.run(eq(definition), any(), isNull(), isNull()))
                .thenReturn(AgentResponse.forConversation("conversation-1").complete("preview"));

        new AgentExecutionService(drafts, versions, registry)
                .previewDraft(
                        "tenant-a",
                        "agent-1",
                        AgentRequest.builder().query("hello").build(),
                        null,
                        null);

        verify(drafts).require("tenant-a", "agent-1");
        verifyNoInteractions(versions);
    }
}
