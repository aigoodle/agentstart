package io.aigoodle.model.provider.builtin;

import io.aigoodle.model.enums.ModelFeature;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.provider.PredefinedModel;
import java.util.List;
import java.util.Set;

/**
 * Factory for the out-of-the-box OpenAI-compatible provider presets. Each preset is
 * the same {@link OpenAiCompatibleModelProvider} with a different name, default base
 * url and shipped model list — which is all that distinguishes these vendors.
 */
public final class BuiltinModelProviders {

    private static final Set<ModelFeature> CHAT_TOOLS =
            Set.of(ModelFeature.STREAM, ModelFeature.TOOL_CALL, ModelFeature.STREAM_TOOL_CALL);

    private BuiltinModelProviders() {}

    public static List<OpenAiCompatibleModelProvider> openAiCompatible() {
        return List.of(openai(), deepseek(), zhipu(), moonshot(), qwen(), volcengine(), siliconflow(), custom());
    }

    public static OpenAiCompatibleModelProvider custom() {
        return new OpenAiCompatibleModelProvider("openai-compatible", "自定义 · OpenAI 兼容接口", null);
    }

    private static PredefinedModel llm(String model, int ctx) {
        return PredefinedModel.builder()
                .model(model)
                .label(model)
                .modelType(ModelType.LLM)
                .features(CHAT_TOOLS)
                .contextLength(ctx)
                .build();
    }

    private static PredefinedModel llm(String model) {
        return PredefinedModel.builder()
                .model(model)
                .label(model)
                .modelType(ModelType.LLM)
                .features(CHAT_TOOLS)
                .build();
    }

    private static PredefinedModel embed(String model, int dim) {
        return PredefinedModel.builder()
                .model(model)
                .label(model)
                .modelType(ModelType.TEXT_EMBEDDING)
                .dimensions(dim)
                .build();
    }

    public static OpenAiCompatibleModelProvider openai() {
        return new OpenAiCompatibleModelProvider(
                "openai",
                "OpenAI",
                "https://api.openai.com",
                List.of(
                        llm("gpt-4o", 128_000),
                        llm("gpt-4o-mini", 128_000),
                        llm("gpt-4.1", 1_000_000),
                        llm("gpt-4.1-mini", 1_000_000),
                        embed("text-embedding-3-small", 1536),
                        embed("text-embedding-3-large", 3072)));
    }

    public static OpenAiCompatibleModelProvider deepseek() {
        return new OpenAiCompatibleModelProvider(
                "deepseek",
                "DeepSeek",
                "https://api.deepseek.com",
                List.of(llm("deepseek-chat", 64_000), llm("deepseek-reasoner", 64_000)));
    }

    public static OpenAiCompatibleModelProvider zhipu() {
        return new OpenAiCompatibleModelProvider(
                "zhipu",
                "Zhipu AI",
                "https://open.bigmodel.cn/api/paas/v4",
                List.of(
                        llm("glm-5.3"),
                        llm("glm-5.3-flash"),
                        llm("glm-4-plus", 128_000),
                        llm("glm-4-flash", 128_000),
                        llm("glm-4-air", 128_000),
                        embed("embedding-3", 2048)));
    }

    public static OpenAiCompatibleModelProvider moonshot() {
        return new OpenAiCompatibleModelProvider(
                "moonshot",
                "Kimi",
                "https://api.moonshot.cn/v1",
                List.of(
                        llm("kimi-k3"),
                        llm("kimi-k2.7-code"),
                        llm("kimi-k2.7-code-highspeed"),
                        llm("kimi-k2.6"),
                        llm("moonshot-v1-8k", 8_000),
                        llm("moonshot-v1-32k", 32_000),
                        llm("moonshot-v1-128k", 128_000))) {
            @Override
            public Set<ModelType> supportedModelTypes() {
                return Set.of(ModelType.LLM);
            }
        };
    }

    public static OpenAiCompatibleModelProvider qwen() {
        // DashScope's OpenAI-compat /v1/models endpoint returns a limited subset
        // (usually only qwen-turbo / qwen-plus / qwen-max), so we ship a rich
        // built-in catalog that mirrors what dashscope.aliyuncs.com actually offers.
        return new OpenAiCompatibleModelProvider(
                "qwen",
                "Qwen",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                List.of(
                        // Generation-3 series (latest)
                        llm("qwen3-235b-a22b-instruct-2507", 128_000),
                        llm("qwen3-30b-a3b-instruct-2507", 128_000),
                        llm("qwen3-32b", 128_000),
                        llm("qwen3-14b", 128_000),
                        llm("qwen3-8b", 128_000),
                        // Generation-2.5 (widely used)
                        llm("qwen2.5-72b-instruct", 128_000),
                        llm("qwen2.5-32b-instruct", 128_000),
                        llm("qwen2.5-14b-instruct", 128_000),
                        llm("qwen2.5-7b-instruct", 128_000),
                        llm("qwen2.5-coder-32b-instruct", 128_000),
                        // Aliased "long / plus / max / turbo" endpoints
                        llm("qwen-plus", 128_000),
                        llm("qwen-plus-latest", 128_000),
                        llm("qwen-max", 32_000),
                        llm("qwen-max-latest", 32_000),
                        llm("qwen-turbo", 1_000_000),
                        llm("qwen-turbo-latest", 1_000_000),
                        llm("qwen-long", 10_000_000),
                        // Vision-language models
                        llm("qwen-vl-plus", 32_000),
                        llm("qwen-vl-max", 32_000),
                        llm("qwen2.5-vl-72b-instruct", 128_000),
                        // Embedding models — all DashScope embed families
                        embed("text-embedding-v1", 1536),
                        embed("text-embedding-v2", 1536),
                        embed("text-embedding-v3", 1024),
                        embed("text-embedding-v4", 1024),
                        embed("text-embedding-async-v1", 1536),
                        embed("text-embedding-async-v2", 1536)));
    }

    public static OpenAiCompatibleModelProvider volcengine() {
        // Volcengine Ark uses an endpoint id as the "model" name.
        return new OpenAiCompatibleModelProvider(
                "volcengine", "Volcengine Ark", "https://ark.cn-beijing.volces.com/api/v3", List.of());
    }

    public static OpenAiCompatibleModelProvider siliconflow() {
        return new OpenAiCompatibleModelProvider(
                "siliconflow",
                "SiliconFlow",
                "https://api.siliconflow.cn/v1",
                List.of(
                        llm("Qwen/Qwen2.5-72B-Instruct", 32_000),
                        llm("deepseek-ai/DeepSeek-V3", 64_000),
                        embed("BAAI/bge-m3", 1024)));
    }
}
