package io.aigoodle.knowledge.pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.aigoodle.knowledge.index.VectorStoreFactory;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PgVectorStoreAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JdbcTemplateAutoConfiguration.class, GoodlePgVectorStoreAutoConfiguration.class))
            .withBean(DataSource.class, () -> mock(DataSource.class));

    @Test
    void selectedAdapterRegistersAfterHostInfrastructure() {
        runner.withPropertyValues("spring-agent.knowledge.vector-store=pgvector")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(VectorStoreFactory.class);
                    assertThat(context.getBean(VectorStoreFactory.class)).isInstanceOf(PgVectorStoreFactory.class);
                });
    }

    @Test
    void unselectedAdapterDoesNotReplaceDefaultStore() {
        runner.withPropertyValues("spring-agent.knowledge.vector-store=jdbc")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(VectorStoreFactory.class));
    }

    @Test
    void hostFactoryTakesPrecedence() {
        var host = mock(VectorStoreFactory.class);
        runner.withPropertyValues("spring-agent.knowledge.vector-store=pgvector")
                .withBean(VectorStoreFactory.class, () -> host)
                .run(context ->
                        assertThat(context.getBean(VectorStoreFactory.class)).isSameAs(host));
    }
}
