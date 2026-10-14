package io.aigoodle.model.provider.volcengine;

import io.aigoodle.model.provider.ModelEndpoint;
import io.aigoodle.model.video.*;
import java.util.*;

/** Ark transport only; platform owns model credentials, durable tasks and orchestration. */
public final class SeedanceVideoModel implements VideoModel {
    private final VideoHttpTransport http;
    private final String model;

    public SeedanceVideoModel(ModelEndpoint endpoint) {
        http = new VideoHttpTransport(
                endpoint.resolveBaseUrl(VolcengineArkModelProvider.DEFAULT_BASE_URL), endpoint.getApiKey());
        // A provider-wide chat endpointId must not silently select a different video model.
        model = endpoint.getModelName();
    }

    @Override
    public String submit(VideoRequest request) {
        if (request.prompt().length() > 10000)
            throw new IllegalArgumentException("Seedance prompt exceeds 10000 characters");
        var body = new LinkedHashMap<String, Object>(request.parameters());
        body.put("model", model);
        body.put("content", List.of(Map.of("type", "text", "text", request.prompt())));
        var response = http.call("POST", "contents/generations/tasks", body, Map.of());
        if (!(response.get("id") instanceof String taskId))
            throw new IllegalStateException("Video provider returned no task ID");
        return VideoHttpTransport.taskId(taskId);
    }

    @Override
    public VideoResult query(String taskId) {
        var reply = http.call("GET", "contents/generations/tasks/" + VideoHttpTransport.taskId(taskId), null, Map.of());
        var status =
                VideoResult.Status.valueOf(String.valueOf(reply.get("status")).toUpperCase(Locale.ROOT));
        var content = reply.get("content") instanceof Map<?, ?> map ? map : Map.of();
        String url = content.get("video_url") instanceof String value ? value : null;
        if (status == VideoResult.Status.SUCCEEDED && (url == null || url.isBlank()))
            throw new IllegalStateException("Video provider returned no video URL");
        return new VideoResult(
                status,
                url,
                reply.get("error") instanceof Map<?, ?> error ? String.valueOf(error.get("code")) : null,
                reply.get("usage") instanceof Map<?, ?> usage
                        ? io.aigoodle.common.util.JsonUtils.convert(usage, Map.class)
                        : Map.of());
    }

    @Override
    public boolean cancel(String taskId) {
        var state = query(taskId);
        if (state.terminal()) return true;
        if (state.status() != VideoResult.Status.QUEUED) return false;
        http.call("DELETE", "contents/generations/tasks/" + VideoHttpTransport.taskId(taskId), null, Map.of());
        return query(taskId).terminal();
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
                        "duration", Map.of("type", "integer", "minimum", 2, "maximum", 12, "title", "时长（秒）"),
                        "ratio",
                                Map.of(
                                        "type",
                                        "string",
                                        "enum",
                                        List.of("16:9", "9:16", "1:1", "4:3", "3:4", "21:9", "adaptive"),
                                        "title",
                                        "画面比例"),
                        "resolution",
                                Map.of("type", "string", "enum", List.of("480p", "720p", "1080p"), "title", "分辨率"),
                        "generate_audio", Map.of("type", "boolean", "title", "生成音频"),
                        "watermark", Map.of("type", "boolean", "title", "水印"),
                        "seed", Map.of("type", "integer", "minimum", -1, "maximum", 4294967295L, "title", "随机种子")));
    }
}
