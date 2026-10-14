package io.aigoodle.tool;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.aigoodle.tool.adapter.ToolDefinitionCallback;
import io.aigoodle.tool.builtin.CalculatorTool;
import io.aigoodle.tool.builtin.CurrentTimeTool;
import io.aigoodle.tool.builtin.HttpGetTool;
import io.aigoodle.tool.execution.ToolExecutionContext;
import io.aigoodle.tool.execution.ToolExecutionGateway;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class ToolsTest {

    @Test
    void calculatorEvaluatesExpressions() {
        CalculatorTool calc = new CalculatorTool();
        assertEquals("14", calc.execute(Map.of("expression", "2*(3+4)")));
        assertEquals("437", calc.execute(Map.of("expression", "23*19")));
        assertEquals("2.5", calc.execute(Map.of("expression", "5/2")));
        assertEquals("-6", calc.execute(Map.of("expression", "-2*3")));
        assertTrue(String.valueOf(calc.execute(Map.of("expression", "bogus"))).startsWith("error"));
    }

    @Test
    void currentTimeReturnsFormattedTime() {
        Object result = new CurrentTimeTool().execute(Map.of("zone", "Asia/Shanghai"));
        assertTrue(String.valueOf(result).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*"));
        assertTrue(String.valueOf(new CurrentTimeTool().execute(Map.of("zone", "Nowhere/Bad")))
                .startsWith("error"));
    }

    @Test
    void httpGetFetchesRealServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/data", ex -> {
            byte[] b = "hello-tool".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            Object out = new HttpGetTool().execute(Map.of("url", "http://127.0.0.1:" + port + "/data"));
            assertTrue(String.valueOf(out).contains("hello-tool"));
            assertTrue(String.valueOf(out).contains("status=200"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void registryResolvesAndExposesCallbacks() {
        ToolRegistry registry = new ToolRegistry(List.of(new CalculatorTool(), new CurrentTimeTool()), List.of());
        assertTrue(registry.has("calculator"));
        assertEquals("14", registry.execute("calculator", Map.of("expression", "2+3*4")));
        assertThrows(RuntimeException.class, () -> registry.get("nope"));

        List<ToolCallback> callbacks = registry.toolCallbacks();
        assertEquals(2, callbacks.size());
        assertEquals(List.of("calculator", "current_time"), registry.names());
        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
        assertThrows(UnsupportedOperationException.class, () -> registry.names().clear());
    }

    @Test
    void registrySelectsKnownCallbacksInRequestedOrder() {
        ToolRegistry registry = new ToolRegistry(List.of(new CalculatorTool(), new CurrentTimeTool()), List.of());

        List<ToolCallback> callbacks = registry.toolCallbacks(List.of("current_time", "missing", "calculator"));

        assertEquals(
                List.of("current_time", "calculator"),
                callbacks.stream()
                        .map(callback -> callback.getToolDefinition().name())
                        .toList());
        assertEquals(List.of(), registry.toolCallbacks(null));
    }

    @Test
    void toolProviderContributesTools() {
        ToolProvider provider = () -> List.of(new CalculatorTool());
        ToolRegistry registry = new ToolRegistry(List.of(), List.of(provider));
        assertTrue(registry.has("calculator"));
    }

    @Test
    void callbackAdapterParsesJsonAndReturnsString() {
        ToolCallback cb = new ToolDefinitionCallback(new CalculatorTool());
        assertEquals("calculator", cb.getToolDefinition().name());
        assertNotNull(cb.getToolDefinition().inputSchema());
        // Spring AI hands the tool a JSON arguments string
        assertEquals("42", cb.call("{\"expression\":\"6*7\"}"));
    }

    @Test
    void registryCallbacksUseGovernedGatewayAndTrustedContext() {
        AtomicReference<ToolExecutionContext> capturedContext = new AtomicReference<>();
        ToolExecutionGateway gateway = (tool, arguments, context) -> {
            capturedContext.set(context);
            return ToolExecutionGateway.invoke(tool, arguments, context);
        };
        ToolExecutionContext trusted = new ToolExecutionContext(
                "run-1", "tenant-a", "employee-7", "conversation-9", Map.of("scope", "tool:call"));
        ToolRegistry registry = new ToolRegistry(List.of(new CalculatorTool()), List.of(), gateway, () -> trusted);

        String result = registry.toolCallbacks(List.of("calculator")).getFirst().call("{\"expression\":\"1+2\"}");

        assertEquals("3", result);
        assertEquals(trusted, capturedContext.get());
    }
}
