package io.aigoodle.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.agent.entity.AgentVersionEntity;
import io.aigoodle.agent.runtime.AgentRuntimeRegistry;
import io.aigoodle.agent.service.AgentService;
import io.aigoodle.agent.service.AgentVersionService;
import io.aigoodle.channel.ChannelAgentRouter;
import io.aigoodle.channel.ChannelConversationService;
import io.aigoodle.channel.ChannelIdentityService;
import io.aigoodle.channel.ChannelInboundEvent;
import io.aigoodle.channel.ChannelInboundResult;
import io.aigoodle.common.context.PrincipalType;
import io.aigoodle.common.context.UserContextHolder;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChannelAgentInboundHandlerTest {
    @Test
    void dispatcherUsesOneImmutableRoutingDecisionPerInboundMessage() {
        ChannelAgentRouter router = mock(ChannelAgentRouter.class);
        AgentService agents = mock(AgentService.class);
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        ChannelInboundEvent event = new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "account-1",
                "message-route-once",
                "sender-1",
                "conversation-route-once",
                "在吗",
                Instant.now(),
                false,
                Map.of());
        when(router.route(event))
                .thenReturn(new ChannelAgentRouter.Result(
                        "connection-1",
                        "tenant-a",
                        "employee-1",
                        "agent-1",
                        null,
                        ChannelAgentRouter.Source.EMPLOYEE,
                        ChannelAgentRouter.UnboundPolicy.SILENT,
                        true));
        when(agents.run(eq("agent-1"), any()))
                .thenReturn(
                        AgentResponse.forConversation("conversation-route-once").complete("你好"));
        ChannelAgentInboundHandler handler = new ChannelAgentInboundHandler(router, agents, identities);
        io.aigoodle.channel.ChannelInboundDispatcher dispatcher =
                new io.aigoodle.channel.ChannelInboundDispatcher(java.util.List.of(handler));

        ChannelInboundResult result = dispatcher.dispatch(event);

        assertThat(result.reply()).isEqualTo("你好");
        verify(router, times(1)).route(event);
    }

    @Test
    void directHandleOfUnmanagedEventReturnsUnhandledWithoutNullMetadataFailure() {
        ChannelAgentRouter router = mock(ChannelAgentRouter.class);
        ChannelInboundEvent event = new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "missing-account",
                "message-unmanaged",
                "sender-1",
                "conversation-1",
                "hello",
                Instant.now(),
                false,
                Map.of());
        when(router.route(event))
                .thenReturn(new ChannelAgentRouter.Result(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        ChannelAgentRouter.Source.NONE,
                        "connection_not_found",
                        1,
                        ChannelAgentRouter.UnboundPolicy.SILENT,
                        false));
        ChannelAgentInboundHandler handler =
                new ChannelAgentInboundHandler(router, mock(AgentService.class), mock(ChannelIdentityService.class));

        ChannelInboundResult result = handler.handle(event);

        assertThat(result.handled()).isFalse();
        assertThat(result.metadata()).isEmpty();
    }

    @Test
    void humanHandoffPausesAutomaticAgentReply() {
        ChannelAgentRouter router = mock(ChannelAgentRouter.class);
        AgentService agents = mock(AgentService.class);
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        ChannelConversationService conversations = mock(ChannelConversationService.class);
        ChannelInboundEvent event = new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "account-1",
                "message-2",
                "sender-1",
                "conversation-1",
                "人工处理中吗",
                Instant.now(),
                false,
                Map.of());
        when(router.route(event))
                .thenReturn(new ChannelAgentRouter.Result(
                        "connection-1",
                        "tenant-a",
                        "employee-1",
                        "agent-1",
                        null,
                        ChannelAgentRouter.Source.EMPLOYEE,
                        ChannelAgentRouter.UnboundPolicy.SILENT,
                        true));
        when(conversations.agentPaused("tenant-a", "connection-1", "conversation-1"))
                .thenReturn(true);
        ChannelAgentInboundHandler handler =
                new ChannelAgentInboundHandler(router, agents, identities, null, conversations);

        ChannelInboundResult result = handler.handle(event);

        assertThat(result.handled()).isTrue();
        assertThat(result.reply()).isNull();
        assertThat(result.metadata()).containsEntry("agentPaused", true);
        verifyNoInteractions(agents);
    }

    @Test
    void routesAccountToBoundAgentAndKeepsConversationStableWhenSenderIsMissing() {
        ChannelAgentRouter router = mock(ChannelAgentRouter.class);
        AgentService agents = mock(AgentService.class);
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        ChannelInboundEvent event = new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "account-1",
                "message-1",
                null,
                "conversation-1",
                "在吗",
                Instant.now(),
                false,
                Map.of());
        when(router.route(event))
                .thenReturn(new ChannelAgentRouter.Result(
                        "connection-1",
                        "tenant-a",
                        "employee-1",
                        "agent-1",
                        null,
                        ChannelAgentRouter.Source.EMPLOYEE,
                        ChannelAgentRouter.UnboundPolicy.SILENT,
                        true));
        AgentResponse response = AgentResponse.forConversation("ignored").complete("你好");
        response.setRunId("run-1");
        when(agents.run(eq("agent-1"), any())).thenAnswer(invocation -> {
            assertThat(UserContextHolder.currentTenantId()).isEqualTo("tenant-a");
            assertThat(UserContextHolder.currentUserId()).isEqualTo("employee-1");
            assertThat(UserContextHolder.currentPrincipalType()).isEqualTo(PrincipalType.CHAT_SESSION);
            assertThat(UserContextHolder.get().attr("channelAccountId")).isEqualTo("account-1");
            return response;
        });
        ChannelAgentInboundHandler handler = new ChannelAgentInboundHandler(router, agents, identities);

        ChannelInboundResult result = handler.handle(event);

        ArgumentCaptor<AgentRequest> request = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agents).run(eq("agent-1"), request.capture());
        assertThat(request.getValue().getConversationId())
                .isEqualTo("openclaw:connection-1:employee-1:user:conversation-1");
        assertThat(request.getValue().getVariables().get("memoryOwnerId"))
                .isEqualTo("channel:employee-1:openclaw:account-1:conversation-1");
        assertThat(request.getValue().getVariables())
                .doesNotContainKey("senderId")
                .containsEntry("tenantId", "tenant-a")
                .containsEntry("employeeId", "employee-1")
                .containsEntry("externalIdentityVerified", false)
                .containsEntry("agentRouteSource", "EMPLOYEE");
        assertThat(result.handled()).isTrue();
        assertThat(result.reply()).isEqualTo("你好");
        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    void existingConversationKeepsItsPinnedAgentVersionAfterBindingChanges() {
        ChannelAgentRouter router = mock(ChannelAgentRouter.class);
        AgentService agents = mock(AgentService.class);
        AgentVersionService versions = mock(AgentVersionService.class);
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        ChannelConversationService conversations = mock(ChannelConversationService.class);
        ChannelInboundEvent event = new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "account-1",
                "message-3",
                "sender-1",
                "conversation-1",
                "继续上次的问题",
                Instant.now(),
                false,
                Map.of());
        when(router.route(event))
                .thenReturn(new ChannelAgentRouter.Result(
                        "connection-1",
                        "tenant-a",
                        "employee-1",
                        "agent-new",
                        "version-new",
                        null,
                        null,
                        ChannelAgentRouter.Source.EMPLOYEE,
                        "employee:pinned_version",
                        9,
                        ChannelAgentRouter.UnboundPolicy.SILENT,
                        true));
        when(conversations.agentAssignment("tenant-a", "connection-1", "conversation-1"))
                .thenReturn(new ChannelConversationService.AgentAssignment(
                        "agent-old", "version-old", "employee:latest_active_version", 7));
        AgentDefinition pinnedDefinition =
                AgentDefinition.builder().id("agent-old").tenantId("tenant-a").build();
        when(versions.definition("tenant-a", "agent-old", "version-old")).thenReturn(pinnedDefinition);
        when(agents.runDefinition(eq(pinnedDefinition), any()))
                .thenReturn(AgentResponse.forConversation("conversation-1").complete("仍使用旧版本"));
        ChannelAgentInboundHandler handler =
                new ChannelAgentInboundHandler(router, agents, identities, versions, conversations);

        ChannelInboundResult result = handler.handle(event);

        verify(agents).runDefinition(eq(pinnedDefinition), any());
        verify(agents, never()).run(eq("agent-new"), any());
        assertThat(result.reply()).isEqualTo("仍使用旧版本");
        assertThat(result.metadata())
                .containsEntry("agentId", "agent-old")
                .containsEntry("agentVersionId", "version-old")
                .containsEntry("routingPolicyVersion", 7L);
    }

    @Test
    void unavailablePrimaryAgentUsesConfiguredFallbackAndExplainsWhy() {
        ChannelAgentRouter router = mock(ChannelAgentRouter.class);
        AgentService agents = mock(AgentService.class);
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        ChannelInboundEvent event = new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "account-1",
                "message-4",
                "sender-1",
                "conversation-2",
                "需要帮助",
                Instant.now(),
                false,
                Map.of());
        when(router.route(event))
                .thenReturn(new ChannelAgentRouter.Result(
                        "connection-1",
                        "tenant-a",
                        "employee-1",
                        "agent-primary",
                        null,
                        "agent-fallback",
                        null,
                        ChannelAgentRouter.Source.EMPLOYEE,
                        "employee:latest_active_version",
                        5,
                        ChannelAgentRouter.UnboundPolicy.SILENT,
                        true));
        when(agents.run(eq("agent-primary"), any())).thenThrow(new IllegalStateException("model unavailable"));
        when(agents.run(eq("agent-fallback"), any()))
                .thenReturn(AgentResponse.forConversation("conversation-2").complete("降级服务已响应"));
        ChannelAgentInboundHandler handler = new ChannelAgentInboundHandler(router, agents, identities);

        ChannelInboundResult result = handler.handle(event);

        assertThat(result.reply()).isEqualTo("降级服务已响应");
        assertThat(result.metadata())
                .containsEntry("agentId", "agent-fallback")
                .containsEntry("fallbackUsed", true)
                .containsEntry("routeReason", "fallback:primary_exception:IllegalStateException");
        ArgumentCaptor<AgentRequest> fallbackRequest = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agents).run(eq("agent-fallback"), fallbackRequest.capture());
        assertThat(fallbackRequest.getValue().getVariables())
                .containsEntry("agentFallback", true)
                .containsEntry("effectiveAgentId", "agent-fallback")
                .containsEntry("agentRouteReason", "fallback:primary_exception:IllegalStateException");
    }

    @Test
    void disabledPrimaryVersionSelectsAndPinsRunnableFallbackBeforeExecution() {
        ChannelAgentRouter router = mock(ChannelAgentRouter.class);
        AgentService agents = mock(AgentService.class);
        AgentVersionService versions = mock(AgentVersionService.class);
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        ChannelConversationService conversations = mock(ChannelConversationService.class);
        ChannelInboundEvent event = new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "account-1",
                "message-disabled",
                "sender-1",
                "conversation-disabled",
                "需要帮助",
                Instant.now(),
                false,
                Map.of());
        when(router.route(event))
                .thenReturn(new ChannelAgentRouter.Result(
                        "connection-1",
                        "tenant-a",
                        "employee-1",
                        "agent-disabled",
                        "version-disabled",
                        "agent-fallback",
                        "version-fallback",
                        ChannelAgentRouter.Source.CONNECTION,
                        "connection:pinned_version",
                        12,
                        ChannelAgentRouter.UnboundPolicy.SILENT,
                        true));
        when(versions.requireRunnable("tenant-a", "agent-disabled", "version-disabled"))
                .thenThrow(new IllegalStateException("disabled"));
        AgentVersionEntity fallbackVersion = new AgentVersionEntity();
        fallbackVersion.setId("version-fallback");
        when(versions.requireRunnable("tenant-a", "agent-fallback", "version-fallback"))
                .thenReturn(fallbackVersion);
        AgentDefinition fallbackDefinition = AgentDefinition.builder()
                .id("agent-fallback")
                .tenantId("tenant-a")
                .build();
        when(versions.definition("tenant-a", "agent-fallback", "version-fallback"))
                .thenReturn(fallbackDefinition);
        when(agents.runDefinition(eq(fallbackDefinition), any()))
                .thenReturn(
                        AgentResponse.forConversation("conversation-disabled").complete("fallback ok"));
        ChannelAgentInboundHandler handler =
                new ChannelAgentInboundHandler(router, agents, identities, versions, conversations);

        ChannelInboundResult result = handler.handle(event);

        assertThat(result.reply()).isEqualTo("fallback ok");
        assertThat(result.metadata())
                .containsEntry("agentId", "agent-fallback")
                .containsEntry("agentVersionId", "version-fallback")
                .containsEntry("fallbackUsed", true)
                .containsEntry("routeReason", "fallback:primary_unavailable:IllegalStateException");
        verify(conversations)
                .promoteFallbackRoute(
                        "tenant-a",
                        "connection-1",
                        "conversation-disabled",
                        "agent-disabled",
                        "agent-fallback",
                        "version-fallback",
                        "fallback:primary_unavailable:IllegalStateException",
                        12);
        verify(agents, never()).run(eq("agent-disabled"), any());
    }

    @Test
    void unavailableFallbackReturnsControlledAgentErrorInsteadOfEscapingDispatcher() {
        ChannelAgentRouter router = mock(ChannelAgentRouter.class);
        AgentService agents = mock(AgentService.class);
        AgentVersionService versions = mock(AgentVersionService.class);
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        ChannelInboundEvent event = new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "account-1",
                "message-no-fallback",
                "sender-1",
                "conversation-no-fallback",
                "hello",
                Instant.now(),
                false,
                Map.of());
        when(router.route(event))
                .thenReturn(new ChannelAgentRouter.Result(
                        "connection-1",
                        "tenant-a",
                        "employee-1",
                        "agent-disabled",
                        "version-disabled",
                        "agent-fallback",
                        "version-disabled-too",
                        ChannelAgentRouter.Source.EMPLOYEE,
                        "employee:pinned_version",
                        4,
                        ChannelAgentRouter.UnboundPolicy.SILENT,
                        true));
        when(versions.requireRunnable("tenant-a", "agent-disabled", "version-disabled"))
                .thenThrow(new IllegalStateException("primary disabled"));
        when(versions.requireRunnable("tenant-a", "agent-fallback", "version-disabled-too"))
                .thenThrow(new IllegalStateException("fallback disabled"));
        ChannelAgentInboundHandler handler = new ChannelAgentInboundHandler(router, agents, identities, versions);

        ChannelInboundResult result = handler.handle(event);

        assertThat(result.handled()).isTrue();
        assertThat(result.code()).isEqualTo("agent_error");
        assertThat(result.reply()).isEqualTo("服务暂时不可用，请稍后重试。");
        assertThat(result.metadata())
                .containsEntry("fallbackUsed", true)
                .containsEntry("routeReason", "fallback:unavailable:IllegalStateException")
                .containsEntry("errorType", "IllegalStateException");
        verifyNoInteractions(agents);
    }

    @Test
    void publishedChannelAgentUsesItsExplicitRuntimeExtension() {
        ChannelAgentRouter router = mock(ChannelAgentRouter.class);
        AgentService agents = mock(AgentService.class);
        AgentVersionService versions = mock(AgentVersionService.class);
        AgentRuntimeRegistry runtimes = mock(AgentRuntimeRegistry.class);
        ChannelIdentityService identities = mock(ChannelIdentityService.class);
        ChannelConversationService conversations = mock(ChannelConversationService.class);
        ChannelInboundEvent event = new ChannelInboundEvent(
                "openclaw",
                "qqbot",
                "account-1",
                "message-5",
                "sender-1",
                "conversation-3",
                "使用图运行时",
                Instant.now(),
                false,
                Map.of());
        when(router.route(event))
                .thenReturn(new ChannelAgentRouter.Result(
                        "connection-1",
                        "tenant-a",
                        "employee-1",
                        "agent-graph",
                        "version-4",
                        null,
                        null,
                        ChannelAgentRouter.Source.CONNECTION,
                        "connection:pinned_version",
                        3,
                        ChannelAgentRouter.UnboundPolicy.SILENT,
                        true));
        when(conversations.agentAssignment("tenant-a", "connection-1", "conversation-3"))
                .thenReturn(new ChannelConversationService.AgentAssignment(
                        "agent-graph", "version-4", "connection:pinned_version", 3));
        AgentDefinition definition = AgentDefinition.builder()
                .id("agent-graph")
                .runtimeType("CUSTOM_RUNTIME")
                .runtimeRef("support-graph")
                .build();
        when(versions.definition("tenant-a", "agent-graph", "version-4")).thenReturn(definition);
        when(runtimes.run(eq(definition), any(), isNull(), isNull()))
                .thenReturn(AgentResponse.forConversation("conversation-3").complete("Alibaba Graph 回复"));
        ChannelAgentInboundHandler handler =
                new ChannelAgentInboundHandler(router, agents, identities, versions, conversations, runtimes);

        ChannelInboundResult result = handler.handle(event);

        assertThat(result.reply()).isEqualTo("Alibaba Graph 回复");
        verify(runtimes).run(eq(definition), any(), isNull(), isNull());
        verify(agents, never()).runDefinition(any(), any());
    }
}
