package io.aigoodle.model.provider;

import io.aigoodle.model.enums.ModelType;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * SPI implemented by every model vendor integration (OpenAI-compatible, Ollama, ...).
 * <p>
 * A provider is a stateless factory: given a resolved {@link ModelEndpoint} it builds
 * the concrete Spring AI model object. Caching of built instances is the responsibility
 * of the {@code ModelInstanceFactory}, not the provider.
 * <p>
 * Implementations are discovered as Spring beans, so third parties can publish a jar
 * with their own {@code ModelProvider} bean and it will be picked up automatically.
 */
public interface ModelProvider {

    /** Unique provider key, e.g. {@code "openai"}, {@code "deepseek"}, {@code "ollama"}. */
    String getName();

    /**
     * Runtime implementation key — how a DB-driven provider definition points at
     * the Java bean that should build ChatModel / EmbeddingModel for it. Defaults
     * to {@link #getName()}, so built-in providers work with zero extra wiring.
     * <p>
     * A "generic OpenAI-compatible" bean can advertise a stable key like
     * {@code "openai_compatible"} so external DB rows can reuse it to point at
     * a new vendor endpoint without shipping additional Java code.
     */
    default String implementationKey() {
        return getName();
    }

    /** Human friendly label for UIs. */
    default String getLabel() {
        return getName();
    }

    /** Model capability categories this provider can serve. */
    Set<ModelType> supportedModelTypes();

    default boolean supports(ModelType type) {
        return supportedModelTypes().contains(type);
    }

    /** Fields required to configure this provider (for UI rendering + validation). */
    CredentialSchema credentialSchema();

    /** Models this provider knows about out of the box. */
    default List<PredefinedModel> predefinedModels() {
        return List.of();
    }

    /**
     * Parameter rules the provider recommends for {@code modelType} when the caller
     * has no {@link PredefinedModel} override — e.g. temperature/topP/maxTokens for
     * an LLM, dimensions for an embedding. Used by the UI to render the
     * per-model parameter drawer without hitting the network.
     */
    default List<ModelParameterRule> defaultParameterRules(ModelType modelType) {
        return switch (modelType) {
            case LLM -> List.of(
                    ModelParameterRule.floatRule("temperature", "Temperature", 0d, 2d, 0.7d, "较高值输出更随机，较低值更聚焦"),
                    ModelParameterRule.floatRule("topP", "Top P", 0d, 1d, 1d, "核采样：只从累积概率 top P 的词里采样"),
                    ModelParameterRule.intRule(
                            "maxTokens", "最大生成 Tokens", 1, 32000, 2048, "单次输出上限；模型上下文一般是 context - maxTokens"),
                    ModelParameterRule.floatRule(
                            "presencePenalty", "Presence Penalty", -2d, 2d, 0d, "对已出现过的话题施加惩罚（0 = 关闭）"),
                    ModelParameterRule.floatRule(
                            "frequencyPenalty", "Frequency Penalty", -2d, 2d, 0d, "对重复词施加惩罚（0 = 关闭）"));
            case TEXT_EMBEDDING -> List.of(ModelParameterRule.intRule(
                    "dimensions", "输出向量维度", 8, 8192, 1536, "留空使用模型默认维度；某些模型可通过 dimensions 参数降维"));
            case RERANK -> List.of(
                    ModelParameterRule.intRule("topK", "Top K", 1, 100, 5, "重排后返回的最大文档数"),
                    ModelParameterRule.floatRule("scoreThreshold", "分数阈值", 0d, 1d, 0d, "低于此分数的文档会被过滤（0 = 不过滤）"));
            default -> List.of();
        };
    }

    /**
     * Query the vendor's live model catalog for the given credentials. Providers whose
     * vendor exposes a {@code /models}-style endpoint (all OpenAI-compatible ones) return
     * real results; others fall back to {@link #predefinedModels()}.
     * <p>
     * Called only when the front end wants to enumerate what an API key actually unlocks
     * (Dify-parity "填入 key 自动拉取"). Implementations MUST NOT persist or cache — the
     * caller decides freshness.
     */
    default List<RemoteModel> listRemoteModels(ModelEndpoint endpoint) {
        return predefinedModels().stream().map(RemoteModel::fromPredefined).toList();
    }

    /**
     * Whether {@link #listRemoteModels} makes a real network call. Drives the UI hint
     * "该供应商支持自动拉取模型列表". Default: false (predefined fallback).
     */
    default boolean supportsRemoteModelListing() {
        return false;
    }

    /**
     * Build a chat (LLM) model. Implementations should throw
     * {@link UnsupportedOperationException} if {@link ModelType#LLM} is unsupported.
     */
    ChatModel createChatModel(ModelEndpoint endpoint);

    /**
     * Build a text-embedding model. Implementations should throw
     * {@link UnsupportedOperationException} if embeddings are unsupported.
     */
    EmbeddingModel createEmbeddingModel(ModelEndpoint endpoint);

    /** Asynchronous video generation; providers opt in by advertising VIDEO. */
    default io.aigoodle.model.video.VideoModel createVideoModel(ModelEndpoint endpoint) {
        throw new UnsupportedOperationException(getName() + " does not support video generation");
    }
}
