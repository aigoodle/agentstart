package io.aigoodle.persistence;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = MybatisPlusAutoConfiguration.class)
@ConditionalOnClass(Interceptor.class)
@EnableConfigurationProperties(TenantPersistenceProperties.class)
public class TenantPersistenceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean(TenantSqlGuardInterceptor.class)
    @ConditionalOnProperty(
            prefix = "spring-agent.persistence.tenant-guard",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public TenantSqlGuardInterceptor tenantSqlGuardInterceptor(TenantPersistenceProperties properties) {
        return new TenantSqlGuardInterceptor(properties.protectedTables());
    }
}
