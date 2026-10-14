package io.aigoodle.workflow.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.workflow.graph.EdgeDef;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.graph.NodeType;
import io.aigoodle.workflow.graph.WorkflowGraph;
import io.aigoodle.workflow.node.NodeExecutor;
import io.aigoodle.workflow.node.NodeResult;
import io.aigoodle.workflow.node.builtin.EndNodeExecutor;
import io.aigoodle.workflow.node.builtin.HttpRequestNodeExecutor;
import io.aigoodle.workflow.node.builtin.IfElseNodeExecutor;
import io.aigoodle.workflow.node.builtin.StartNodeExecutor;
import io.aigoodle.workflow.node.builtin.TemplateTransformNodeExecutor;
import io.aigoodle.workflow.node.builtin.VariableAggregatorNodeExecutor;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Deterministic, offline tests of the DAG engine: variable interpolation, conditional
 * branching, step recording and a real HTTP node against an embedded server.
 */
class WorkflowEngineTest {

    private WorkflowEngine engine() {
        List<NodeExecutor> executors = List.of(
                new StartNodeExecutor(), new EndNodeExecutor(),
                new TemplateTransformNodeExecutor(), new IfElseNodeExecutor(),
                new VariableAggregatorNodeExecutor(), new HttpRequestNodeExecutor());
        return new WorkflowEngine(new NodeExecutorRegistry(executors));
    }

