package io.aigoodle.knowledge.milvus;

import io.aigoodle.knowledge.config.GoodleKnowledgeAutoConfiguration;
import io.aigoodle.knowledge.index.VectorStoreFactory;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers a Milvus-backed {@link VectorStoreFactory} when
 * {@code spring-agent.knowledge.vector-store=milvus}. Builds a
 * {@link MilvusServiceClient} from properties unless the app provides its own bean.
 */
@AutoConfiguration(before = GoodleKnowledgeAutoConfiguration.class)
@ConditionalOnClass({MilvusVectorStore.class, MilvusServiceClient.class})
@ConditionalOnProperty(prefix = "spring-agent.knowledge", name = "vector-store", havingValue = "milvus")
@EnableConfigurationProperties(MilvusStoreProperties.class)
public class GoodleMilvusStoreAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public MilvusServiceClient agentMilvusServiceClient(MilvusStoreProperties properties) {
        ConnectParam.Builder builder = ConnectParam.newBuilder().withDatabaseName(properties.getDatabaseName());
        if (properties.getUri() != null && !properties.getUri().isBlank()) {
            builder.withUri(properties.getUri());
        } else {
            builder.withHost(properties.getHost()).withPort(properties.getPort());
        }
        if (properties.getToken() != null && !properties.getToken().isBlank()) {
            builder.withToken(properties.getToken());
        } else if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            builder.withAuthorization(
                    properties.getUsername(), properties.getPassword() == null ? "" : properties.getPassword());
        }
        return new MilvusServiceClient(builder.build());
    }

    @Bean
    @ConditionalOnMissingBean(VectorStoreFactory.class)
    public VectorStoreFactory milvusVectorStoreFactory(
            MilvusServiceClient milvusClient, MilvusStoreProperties properties) {
        return new MilvusVectorStoreFactory(milvusClient, properties);
    }
}
