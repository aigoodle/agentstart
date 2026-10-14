package io.aigoodle.knowledge.milvus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.aigoodle.knowledge.index.VectorStoreFactory;
import io.milvus.client.MilvusServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MilvusStoreAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GoodleMilvusStoreAutoConfiguration.class))
            .withBean(MilvusServiceClient.class, () -> mock(MilvusServiceClient.class));

    @Test
    void selectedAdapterRegistersAfterHostInfrastructure() {
        runner.withPropertyValues("spring-agent.knowledge.vector-store=milvus").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(VectorStoreFactory.class);
            assertThat(context.getBean(VectorStoreFactory.class)).isInstanceOf(MilvusVectorStoreFactory.class);
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
        runner.withPropertyValues("spring-agent.knowledge.vector-store=milvus")
                .withBean(VectorStoreFactory.class, () -> host)
                .run(context ->
                        assertThat(context.getBean(VectorStoreFactory.class)).isSameAs(host));
    }
}
