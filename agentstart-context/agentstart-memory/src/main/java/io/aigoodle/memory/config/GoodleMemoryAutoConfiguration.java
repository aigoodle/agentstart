package io.aigoodle.memory.config;

import io.aigoodle.memory.LayeredMemoryManager;
import io.aigoodle.memory.MemoryMaintenance;
import io.aigoodle.memory.MemoryManager;
import io.aigoodle.memory.MemoryStore;
import io.aigoodle.memory.extraction.MemoryExtractor;
import io.aigoodle.memory.extraction.RuleBasedMemoryExtractor;
import io.aigoodle.memory.mapper.MemoryMapper;
import io.aigoodle.memory.store.JdbcMemoryStore;
import java.util.List;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = io.aigoodle.persistence.TenantPersistenceAutoConfiguration.class)
@EnableConfigurationProperties(MemoryProperties.class)
@MapperScan("io.aigoodle.memory.mapper")
public class GoodleMemoryAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public MemoryStore memoryStore(MemoryMapper mapper) {
        return new JdbcMemoryStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryExtractor ruleBasedMemoryExtractor() {
        return new RuleBasedMemoryExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryManager memoryManager(
            MemoryStore store, MemoryProperties properties, List<MemoryExtractor> extractors) {
        return new LayeredMemoryManager(store, properties, extractors);
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryMaintenance memoryMaintenance(MemoryStore store) {
        return new MemoryMaintenance(store);
    }
}
