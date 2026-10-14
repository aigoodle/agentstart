package io.aigoodle.plugin.seedance;

import io.aigoodle.plugin.seedance.mapper.SeedanceSubmissionMapper;
import java.io.IOException;
import java.net.URI;
import javax.sql.DataSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@MapperScan("io.aigoodle.plugin.seedance.mapper")
public class SeedanceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public SeedanceSubmissionStore seedanceSubmissionStore(
            DataSource source,
            SeedanceSubmissionMapper mapper,
            PlatformTransactionManager transactionManager,
            @Value("${spring-agent.plugins.seedance.initialize-schema:true}") boolean initialize) {
        if (initialize)
            new ResourceDatabasePopulator(new ClassPathResource("db/plugin-seedance-schema.sql")).execute(source);
        return new SeedanceSubmissionStore(mapper, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public SeedanceVideoPlugin seedanceVideoPlugin(
            SeedanceSubmissionStore store,
            @Value("${spring-agent.plugins.seedance.base-url:https://ark.cn-beijing.volces.com/api/v3}") String baseUrl)
            throws IOException {
        return new SeedanceVideoPlugin(store, URI.create(baseUrl));
    }
}
