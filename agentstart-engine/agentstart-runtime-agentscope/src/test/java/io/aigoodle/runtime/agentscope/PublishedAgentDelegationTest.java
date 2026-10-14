package io.aigoodle.runtime.agentscope;

import static org.junit.jupiter.api.Assertions.*;

import io.aigoodle.agent.api.AgentDefinition;
import io.aigoodle.agent.api.AgentRequest;
import io.aigoodle.agent.api.AgentResponse;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublishedAgentDelegationTest {
    @Test
    void propagatesTrustedAncestryAndRestoresCallerAfterNestedExecution() {
        var caller = CurrentUser.builder().tenantId("tenant").userId("owner").build();
        UserContextHolder.runAs(caller, () -> {
            var answer = PublishedAgentDelegation.run(
                    AgentDefinition.builder().id("parent").build(), "child", AgentRequest.of("task"), (id, request) -> {
                        assertEquals(
                                List.of("parent", "child"),
                                UserContextHolder.require().attr(PublishedAgentDelegation.PATH_ATTRIBUTE));
                        assertEquals("tenant", UserContextHolder.currentTenantId());
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> PublishedAgentDelegation.run(
                                        AgentDefinition.builder().id("child").build(),
                                        "parent",
                                        request,
                                        (target, input) -> {
                                            throw new AssertionError("Cycle must not execute");
                                        }));
                        return AgentResponse.forConversation("child").complete("answer");
                    });
            assertEquals("answer", answer.getText());
            assertSame(caller, UserContextHolder.get());
            assertNull(caller.attr(PublishedAgentDelegation.PATH_ATTRIBUTE));
        });
    }

    @Test
    void refusesMalformedAncestryAndDepthOverflowBeforeCallingTheRunner() {
        for (Object ancestry : List.of("invalid", java.util.Collections.nCopies(16, "ancestor"))) {
            var user = CurrentUser.builder()
                    .userId("owner")
                    .extra(Map.of(PublishedAgentDelegation.PATH_ATTRIBUTE, ancestry))
                    .build();
            UserContextHolder.runAs(
                    user,
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> PublishedAgentDelegation.run(
                                    AgentDefinition.builder().id("parent").build(),
                                    "child",
                                    AgentRequest.of("task"),
                                    (id, request) -> {
                                        throw new AssertionError("Invalid path must not execute");
                                    })));
        }
    }
}
