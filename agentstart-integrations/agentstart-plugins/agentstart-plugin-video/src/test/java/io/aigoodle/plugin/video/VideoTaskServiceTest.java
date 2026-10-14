package io.aigoodle.plugin.video;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import io.aigoodle.common.crypto.AesGcmTextEncryptor;
import io.aigoodle.connector.execution.ConnectorExecutionContext;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.service.CredentialCodec;
import io.aigoodle.model.video.*;
import io.aigoodle.plugin.*;
import io.aigoodle.plugin.video.entity.VideoTaskEntity;
import io.aigoodle.plugin.video.mapper.VideoTaskMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class VideoTaskServiceTest {
    VideoTaskStore store;
    VideoModelService models;
    VideoModel video;
    VideoTaskService service;
    CredentialCodec codec;
    VideoTaskMapper mapper;
    DataSourceTransactionManager transactionManager;
    DriverManagerDataSource source;
    ModelEndpoint endpoint;

    PluginContext context(String tenant, String execution) {
        return new PluginContext(
                new ConnectorExecutionContext(execution, tenant, "user", null, null, null, null, Map.of()),
                Map.of(),
                Map.of(),
                (name, args) -> null);
    }

    PluginInvocation input(Map<String, Object> parameters) {
        return new PluginInvocation(
                "generate",
                Map.of("model", Map.of("modelId", "configured"), "prompt", "A bird flying", "parameters", parameters));
    }

    @BeforeEach
    void setup() throws Exception {
        source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/plugin-video-schema.sql")).execute(source);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        var sessions = factory.getObject();
        sessions.getConfiguration().addMapper(VideoTaskMapper.class);
        mapper = new SqlSessionTemplate(sessions).getMapper(VideoTaskMapper.class);
        transactionManager = new DataSourceTransactionManager(source);
        store = new VideoTaskStore(mapper, transactionManager);
        models = mock(VideoModelService.class);
        video = mock(VideoModel.class);
        codec = new CredentialCodec(new AesGcmTextEncryptor("video-test-secret"));
        service = new VideoTaskService(store, models, codec);
        endpoint = ModelEndpoint.builder()
                .id("configured")
                .tenantId("tenant")
                .providerName("video-test")
                .modelName("video-v1")
                .modelType(ModelType.VIDEO)
                .apiKey("must-remain-secret")
                .build();
        when(models.resolve(eq("tenant"), anyMap())).thenReturn(endpoint);
        when(models.create(any())).thenReturn(video);
        when(video.maxPromptLength()).thenReturn(10000);
        when(video.parameterSchema())
                .thenReturn(Map.of(
                        "type",
                        "object",
                        "additionalProperties",
                        false,
                        "properties",
                        Map.of("duration", Map.of("type", "integer", "minimum", 2, "maximum", 12))));
        when(video.submit(any())).thenReturn("vendor-task");
        when(video.query(any()))
                .thenReturn(new VideoResult(
                        VideoResult.Status.SUCCEEDED, "https://media.example/video.mp4", null, Map.of()));
    }

    @AfterEach
    void close() {
        service.close();
    }

    @Test
    void starterScansMapperAndInitializesSchemaIdempotently() {
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        VideoPluginAutoConfiguration.class,
                        com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
                        org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
                                .class))
                .withBean(javax.sql.DataSource.class, () -> source)
                .withBean(VideoModelService.class, () -> models)
                .withBean(CredentialCodec.class, () -> codec);
        for (int i = 0; i < 2; i++)
            runner.run(context -> {
                assertNull(context.getStartupFailure());
                assertNotNull(context.getBean(VideoTaskMapper.class));
                assertNotNull(context.getBean(VideoGenerationPlugin.class));
            });
    }

    @Test
    void dueQueryMapsEntitiesAndExpiresUncertainSubmissions() {
        store.reserve("ready", "key-ready", "tenant", "owner", "hash", "cipher");
        store.accepted("tenant", "ready", "vendor");
        store.reserve("stale", "key-stale", "tenant", "owner", "hash", "cipher");
        mapper.update(
                null,
                new LambdaUpdateWrapper<VideoTaskEntity>()
                        .in(VideoTaskEntity::getId, "ready", "stale")
                        .set(
                                VideoTaskEntity::getNextPollAt,
                                java.time.Instant.now().minusSeconds(1)));
        var due = store.due(1);
        assertEquals(1, due.size());
        assertEquals("ready", due.getFirst().id());
        assertEquals("tenant", due.getFirst().tenant());
        assertEquals("vendor", due.getFirst().vendorId());
        assertEquals("UNKNOWN", store.get("tenant", "stale").status());
    }

    @Test
    void restartUsesEncryptedOriginalEndpointAndNeverResubmits() {
        var context = context("tenant", "execution");
        var first = service.submit(input(Map.of("duration", 5)), context);
        assertNotNull(first.task());
        String cipher = store.get("tenant", first.task().id()).endpointCipher();
        assertFalse(cipher.contains("must-remain-secret"));
        assertEquals(
                first.task().id(),
                service.submit(input(Map.of("duration", 5)), context).task().id());
        clearInvocations(models);
        try (var restarted = new VideoTaskService(store, models, codec)) {
            var result = restarted.query(first.task(), context);
            assertTrue(result.result().success());
            verify(models, never()).resolve(anyString(), anyMap());
            verify(models)
                    .create(argThat(saved ->
                            "must-remain-secret".equals(saved.getApiKey()) && "video-v1".equals(saved.getModelName())));
        }
        verify(video, times(1)).submit(any());
    }

    @Test
    void uncertainSubmissionRemainsUnknownAcrossRetry() {
        when(video.submit(any())).thenThrow(new IllegalStateException("connection reset after submit"));
        var context = context("tenant", "uncertain");
        assertFalse(service.submit(input(Map.of()), context).result().success());
        assertFalse(service.submit(input(Map.of()), context).result().success());
        verify(video, times(1)).submit(any());
        assertEquals("UNKNOWN", mapper.selectList(null).getFirst().getStatus());
    }

    @Test
    void invalidParametersAndPromptCannotCreatePaidTasks() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.submit(input(Map.of("duration", 100)), context("tenant", "bad")));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.submit(input(Map.of("invented", true)), context("tenant", "bad")));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.submit(
                        new PluginInvocation(
                                "generate", Map.of("model", Map.of("modelId", "configured"), "prompt", " ")),
                        context("tenant", "bad")));
        verify(video, never()).submit(any());
        assertEquals(0L, mapper.selectCount(null));
    }

    @Test
    void taskCannotBeQueriedByDifferentTenantOrExecution() {
        var pending = service.submit(input(Map.of()), context("tenant", "one"));
        assertThrows(IllegalArgumentException.class, () -> service.query(pending.task(), context("other", "one")));
        assertThrows(IllegalArgumentException.class, () -> service.query(pending.task(), context("tenant", "two")));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.submit(input(Map.of("duration", 6)), context("tenant", "one")));
        verify(video, never()).query(any());
    }

    @Test
    void cancellationIsDurableAndPendingUntilVendorStops() {
        when(video.query(any())).thenReturn(new VideoResult(VideoResult.Status.RUNNING, null, null, Map.of()));
        var context = context("tenant", "cancel");
        var pending = service.submit(input(Map.of()), context);
        assertFalse(service.cancel(pending.task(), context).result().success());
        assertTrue(store.get("tenant", pending.task().id()).cancel());
        clearInvocations(video);
        mapper.update(
                null,
                new LambdaUpdateWrapper<VideoTaskEntity>()
                        .eq(VideoTaskEntity::getId, pending.task().id())
                        .set(
                                VideoTaskEntity::getNextPollAt,
                                java.time.Instant.now().minusSeconds(1)));
        when(video.query(any())).thenReturn(new VideoResult(VideoResult.Status.CANCELLED, null, null, Map.of()));
        try (var restarted = new VideoTaskService(store, models, codec)) {
            restarted.query(pending.task(), context);
        }
        assertEquals("CANCELLED", store.get("tenant", pending.task().id()).status());
        verify(video).cancel("vendor-task");
    }

    @Test
    void committedReservationSurvivesOuterRollbackAndDuplicateDoesNotPoisonCaller() {
        var outer = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        outer.executeWithoutResult(status -> {
            assertTrue(store.reserve("tx-task", "tx-key", "tenant", "owner", "hash", "cipher"));
            assertFalse(store.reserve("duplicate", "tx-key", "tenant", "owner", "hash", "cipher"));
            assertNotNull(store.byInvocation("tenant", "tx-key"));
            status.setRollbackOnly();
        });
        assertNotNull(store.get("tenant", "tx-task"));
        store.accepted("tenant", "tx-task", "vendor-task");
        var saved = store.get("tenant", "tx-task");
        String lease = store.claim(saved);
        assertNotNull(lease);
        store.finish(saved, lease, "RUNNING", "previous", "error", -1);
        lease = store.claim(saved);
        assertNotNull(lease);
        store.finish(saved, lease, "SUCCEEDED", "done", null, 5);
        var row = mapper.selectById("tx-task");
        assertNull(row.getLeaseToken());
        assertNull(row.getLeaseUntil());
        assertNull(row.getLastError());
        assertEquals(2, row.getAttempts());
    }

    @Test
    void pollLeaseAndBackoffPreventDuplicateQueries() {
        var pending = service.submit(input(Map.of()), context("tenant", "lease"));
        var saved = store.get("tenant", pending.task().id());
        var lease = store.claim(saved);
        assertNotNull(lease);
        assertNull(store.claim(saved));
        store.finish(saved, "wrong-token", "FAILED", null, null, 5);
        assertEquals("QUEUED", store.get("tenant", saved.id()).status());
        store.finish(saved, lease, "RUNNING", null, null, 30);
        assertNull(store.claim(saved));
    }
}
