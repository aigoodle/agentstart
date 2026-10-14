package io.aigoodle.plugin.example;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ProductVideoAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ProductVideoPlugin productVideoPlugin() {
        return new ProductVideoPlugin();
    }
}
