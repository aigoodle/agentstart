package io.aigoodle.plugin.video;

import io.aigoodle.plugin.*;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;

public final class VideoGenerationPlugin implements AsyncPlugin {
    private final PluginManifest manifest;
    private final VideoTaskService tasks;

    public VideoGenerationPlugin(VideoTaskService tasks) throws IOException {
        this.tasks = tasks;
        manifest = PluginManifests.load(new ClassPathResource("plugins/video/manifest.yaml"));
    }

    @Override
    public PluginManifest manifest() {
        return manifest;
    }

    @Override
    public PluginResult execute(PluginInvocation invocation, PluginContext context) {
        return tasks.submit(invocation, context);
    }

    @Override
    public PluginResult query(PluginInvocation invocation, PluginTask task, PluginContext context) {
        return tasks.query(task, context);
    }

    @Override
    public PluginResult cancel(PluginInvocation invocation, PluginTask task, PluginContext context) {
        return tasks.cancel(task, context);
    }
}
