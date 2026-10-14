package io.aigoodle.plugin.seedance;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.connector.execution.ConnectorExecutionContext;
import io.aigoodle.plugin.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class SeedanceVideoPluginTest {
    HttpServer server;
    DriverManagerDataSource source;
    io.aigoodle.plugin.seedance.mapper.SeedanceSubmissionMapper mapper;
    org.springframework.jdbc.datasource.DataSourceTransactionManager transactions;
    AtomicInteger posts = new AtomicInteger(), deletes = new AtomicInteger();
    AtomicReference<String> state = new AtomicReference<>("running");
    AtomicReference<Map<String, Object>> submitted = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    boolean rejectSubmission;

    @BeforeEach
    void start() throws Exception {
        source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/plugin-seedance-schema.sql")).execute(source);
        var factory = new com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        var sessions = factory.getObject();
        sessions.getConfiguration().addMapper(io.aigoodle.plugin.seedance.mapper.SeedanceSubmissionMapper.class);
        mapper = new org.mybatis.spring.SqlSessionTemplate(sessions)
                .getMapper(io.aigoodle.plugin.seedance.mapper.SeedanceSubmissionMapper.class);
        transactions = new org.springframework.jdbc.datasource.DataSourceTransactionManager(source);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/contents/generations/tasks", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            Object reply;
            if ("POST".equals(exchange.getRequestMethod())) {
                posts.incrementAndGet();
                submitted.set(JsonUtils.parseMap(
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                if (rejectSubmission) {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                    return;
                }
                reply = Map.of("id", "cgt-test");
            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                deletes.incrementAndGet();
                state.set("cancelled");
                reply = Map.of();
            } else
                reply = Map.of(
                        "id",
                        "cgt-test",
                        "status",
                        state.get(),
                        "content",
                        Map.of("video_url", "https://media.example/video.mp4"));
            byte[] bytes = JsonUtils.toJson(reply).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    SeedanceVideoPlugin plugin() throws Exception {
        return new SeedanceVideoPlugin(
                new SeedanceSubmissionStore(mapper, transactions),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v3"));
    }

    PluginContext context(String tenant) {
        return new PluginContext(
                new ConnectorExecutionContext("exec-1", tenant, "u1", null, null, null, null, Map.of()),
                Map.of("model", "ep-video"),
                Map.of("apiKey", "test-secret"),
                (name, args) -> null);
    }

    PluginInvocation invocation() {
        return new PluginInvocation("generate", Map.of("prompt", "商品展示", "duration", 5));
    }

    @Test
    void submitRestartPollAndCompleteReturnsVideoUrl() throws Exception {
        var first = plugin().execute(invocation(), context("tenant-a"));
        assertThat(first.task().id()).isEqualTo("cgt-test");
        var restarted = plugin();
        assertThat(restarted.execute(invocation(), context("tenant-a")).task().id())
                .isEqualTo("cgt-test");
        assertThat(posts.get()).isEqualTo(1);
        assertThat(authorization.get()).isEqualTo("Bearer test-secret");
        assertThat(submitted.get()).containsEntry("model", "ep-video").containsEntry("duration", 5);
        assertThat(restarted
                        .query(invocation(), first.task(), context("tenant-a"))
                        .task())
                .isNotNull();
        state.set("succeeded");
        assertThat(restarted
                        .query(invocation(), first.task(), context("tenant-a"))
                        .result()
                        .data())
                .isEqualTo(Map.of(
                        "taskId", "cgt-test", "status", "succeeded", "videoUrl", "https://media.example/video.mp4"));
        assertThatThrownBy(() -> restarted.query(invocation(), first.task(), context("tenant-b")))
                .hasMessageContaining("does not belong");
        assertThat(restarted
                        .execute(new PluginInvocation("generate", Map.of("prompt", "changed")), context("tenant-a"))
                        .result()
                        .success())
                .isFalse();
    }

    @Test
    void starterRegistersManifestAndValidatesConnectionAndInputs() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        SeedanceAutoConfiguration.class,
                        io.aigoodle.plugin.config.PluginAutoConfiguration.class,
                        com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
                        org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
                                .class))
                .withBean(javax.sql.DataSource.class, () -> source)
                .withBean(
                        io.aigoodle.plugin.runtime.PluginConnectionResolver.class,
                        () -> request ->
                                new io.aigoodle.connector.connection.ConnectorConnectionService.ResolvedConnection(
                                        Map.of("model", "ep-video"), Map.of("apiKey", "test-secret")))
                .withPropertyValues("spring-agent.plugins.seedance.base-url=http://127.0.0.1:"
                        + server.getAddress().getPort() + "/api/v3")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    var provider = application.getBean(io.aigoodle.plugin.runtime.PluginConnectorProvider.class);
                    assertThat(provider.discover().getFirst().name()).isEqualTo("Seedance 视频生成");
                    var request = new io.aigoodle.connector.execution.ConnectorExecutionRequest(
                            new io.aigoodle.connector.ConnectorKey("plugin", "volcengine.seedance"),
                            "generate",
                            null,
                            null,
                            invocation().inputs(),
                            context("tenant-a").identity());
                    assertThat(provider.execute(request).metadata()).containsEntry("status", "PENDING");
                    var invalid = new io.aigoodle.connector.execution.ConnectorExecutionRequest(
                            request.connector(), "generate", null, null, Map.of(), request.context());
                    assertThatThrownBy(() -> provider.execute(invalid)).hasMessageContaining("JSON Schema");
                    assertThat(posts.get()).isEqualTo(1);
                });
    }

    @Test
    void unknownSubmissionCannotBeAutomaticallyResubmittedAfterRestart() throws Exception {
        rejectSubmission = true;
        assertThatThrownBy(() -> plugin().execute(invocation(), context("tenant-a")))
                .hasMessageContaining("503");
        var retry = plugin().execute(invocation(), context("tenant-a"));
        assertThat(retry.result().error().code()).isEqualTo("seedance_submission_unknown");
        assertThat(posts.get()).isEqualTo(1);
    }

    @Test
    void cancellationDoesNotDeleteCompletedVideoAndFailureIsTerminal() throws Exception {
        var plugin = plugin();
        var context = context("tenant-a");
        var task = plugin.execute(invocation(), context).task();
        assertThat(plugin.cancel(invocation(), task, context).result().success())
                .isFalse();
        assertThat(deletes.get()).isZero();
        state.set("queued");
        assertThat(plugin.cancel(invocation(), task, context).result().success())
                .isTrue();
        assertThat(plugin.cancel(invocation(), task, context).result().success())
                .isTrue();
        assertThat(deletes.get()).isEqualTo(1);
        state.set("succeeded");
        plugin.cancel(invocation(), task, context);
        assertThat(deletes.get()).isEqualTo(1);
        state.set("failed");
        assertThat(plugin.query(invocation(), task, context).result().success()).isFalse();
    }
}
