package io.aigoodle.plugin.video;

import io.aigoodle.model.service.CredentialCodec;
import io.aigoodle.model.video.VideoModelService;
import io.aigoodle.plugin.video.mapper.VideoTaskMapper;
import java.io.IOException;
import javax.sql.DataSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@org.springframework.scheduling.annotation.EnableScheduling
@MapperScan("io.aigoodle.plugin.video.mapper")
public class VideoPluginAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public VideoTaskStore videoTaskStore(
            DataSource source,
            VideoTaskMapper mapper,
            PlatformTransactionManager transactionManager,
            @Value("${spring-agent.plugins.video.initialize-schema:true}") boolean initialize) {
        if (initialize) {
            new ResourceDatabasePopulator(new ClassPathResource("db/plugin-video-schema.sql")).execute(source);
            ensurePollIndex(source);
        }
        return new VideoTaskStore(mapper, transactionManager);
    }

    private static void ensurePollIndex(DataSource source) {
        if (hasPollIndex(source)) return;
        try {
            new ResourceDatabasePopulator(new ClassPathResource("db/plugin-video-index.sql")).execute(source);
        } catch (org.springframework.dao.DataAccessException concurrentCreation) {
            if (!hasPollIndex(source)) throw concurrentCreation;
        }
    }

    private static boolean hasPollIndex(DataSource source) {
        try (var connection = source.getConnection()) {
            var metadata = connection.getMetaData();
            String table = metadata.storesUpperCaseIdentifiers() ? "PLUGIN_VIDEO_TASK" : "plugin_video_task";
            try (var indexes =
                    metadata.getIndexInfo(connection.getCatalog(), connection.getSchema(), table, false, false)) {
                while (indexes.next())
                    if ("idx_plugin_video_poll".equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return true;
                return false;
            }
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("Cannot inspect video task indexes", failure);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public VideoTaskService videoTaskService(VideoTaskStore store, VideoModelService models, CredentialCodec codec) {
        return new VideoTaskService(store, models, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    public VideoGenerationPlugin videoGenerationPlugin(VideoTaskService tasks) throws IOException {
        return new VideoGenerationPlugin(tasks);
    }

    @Bean
    public VideoRecovery videoTaskRecovery(VideoTaskService tasks) {
        return new VideoRecovery(tasks);
    }

    public record VideoRecovery(VideoTaskService tasks) {
        @Scheduled(fixedDelayString = "${spring-agent.plugins.video.poll-delay-ms:5000}")
        public void recover() {
            tasks.recover();
        }
    }
}
