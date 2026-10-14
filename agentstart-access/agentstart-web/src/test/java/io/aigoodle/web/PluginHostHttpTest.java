package io.aigoodle.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.aigoodle.connector.ConnectorException;
import io.aigoodle.plugin.host.PluginHostApi;
import io.aigoodle.web.controller.PluginHostController;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PluginHostHttpTest {
    @Test
    void nonStreamingModelRouteUsesInvocationToken() throws Exception {
        var host = mock(PluginHostApi.class);
        when(host.call(eq("invocation-token"), eq("model.chat"), anyMap()))
                .thenReturn(Map.of("text", "A video script"));
        var mvc =
                MockMvcBuilders.standaloneSetup(new PluginHostController(host)).build();
        var pending = mvc.perform(post("/plugin-host/v1/models/chat")
                        .header("Authorization", "Bearer invocation-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"model-a\",\"messages\":[{\"role\":\"user\",\"content\":\"Write\"}]}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        pending.getAsyncResult(5000);
        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("A video script"));
        verify(host).call(eq("invocation-token"), eq("model.chat"), anyMap());
    }

    @Test
    void expiredTokenProducesUnauthorized() throws Exception {
        var host = mock(PluginHostApi.class);
        when(host.call(anyString(), anyString(), anyMap()))
                .thenThrow(new ConnectorException("plugin_host_unauthorized", "Expired"));
        var mvc =
                MockMvcBuilders.standaloneSetup(new PluginHostController(host)).build();
        var pending = mvc.perform(post("/plugin-host/v1/models/chat")
                        .header("Authorization", "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        pending.getAsyncResult(5000);
        mvc.perform(asyncDispatch(pending)).andExpect(status().isUnauthorized());
    }
}
