package io.aigoodle.skill.config;

import io.aigoodle.skill.mapper.SkillMapper;
import io.aigoodle.skill.service.SkillResolver;
import io.aigoodle.skill.service.SkillService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = io.aigoodle.persistence.TenantPersistenceAutoConfiguration.class)
@MapperScan("io.aigoodle.skill.mapper")
public class GoodleSkillAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public SkillService skillService(SkillMapper mapper) {
        return new SkillService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(SkillResolver.class)
    public SkillResolver skillResolver(SkillService service) {
        return service;
    }
}