    private WorkflowGraph branchingGraph() {
        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("greet", NodeType.TEMPLATE_TRANSFORM)
                .with("template", "Hello {{#start.name#}}")
                .with("outputKey", "msg"));
        g.addNode(NodeDef.of("check", NodeType.IF_ELSE)
                .with("logicalOperator", "and")
                .with("conditions", List.of(Map.of("variable", "start.vip", "operator", "equals", "value", "true"))));
        g.addNode(NodeDef.of("endYes", NodeType.END).with("outputs", Map.of("answer", "VIP {{#greet.msg#}}")));
        g.addNode(NodeDef.of("endNo", NodeType.END).with("outputs", Map.of("answer", "{{#greet.msg#}}")));
        g.addEdge(EdgeDef.of("start", "greet"));
        g.addEdge(EdgeDef.of("greet", "check"));
        g.addEdge(EdgeDef.of("check", "endYes", "true"));
        g.addEdge(EdgeDef.of("check", "endNo", "false"));
        return g;
    }

    @Test
    void propagatesTrustedAppAttributionToNodeThreads() {
        var captured = new AtomicReference<io.aigoodle.common.context.ModelExecutionScope.Attribution>();
        NodeExecutor start = new NodeExecutor() {
            @Override
            public NodeType type() {
                return NodeType.START;
            }

            @Override
            public NodeResult execute(NodeDef node, io.aigoodle.workflow.node.ExecutionContext context) {
                captured.set(io.aigoodle.common.context.ModelExecutionScope.current());
                assertEquals("app-metered", context.getAppId());
                return NodeResult.empty();
            }
        };
        var engine = new WorkflowEngine(new NodeExecutorRegistry(List.of(start, new EndNodeExecutor())));
        var graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START));
        graph.addNode(NodeDef.of("end", NodeType.END));
        graph.addEdge(EdgeDef.of("start", "end"));
        var result = engine.run(
                graph,
                Map.of("appId", "untrusted-app"),
                null,
                null,
                null,
                "tenant-metered",
                WorkflowRunOptions.defaults().withAppId("app-metered"));
        assertTrue(result.isSuccess(), result.getError());
        assertEquals(
                new io.aigoodle.common.context.ModelExecutionScope.Attribution("tenant-metered", "app-metered"),
                captured.get());
        assertNull(io.aigoodle.common.context.ModelExecutionScope.current());
    }

    @Test
    void interpolatesAndTakesTrueBranch() {
        WorkflowRunResult r = engine().run(branchingGraph(), Map.of("name", "Alice", "vip", "true"), null);
        assertTrue(r.isSuccess(), r.getError());
        assertEquals("VIP Hello Alice", r.output("answer"));
        // the false-branch END must not have executed
        assertTrue(r.getSteps().stream().noneMatch(s -> "endNo".equals(s.getNodeId())));
        assertTrue(r.getSteps().stream().anyMatch(s -> "endYes".equals(s.getNodeId())));
    }

    @Test
    void takesFalseBranch() {
        WorkflowRunResult r = engine().run(branchingGraph(), Map.of("name", "Bob", "vip", "false"), null);
        assertTrue(r.isSuccess(), r.getError());
        assertEquals("Hello Bob", r.output("answer"));
    }

    @Test
    void recordsEveryStep() {
        WorkflowRunResult r = engine().run(branchingGraph(), Map.of("name", "Z", "vip", "true"), null);
        // start, greet, check, endYes
        assertEquals(4, r.getSteps().size());
        assertNotNull(r.getRunId());
    }

    @Test
    void capturesCurrentTenantBeforeDispatchingNodesToVirtualThreads() {
        AtomicReference<String> executedTenant = new AtomicReference<>();
        NodeExecutor capturingStart = new NodeExecutor() {
            @Override
            public NodeType type() {
                return NodeType.START;
            }

            @Override
            public NodeResult execute(NodeDef node, io.aigoodle.workflow.node.ExecutionContext context) {
                executedTenant.set(context.getTenantId());
                return NodeResult.empty();
            }
        };
        WorkflowEngine tenantAwareEngine =
                new WorkflowEngine(new NodeExecutorRegistry(List.of(capturingStart, new EndNodeExecutor())));
        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START));
        graph.addNode(NodeDef.of("end", NodeType.END));
        graph.addEdge(EdgeDef.of("start", "end"));

        CurrentUser user =
                CurrentUser.builder().userId("user-1").tenantId("tenant-a").build();
        WorkflowRunResult result = UserContextHolder.callAs(user, () -> tenantAwareEngine.run(graph, Map.of(), null));

        assertTrue(result.isSuccess(), result.getError());
        assertEquals("tenant-a", executedTenant.get());
    }

    @Test
    void usesExplicitRunTenantWithoutDependingOnThreadLocalContext() {
        AtomicReference<String> executedTenant = new AtomicReference<>();
        NodeExecutor capturingStart = new NodeExecutor() {
            @Override
            public NodeType type() {
                return NodeType.START;
            }

            @Override
            public NodeResult execute(NodeDef node, io.aigoodle.workflow.node.ExecutionContext context) {
                executedTenant.set(context.getTenantId());
                return NodeResult.empty();
            }
        };
        WorkflowEngine tenantAwareEngine =
                new WorkflowEngine(new NodeExecutorRegistry(List.of(capturingStart, new EndNodeExecutor())));
        WorkflowGraph graph = new WorkflowGraph();
        graph.addNode(NodeDef.of("start", NodeType.START));
        graph.addNode(NodeDef.of("end", NodeType.END));
        graph.addEdge(EdgeDef.of("start", "end"));

        WorkflowRunResult result = tenantAwareEngine.run(graph, Map.of(), null, null, null, "stored-workflow-tenant");

        assertTrue(result.isSuccess(), result.getError());
        assertEquals("stored-workflow-tenant", executedTenant.get());
    }

    @Test
    void httpNodeCallsRealServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ping", exchange -> {
            byte[] body = "{\"pong\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            WorkflowGraph g = new WorkflowGraph();
            g.addNode(NodeDef.of("start", NodeType.START));
            g.addNode(NodeDef.of("http", NodeType.HTTP_REQUEST)
                    .with("method", "GET")
                    .with("url", "http://127.0.0.1:" + port + "/ping"));
            g.addNode(NodeDef.of("end", NodeType.END)
                    .with("outputs", Map.of("status", "{{#http.status#}}", "body", "{{#http.body#}}")));
            g.addEdge(EdgeDef.of("start", "http"));
            g.addEdge(EdgeDef.of("http", "end"));

            WorkflowRunResult r = engine().run(g, Map.of(), null);
            assertTrue(r.isSuccess(), r.getError());
            assertEquals("200", String.valueOf(r.output("status")));
            assertTrue(String.valueOf(r.output("body")).contains("pong"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpNodePostsJsonWithHeadersAuthAndQuery() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedAuth = new AtomicReference<>();
        AtomicReference<String> receivedTrace = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();
        AtomicReference<String> receivedQuery = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", (HttpExchange exchange) -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedTrace.set(exchange.getRequestHeaders().getFirst("X-Trace"));
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedQuery.set(exchange.getRequestURI().getRawQuery());
            try (InputStream in = exchange.getRequestBody();
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                receivedBody.set(out.toString(StandardCharsets.UTF_8));
            }
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Server", "test");
            exchange.sendResponseHeaders(201, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            WorkflowGraph g = new WorkflowGraph();
            g.addNode(NodeDef.of("start", NodeType.START));
            g.addNode(NodeDef.of("http", NodeType.HTTP_REQUEST)
                    .with("method", "POST")
                    .with("url", "http://127.0.0.1:" + port + "/echo")
                    .with("headers", List.of(Map.of("name", "X-Trace", "value", "{{#start.trace#}}")))
                    .with(
                            "parameters",
                            List.of(
                                    Map.of("name", "q", "value", "hello world"),
                                    Map.of("name", "user", "value", "{{#start.user#}}")))
                    .with(
                            "authorization",
                            Map.of(
                                    "auth_type", "api_key",
                                    "api_key_header", "Authorization",
                                    "api_key_header_prefix", "bearer",
                                    "api_key_value", "s3cret"))
                    .with("bodyType", "JSON")
                    .with("body", Map.of("JSON", "{\"name\":\"{{#start.user#}}\"}")));
            g.addNode(NodeDef.of("end", NodeType.END)
                    .with(
                            "outputs",
                            Map.of(
                                    "status", "{{#http.status#}}",
                                    "body", "{{#http.body#}}")));
            g.addEdge(EdgeDef.of("start", "http"));
            g.addEdge(EdgeDef.of("http", "end"));

            WorkflowRunResult r = engine().run(g, Map.of("user", "alice", "trace", "abc123"), null);
            assertTrue(r.isSuccess(), r.getError());
            assertEquals("201", String.valueOf(r.output("status")));
            assertTrue(String.valueOf(r.output("body")).contains("ok"));
            assertEquals("Bearer s3cret", receivedAuth.get(), "authorization prefix should be applied");
            assertEquals("abc123", receivedTrace.get(), "designer array-shape headers must be sent");
            assertNotNull(receivedContentType.get());
            assertTrue(
                    receivedContentType.get().startsWith("application/json"),
                    "JSON body must default Content-Type; got: " + receivedContentType.get());
            assertEquals("{\"name\":\"alice\"}", receivedBody.get());
            assertNotNull(receivedQuery.get(), "parameters should be appended as query string");
            assertTrue(
                    receivedQuery.get().contains("q=hello+world")
                            || receivedQuery.get().contains("q=hello%20world"),
                    "query values must be URL-encoded: " + receivedQuery.get());
            assertTrue(
                    receivedQuery.get().contains("user=alice"),
                    "templated query params must render: " + receivedQuery.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpNodePrependsBaseUrlForRelativePaths() throws Exception {
        AtomicReference<String> receivedPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            // Note trailing slash on the base URL — executor should normalise
            // it so we don't get "//api/foo".
            HttpRequestNodeExecutor http = new HttpRequestNodeExecutor("http://127.0.0.1:" + port + "/");
            WorkflowEngine e = new WorkflowEngine(
                    new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new EndNodeExecutor(), http)));

            WorkflowGraph g = new WorkflowGraph();
            g.addNode(NodeDef.of("start", NodeType.START));
            g.addNode(NodeDef.of("http", NodeType.HTTP_REQUEST)
                    .with("method", "GET")
                    .with("url", "/api/foo"));
            g.addNode(NodeDef.of("end", NodeType.END).with("outputs", Map.of("status", "{{#http.status#}}")));
            g.addEdge(EdgeDef.of("start", "http"));
            g.addEdge(EdgeDef.of("http", "end"));

            WorkflowRunResult r = e.run(g, Map.of(), null);
            assertTrue(r.isSuccess(), r.getError());
            assertEquals("/api/foo", receivedPath.get(), "base URL should be prepended");
            assertEquals("200", String.valueOf(r.output("status")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpNodeLeavesAbsoluteUrlAlone() throws Exception {
        AtomicReference<String> receivedPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/direct", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            // Base URL points at a bogus host — absolute URLs must bypass it.
            HttpRequestNodeExecutor http = new HttpRequestNodeExecutor("http://never-used.invalid");
            WorkflowEngine e = new WorkflowEngine(
                    new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new EndNodeExecutor(), http)));

            WorkflowGraph g = new WorkflowGraph();
            g.addNode(NodeDef.of("start", NodeType.START));
            g.addNode(NodeDef.of("http", NodeType.HTTP_REQUEST)
                    .with("method", "GET")
                    .with("url", "http://127.0.0.1:" + port + "/direct"));
            g.addNode(NodeDef.of("end", NodeType.END));
            g.addEdge(EdgeDef.of("start", "http"));
            g.addEdge(EdgeDef.of("http", "end"));

            WorkflowRunResult r = e.run(g, Map.of(), null);
            assertTrue(r.isSuccess(), r.getError());
            assertEquals("/direct", receivedPath.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpNodeRejectsRelativeUrlWhenNoBaseConfigured() {
        HttpRequestNodeExecutor http = new HttpRequestNodeExecutor(""); // no base
        WorkflowEngine e = new WorkflowEngine(
                new NodeExecutorRegistry(List.of(new StartNodeExecutor(), new EndNodeExecutor(), http)));

        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(
                NodeDef.of("http", NodeType.HTTP_REQUEST).with("method", "GET").with("url", "/api/foo"));
        g.addNode(NodeDef.of("end", NodeType.END));
        g.addEdge(EdgeDef.of("start", "http"));
        g.addEdge(EdgeDef.of("http", "end"));

        WorkflowRunResult r = e.run(g, Map.of(), null);
        assertFalse(r.isSuccess());
        assertNotNull(r.getError());
        assertTrue(
                r.getError().contains("http-base-url"),
                "error should point at the missing base-URL config: " + r.getError());
    }

    @Test
    void httpNodeRejectsEmptyUrl() {
        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(
                NodeDef.of("http", NodeType.HTTP_REQUEST).with("method", "GET").with("url", ""));
        g.addNode(NodeDef.of("end", NodeType.END));
        g.addEdge(EdgeDef.of("start", "http"));
        g.addEdge(EdgeDef.of("http", "end"));
        WorkflowRunResult r = engine().run(g, Map.of(), null);
        assertFalse(r.isSuccess());
        assertTrue(
                r.getError() != null && r.getError().toLowerCase().contains("empty"),
                "should surface empty-URL error: " + r.getError());
    }

    @Test
    void failingNodeStopsRunWithError() {
        WorkflowGraph g = new WorkflowGraph();
        g.addNode(NodeDef.of("start", NodeType.START));
        g.addNode(NodeDef.of("http", NodeType.HTTP_REQUEST)
                .with("method", "GET")
                .with("url", "http://127.0.0.1:1/never"));
        g.addNode(NodeDef.of("end", NodeType.END));
        g.addEdge(EdgeDef.of("start", "http"));
        g.addEdge(EdgeDef.of("http", "end"));
        WorkflowRunResult r = engine().run(g, Map.of(), null);
        assertFalse(r.isSuccess());
        assertNotNull(r.getError());
    }
}
