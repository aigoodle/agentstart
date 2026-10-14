package io.aigoodle.knowledge.elasticsearch;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import io.aigoodle.knowledge.config.GoodleKnowledgeAutoConfiguration;
import io.aigoodle.knowledge.index.VectorStoreFactory;
import io.aigoodle.knowledge.service.DatasetService;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.message.BasicHeader;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers an Elasticsearch-backed {@link VectorStoreFactory} when
 * {@code spring-agent.knowledge.vector-store=elasticsearch}. Constructs a low-level
 * {@link Rest5Client} from properties unless the app provides its own bean.
 */
@AutoConfiguration(before = GoodleKnowledgeAutoConfiguration.class)
@ConditionalOnClass({ElasticsearchVectorStore.class, Rest5Client.class})
@ConditionalOnProperty(prefix = "spring-agent.knowledge", name = "vector-store", havingValue = "elasticsearch")
@EnableConfigurationProperties(ElasticsearchStoreProperties.class)
public class GoodleElasticsearchStoreAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public Rest5Client agentElasticsearchRestClient(ElasticsearchStoreProperties properties) {
        Rest5ClientBuilder builder = Rest5Client.builder(
                properties.getUris().stream().map(java.net.URI::create).toList());
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.setDefaultHeaders(new org.apache.hc.core5.http.Header[] {
                new BasicHeader("Authorization", "ApiKey " + properties.getApiKey())
            });
        } else if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(
                    new AuthScope(null, -1),
                    new UsernamePasswordCredentials(
                            properties.getUsername(),
                            properties.getPassword() == null
                                    ? new char[0]
                                    : properties.getPassword().toCharArray()));
            builder.setHttpClientConfigCallback(hc -> hc.setDefaultCredentialsProvider(credentials));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStoreFactory.class)
    public VectorStoreFactory elasticsearchVectorStoreFactory(
            Rest5Client restClient, ElasticsearchStoreProperties properties) {
        return new ElasticsearchVectorStoreFactory(restClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean(ElasticsearchSparseRetriever.class)
    public ElasticsearchSparseRetriever elasticsearchSparseRetriever(
            Rest5Client restClient, ElasticsearchStoreProperties properties, DatasetService datasetService) {
        return new ElasticsearchSparseRetriever(restClient, properties, datasetService);
    }
}
