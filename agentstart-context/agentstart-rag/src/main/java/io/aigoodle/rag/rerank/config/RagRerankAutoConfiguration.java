package io.aigoodle.rag.rerank.config;

import io.aigoodle.rag.rerank.ScoreReranker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class RagRerankAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ScoreReranker.class)
    ScoreReranker scoreReranker() {
        return new ScoreReranker();
    }
}
