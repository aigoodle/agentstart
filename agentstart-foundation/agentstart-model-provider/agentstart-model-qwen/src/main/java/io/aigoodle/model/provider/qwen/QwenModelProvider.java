package io.aigoodle.model.provider.qwen;

import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.*;
import io.aigoodle.model.provider.builtin.*;
import io.aigoodle.model.video.VideoModel;
import java.util.*;

/** Preserves Qwen chat/embedding support and adds Wan video through the same provider identity. */
public final class QwenModelProvider extends OpenAiCompatibleModelProvider {
    public QwenModelProvider() {
        super("qwen", "阿里百炼 / Qwen / Wan", "https://dashscope.aliyuncs.com/compatible-mode/v1", models());
    }

    private static List<PredefinedModel> models() {
        var models = new ArrayList<>(BuiltinModelProviders.qwen().predefinedModels());
        models.add(PredefinedModel.builder()
                .model("wan2.6-t2v")
                .label("Wan 2.6 文生视频")
                .modelType(ModelType.VIDEO)
                .build());
        return models;
    }

    @Override
    public Set<ModelType> supportedModelTypes() {
        return Set.of(ModelType.LLM, ModelType.TEXT_EMBEDDING, ModelType.VIDEO);
    }

    @Override
    public VideoModel createVideoModel(ModelEndpoint endpoint) {
        return new WanVideoModel(endpoint);
    }
}
