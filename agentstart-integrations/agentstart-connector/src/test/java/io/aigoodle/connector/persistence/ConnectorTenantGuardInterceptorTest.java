package io.aigoodle.connector.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.aigoodle.common.context.CurrentUser;
import io.aigoodle.common.context.UserContextHolder;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;

class ConnectorTenantGuardInterceptorTest {
    @Test
    void rejectsUnscopedReadsAndWritesOnChannelTables() {
        assertThatThrownBy(() -> ConnectorTenantGuardInterceptor.requireTenantConstraint(
                        "select id, tenant_id from agent_channel_event where id = ?"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ConnectorTenantGuardInterceptor.requireTenantConstraint(
                        "update agent_channel_connection set status = ? where id = ?"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ConnectorTenantGuardInterceptor.requireTenantConstraint(
                        "delete from agent_channel_identity where id = ?"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ConnectorTenantGuardInterceptor.requireTenantConstraint(
                        "select * from agent_connector_connection where id = ?"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ConnectorTenantGuardInterceptor.requireTenantConstraint(
                        "update agent_connector_installation set enabled = ? where id = ?"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ConnectorTenantGuardInterceptor.requireTenantConstraint(
                        "select * from agent_connector_execution order by created_at desc"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void acceptsTenantScopedOperationsAndIgnoresOtherDomains() {
        assertThatCode(() -> ConnectorTenantGuardInterceptor.requireTenantConstraint(
                        "select * from agent_channel_event where tenant_id = ? and id = ?"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ConnectorTenantGuardInterceptor.requireTenantConstraint(
                        "insert into agent_channel_event (id, tenant_id, content) values (?, ?, ?)"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ConnectorTenantGuardInterceptor.requireTenantConstraint(
                        "select * from goodle_model_provider where id = ?"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAValidLookingPredicateBoundToAnotherTenant() {
        assertThatThrownBy(() -> ConnectorTenantGuardInterceptor.requireTenantValues(
                        "select * from agent_channel_event where tenant_id = ? and id = ?",
                        "tenant-a",
                        List.of("tenant-b", "event-1")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("authenticated tenant");
        assertThatThrownBy(() -> ConnectorTenantGuardInterceptor.requireTenantValues(
                        "insert into agent_channel_event (id, content, tenant_id) values (?, ?, ?)",
                        "tenant-a",
                        List.of("event-1", "hello", "tenant-b")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void acceptsTheAuthenticatedTenantForReadsWritesAndReversedPredicates() {
        assertThatCode(() -> ConnectorTenantGuardInterceptor.requireTenantValues(
                        "select * from agent_channel_event where id = ? and tenant_id = ?",
                        "tenant-a",
                        List.of("event-1", "tenant-a")))
                .doesNotThrowAnyException();
        assertThatCode(() -> ConnectorTenantGuardInterceptor.requireTenantValues(
                        "update agent_channel_event set status = ? where ? = tenant_id and id = ?",
                        "tenant-a",
                        List.of("SENT", "tenant-a", "event-1")))
                .doesNotThrowAnyException();
        assertThatCode(() -> ConnectorTenantGuardInterceptor.requireTenantValues(
                        "insert into agent_channel_event (id, tenant_id, content) values (?, ?, ?)",
                        "tenant-a",
                        List.of("event-1", "tenant-a", "hello")))
                .doesNotThrowAnyException();
    }

    @Test
    void interceptorRejectsForeignTenantBeforeExecutingTheMapper() throws Exception {
        Configuration configuration = new Configuration();
        String sql = "select * from agent_channel_event where tenant_id = ? and id = ?";
        List<ParameterMapping> mappings = List.of(
                new ParameterMapping.Builder(configuration, "tenantId", String.class).build(),
                new ParameterMapping.Builder(configuration, "id", String.class).build());
        MappedStatement statement = new MappedStatement.Builder(
                        configuration,
                        "events.find",
                        new StaticSqlSource(configuration, sql, mappings),
                        SqlCommandType.SELECT)
                .build();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantId", "tenant-b");
        parameters.put("id", "event-1");
        Method query = Executor.class.getMethod(
                "query", MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Invocation invocation = new Invocation(
                mock(Executor.class), query, new Object[] {statement, parameters, RowBounds.DEFAULT, null});
        CurrentUser authenticated = CurrentUser.builder().tenantId("tenant-a").build();

        assertThatThrownBy(() -> UserContextHolder.callAs(
                        authenticated, () -> intercept(new ConnectorTenantGuardInterceptor(), invocation)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("authenticated tenant");
    }

    private static Object intercept(ConnectorTenantGuardInterceptor interceptor, Invocation invocation) {
        try {
            return interceptor.intercept(invocation);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
    }
}
