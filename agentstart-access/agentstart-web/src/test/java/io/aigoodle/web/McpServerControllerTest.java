package io.aigoodle.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.aigoodle.tool.ToolRegistry;
import io.aigoodle.tool.mcp.McpClientManager;
import io.aigoodle.tool.mcp.McpInvocationHeaders;
import io.aigoodle.tool.mcp.McpToolProvider;
import io.aigoodle.web.controller.McpServerController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class McpServerControllerTest {
    @Test
    void forwardsHeadersOnAsyncWorkerDuringTestAndRefreshWithoutLeakingToNextRequest() throws Exception {
        var manager = mock(McpClientManager.class);
        var provider = mock(McpToolProvider.class);
        var tools = mock(ToolRegistry.class);
        var beans = new StaticListableBeanFactory();
        beans.addBean("manager", manager);
        beans.addBean("provider", provider);
        beans.addBean("tools", tools);
        var controller = new McpServerController(
                beans.getBeanProvider(McpClientManager.class),
                beans.getBeanProvider(McpToolProvider.class),
                beans.getBeanProvider(ToolRegistry.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(manager.test("school")).thenAnswer(invocation -> {
            assertThat(McpInvocationHeaders.currentAuthorization()).isEqualTo("Bearer business-token");
            return 104;
        });
        doAnswer(invocation -> {
                    assertThat(McpInvocationHeaders.currentAuthorization()).isEqualTo("Bearer business-token");
                    return null;
                })
                .when(tools)
                .refresh();

        var pending = mvc.perform(post("/mcp-servers/school/test")
                        .header("Authorization", "Bearer host-token")
                        .header("X-MCP-Authorization", "Bearer business-token"))
                .andExpect(request().asyncStarted())
                .andReturn();
        pending.getAsyncResult(5000);
        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.toolCount").value(104));
        assertThat(McpInvocationHeaders.currentAuthorization()).isNull();

        doAnswer(invocation -> {
                    assertThat(McpInvocationHeaders.currentAuthorization()).isNull();
                    throw new IllegalStateException("Authorization error when sending message");
                })
                .when(manager)
                .test("school");
        var missing = mvc.perform(post("/mcp-servers/school/test"))
                .andExpect(request().asyncStarted())
                .andReturn();
        missing.getAsyncResult(5000);
        mvc.perform(asyncDispatch(missing))
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(
                        jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("X-MCP-Authorization")));
        verify(tools, times(1)).refresh();
    }

    @Test
    void fallsBackToAuthorizationWhenDedicatedHeaderIsBlank() throws Exception {
        var manager = mock(McpClientManager.class);
        var provider = mock(McpToolProvider.class);
        var tools = mock(ToolRegistry.class);
        var beans = new StaticListableBeanFactory();
        beans.addBean("manager", manager);
        beans.addBean("provider", provider);
        beans.addBean("tools", tools);
        var controller = new McpServerController(
                beans.getBeanProvider(McpClientManager.class),
                beans.getBeanProvider(McpToolProvider.class),
                beans.getBeanProvider(ToolRegistry.class));
        when(manager.test("school")).thenAnswer(invocation -> {
            assertThat(McpInvocationHeaders.currentAuthorization()).isEqualTo("Bearer login-token");
            return 104;
        });
        assertThat(controller
                        .test("school", "Bearer login-token", " ")
                        .get()
                        .getData()
                        .success())
                .isTrue();
    }
}
