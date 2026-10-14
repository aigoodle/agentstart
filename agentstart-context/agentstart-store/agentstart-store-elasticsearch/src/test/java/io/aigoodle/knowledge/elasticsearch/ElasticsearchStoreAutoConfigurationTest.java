package io.aigoodle.knowledge.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import io.aigoodle.knowledge.index.VectorStoreFactory;
import io.aigoodle.knowledge.service.DatasetService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ElasticsearchStoreAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GoodleElasticsearchStoreAutoConfiguration.class))
            .withBean(Rest5Client.class, () -> mock(Rest5Client.class))
            .withBean(DatasetService.class, () -> mock(DatasetService.class));

    @Test
    void selectedAdapterRegistersAfterHostInfrastructure() {
        runner.withPropertyValues("spring-agent.knowledge.vector-store=elasticsearch")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(VectorStoreFactory.class);
                    assertThat(context.getBean(VectorStoreFactory.class))
                            .isInstanceOf(ElasticsearchVectorStoreFactory.class);
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
        runner.withPropertyValues("spring-agent.knowledge.vector-store=elasticsearch")
                .withBean(VectorStoreFactory.class, () -> host)
                .run(context ->
                        assertThat(context.getBean(VectorStoreFactory.class)).isSameAs(host));
    }
}
