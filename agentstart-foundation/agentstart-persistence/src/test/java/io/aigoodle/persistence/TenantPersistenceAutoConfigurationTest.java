package io.aigoodle.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TenantPersistenceAutoConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantPersistenceAutoConfiguration.class));

    @Test
    void installsTheGuardByDefault() {
        context.run(result -> assertThat(result).hasSingleBean(TenantSqlGuardInterceptor.class));
    }

    @Test
    void letsEmbeddedHostsDisableOrReplaceTheGuard() {
        context.withPropertyValues("spring-agent.persistence.tenant-guard.enabled=false")
                .run(result -> assertThat(result).doesNotHaveBean(TenantSqlGuardInterceptor.class));
        TenantSqlGuardInterceptor replacement = new TenantSqlGuardInterceptor(TenantGuardTables.CONNECTOR);
        context.withBean(TenantSqlGuardInterceptor.class, () -> replacement)
                .run(result -> assertThat(result.getBean(TenantSqlGuardInterceptor.class))
                        .isSameAs(replacement));
    }

    @Test
    void customTablesExtendRatherThanReplaceBuiltInProtection() {
        context.withPropertyValues("spring-agent.persistence.tenant-guard.additional-tables[0]=host_business_record")
                .run(result -> {
                    TenantPersistenceProperties properties = result.getBean(TenantPersistenceProperties.class);
                    assertThat(properties.protectedTables())
                            .contains(
                                    "host_business_record",
                                    "agent_channel_event",
                                    "goodle_model",
                                    "goodle_documents",
                                    "goodle_document_segments",
                                    "goodle_document_ingest_queue",
                                    "goodle_dataset_query",
                                    "goodle_memories",
                                    "goodle_app_triggers",
                                    "goodle_trigger_invocations",
                                    "goodle_agent_versions",
                                    "goodle_app_annotations",
                                    "goodle_app_annotation_settings",
                                    "goodle_conversations",
                                    "goodle_runs",
                                    "goodle_run_events",
                                    "goodle_api_tokens",
                                    "goodle_app_sites",
                                    "goodle_tags",
                                    "goodle_tag_bindings");
                });
    }

    @Test
    void tableMatchingUsesIdentifierBoundaries() {
        assertThatCode(() -> TenantSqlGuardInterceptor.requireTenantConstraint(
                        "UPDATE goodle_model_provider SET enabled = ? WHERE id = ?", java.util.Set.of("goodle_model")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> TenantSqlGuardInterceptor.requireTenantConstraint(
                        "UPDATE goodle_model SET enabled = ? WHERE id = ?", java.util.Set.of("goodle_model")))
                .isInstanceOf(SecurityException.class);
        assertThatCode(() -> TenantSqlGuardInterceptor.requireTenantConstraint(
                        "UPDATE public.goodle_model SET enabled = ? WHERE tenant_id = ? AND id = ?",
                        java.util.Set.of("goodle_model")))
                .doesNotThrowAnyException();
    }
}
