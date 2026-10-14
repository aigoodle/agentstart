package io.aigoodle.agent.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.api.AgentStep;
import io.aigoodle.common.exception.PlatformException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class AgentRuntimeRegistryTest {
    @Test
    void selectsExplicitExtensionAndNeverSilentlyFallsBackForUnknownRuntime() {
        AgentRuntime nativeRuntime = mock(AgentRuntime.class);
        AgentRuntimeExtension custom = extension("CUSTOM_RUNTIME", "custom");
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(nativeRuntime, List.of(custom));
        AgentDefinition definition = AgentDefinition.builder()
                .id("agent-1")
                .runtimeType("custom-runtime")
                .runtimeRef("support-agent")
                .build();

        assertThat(registry.run(definition, AgentRequest.of("hello"), null, null)
                        .getText())
                .isEqualTo("custom");
        verifyNoInteractions(nativeRuntime);

        definition.setRuntimeType("missing-runtime");
        assertThatThrownBy(() -> registry.run(definition, AgentRequest.of("hello"), null, null))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("not installed");
        verifyNoInteractions(nativeRuntime);
    }

    @Test
    void blankRuntimeTypeIsBackwardCompatibleWithNativeRuntime() {
        AgentRuntime nativeRuntime = mock(AgentRuntime.class);
        AgentResponse expected = AgentResponse.forConversation("c1").complete("native");
        when(nativeRuntime.run(any(), any(), isNull(), isNull())).thenReturn(expected);
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(nativeRuntime, List.of());

        AgentResponse result =
                registry.run(AgentDefinition.builder().runtimeType(null).build(), AgentRequest.of("hello"), null, null);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void appliesHostInterceptorsInStableOrderToEveryRuntime() {
        AgentRuntime nativeRuntime = mock(AgentRuntime.class);
        AgentRuntimeExtension custom = extension("CUSTOM_RUNTIME", "custom");
        ArrayList<String> calls = new ArrayList<>();
        AgentRuntimeInterceptor later = interceptor(20, "later", calls);
        AgentRuntimeInterceptor earlier = interceptor(-10, "earlier", calls);
        AgentRuntimeRegistry registry =
                new AgentRuntimeRegistry(nativeRuntime, List.of(custom), List.of(later, earlier));

        AgentResponse response = registry.run(
                AgentDefinition.builder().runtimeType("CUSTOM_RUNTIME").build(), AgentRequest.of("hello"), null, null);

        assertThat(response.getText()).isEqualTo("custom");
        assertThat(calls).containsExactly("earlier:before", "later:before", "later:after", "earlier:after");
        verifyNoInteractions(nativeRuntime);
    }

    @Test
    void interceptorCanFailClosedBeforeTheSelectedRuntimeExecutes() {
        AgentRuntime nativeRuntime = mock(AgentRuntime.class);
        AgentRuntimeInterceptor policy = new AgentRuntimeInterceptor() {
            @Override
            public AgentResponse intercept(AgentRuntimeInvocation invocation, Chain chain) {
                throw new PlatformException("quota_exceeded", "Tenant quota exceeded", null);
            }
        };
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(nativeRuntime, List.of(), List.of(policy));

        assertThatThrownBy(() -> registry.run(AgentDefinition.builder().build(), AgentRequest.of("hello")))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("quota");
        verifyNoInteractions(nativeRuntime);
    }

    @Test
    void tenantScopedRunOperationsFailBeforeDelegatingToForeignRuntime() {
        AgentRuntime nativeRuntime = mock(AgentRuntime.class);
        AgentRunSnapshot foreign = new AgentRunSnapshot(
                "run-1",
                "tenant-b",
                "agent-1",
                "conversation-1",
                AgentRunStatus.RUNNING,
                "{}",
                "{}",
                null,
                null,
                1,
                null,
                null,
                null,
                null);
        when(nativeRuntime.findRun("run-1")).thenReturn(Optional.of(foreign));
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(nativeRuntime, List.of());

        assertThat(registry.findRun("tenant-a", "run-1")).isEmpty();
        assertThatThrownBy(() -> registry.runEventsForTenant("tenant-a", "run-1", 0, 20))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> registry.cancelForTenant("tenant-a", "run-1"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("not found");

        verify(nativeRuntime, never()).runEvents(anyString(), anyLong(), anyInt());
        verify(nativeRuntime, never()).cancel(anyString());
    }

    private static AgentRuntimeExtension extension(String type, String answer) {
        return new AgentRuntimeExtension() {
            @Override
            public String runtimeType() {
                return type;
            }

            @Override
            public AgentResponse run(
                    AgentDefinition definition,
                    AgentRequest request,
                    Consumer<AgentStep> steps,
                    Consumer<String> tokens) {
                return AgentResponse.forConversation(request.getConversationId())
                        .complete(answer);
            }
        };
    }

    private static AgentRuntimeInterceptor interceptor(int order, String name, List<String> calls) {
        return new AgentRuntimeInterceptor() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public AgentResponse intercept(AgentRuntimeInvocation invocation, Chain chain) {
                calls.add(name + ":before");
                try {
                    return chain.proceed(invocation);
                } finally {
                    calls.add(name + ":after");
                }
            }
        };
    }
}
