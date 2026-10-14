package io.aigoodle.model.provider.qwen;

import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.video.*;
import java.util.*;

/** DashScope's asynchronous text-to-video protocol for Wan 2.6. */
public final class WanVideoModel implements VideoModel {
    private final VideoHttpTransport http;
    private final String model;

    public WanVideoModel(ModelEndpoint endpoint) {
        model = endpoint.getModelName();
        if (!Set.of("wan2.6-t2v", "wan2.6-t2v-us").contains(model))
            throw new IllegalArgumentException("This adapter supports Wan 2.6 text-to-video models");
        String base = endpoint.resolveBaseUrl("https://dashscope.aliyuncs.com")
                .replaceAll("/compatible-mode/v1/?$", "")
                .replaceAll("/api/v1/?$", "");
        http = new VideoHttpTransport(base + "/api/v1", endpoint.getApiKey());
    }

    @Override
    public String submit(VideoRequest request) {
        if (request.prompt().length() > 1500) throw new IllegalArgumentException("Wan prompt exceeds 1500 characters");
        var result = http.call(
                "POST",
                "services/aigc/video-generation/video-synthesis",
                Map.of("model", model, "input", Map.of("prompt", request.prompt()), "parameters", request.parameters()),
                Map.of("X-DashScope-Async", "enable"));
        if (!(result.get("output") instanceof Map<?, ?> output) || !(output.get("task_id") instanceof String taskId))
            throw new IllegalStateException("Video provider returned no task ID");
        return VideoHttpTransport.taskId(taskId);
    }

    @Override
    public VideoResult query(String taskId) {
        var reply = http.call("GET", "tasks/" + VideoHttpTransport.taskId(taskId), null, Map.of());
        if (!(reply.get("output") instanceof Map<?, ?> output))
            throw new IllegalStateException("Video task response has no output");
        var status =
                switch (String.valueOf(output.get("task_status"))) {
                    case "PENDING" -> VideoResult.Status.QUEUED;
                    case "RUNNING" -> VideoResult.Status.RUNNING;
                    case "SUCCEEDED" -> VideoResult.Status.SUCCEEDED;
                    case "FAILED" -> VideoResult.Status.FAILED;
                    case "CANCELED" -> VideoResult.Status.CANCELLED;
                    case "UNKNOWN" -> VideoResult.Status.EXPIRED;
                    default -> throw new IllegalStateException("Unknown video task status");
                };
        String url = output.get("video_url") instanceof String value ? value : null;
        if (status == VideoResult.Status.SUCCEEDED && (url == null || url.isBlank()))
            throw new IllegalStateException("Video provider returned no video URL");
        return new VideoResult(
                status,
                url,
                output.get("code") instanceof String code ? code : null,
                reply.get("usage") instanceof Map<?, ?> usage
                        ? io.aigoodle.common.util.JsonUtils.convert(usage, Map.class)
                        : Map.of());
    }

    @Override
    public boolean cancel(String taskId) {
        return query(taskId).terminal();
    }

    @Override
    public int maxPromptLength() {
        return 1500;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                Map.of(
                        "duration",
                                model.endsWith("-us")
                                        ? Map.of("type", "integer", "enum", List.of(5, 10), "title", "时长（秒）")
                                        : Map.of("type", "integer", "minimum", 2, "maximum", 15, "title", "时长（秒）"),
                        "size",
                                Map.of(
                                        "type",
                                        "string",
                                        "enum",
                                        List.of(
                                                "1280*720",
                                                "720*1280",
                                                "960*960",
                                                "1088*832",
                                                "832*1088",
                                                "1920*1080",
                                                "1080*1920",
                                                "1440*1440",
                                                "1632*1248",
                                                "1248*1632"),
                                        "title",
                                        "视频尺寸"),
                        "watermark", Map.of("type", "boolean", "title", "水印"),
                        "prompt_extend", Map.of("type", "boolean", "title", "扩写提示词"),
                        "seed", Map.of("type", "integer", "minimum", 0, "maximum", 2147483647, "title", "随机种子")));
    }
}
