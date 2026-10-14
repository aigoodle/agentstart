package io.aigoodle.workflow.node.builtin;

import io.aigoodle.common.context.UserContextHolder;
import io.aigoodle.model.entity.ModelEntity;
import io.aigoodle.model.enums.ModelType;
import io.aigoodle.model.options.ChatOptionsFactory;
import io.aigoodle.model.service.ModelService;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.node.ExecutionContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Resolves a {@link ChatClient} from an LLM-shaped workflow node.
 *
 * <p>The console saves the picked model as a nested {@code model} object on the
 * node's data — {@code {"modelName": "qwen3.6-plus", "modelProvider": "qwen"}}.
 * Older node configs used a flat top-level {@code modelId} carrying an
 * {@code agent_model.id} UUID; both shapes are still accepted so persisted
 * graphs keep loading.</p>
 */
final class NodeModelResolver {

    private NodeModelResolver() {}

    static ChatClient resolve(NodeDef node, ExecutionContext context, ModelService modelService) {
        String tenantId = tenantOf(context);
        return context.withResourceTenant(
                () -> modelService.getChatClient(tenantId, resolveEntityId(node, context, modelService)));
    }

    /**
     * Same resolution rules as {@link #resolve} but returns the raw
     * {@link ChatModel} instead of the fluent client. Used by the LLM node's
     * streaming path: {@code ChatClient.prompt().stream()} in Spring AI 1.1.2
     * wraps the response flux with {@code MessageAggregator} for tool-call
     * detection + observation callbacks, which buffers upstream tokens before
     * they reach downstream subscribers. Calling {@code chatModel.stream(prompt)}
     * directly skips that whole layer and gives us the raw chunk-by-chunk
     * emission the LLM API actually produces.
     */
    static ChatModel resolveModel(NodeDef node, ExecutionContext context, ModelService modelService) {
        String tenantId = tenantOf(context);
        return context.withResourceTenant(
                () -> modelService.getChatModel(tenantId, resolveEntityId(node, context, modelService)));
    }

    /**
     * Walk the node config, find whichever locator the designer wrote, and
     * hydrate the matching {@code agent_model} row (creating one via
     * {@link ModelService#findOrMaterialize} when only the provider+name pair
     * is given). Returns the resolved entity's id so callers can pick between
     * {@link ModelService#getChatClient(String) getChatClient(id)} and
     * {@link ModelService#getChatModel(String) getChatModel(id)}.
     */
    private static String resolveEntityId(NodeDef node, ExecutionContext context, ModelService modelService) {
        NodeModelReference modelReference = NodeModelReference.from(node);
        if (modelReference != null && modelReference.identifiesProviderModel()) {
            ModelEntity entity = modelService.findOrMaterialize(
                    tenantOf(context), modelReference.provider(), modelReference.modelName(), ModelType.LLM);
            return entity.getId();
        }
        if (modelReference != null && modelReference.identifiesEntity()) {
            return modelReference.entityId();
        }
        throw new IllegalArgumentException(
                "Node '" + node.getId() + "' has no resolvable model (need modelProvider + modelName)");
    }

    /** Prefer the run-bound tenant and fall back to the current authenticated user. */
    private static String tenantOf(ExecutionContext context) {
        String tenantId = context == null ? null : context.resourceTenant();
        return tenantId == null || tenantId.isBlank() ? UserContextHolder.currentTenantId() : tenantId;
    }

    /**
     * Translate the designer's per-node model overrides
     * ({@code model.completionParams} — Dify shape) into a Spring AI
     * {@link ChatOptions}. Delegates the vendor-specific quirks (thinking mode
     * toggles for Qwen / GLM / Doubao, {@code reasoning_effort} for o-series,
     * Ollama's native {@code enableThinking()} / {@code disableThinking()},
     * penalties, stops, etc.) to {@link ChatOptionsFactory} in
     * {@code agentstart-model} so every LLM-shaped node
     * ({@code LlmNodeExecutor}, {@code ParameterExtractorNodeExecutor}, …)
     * shares one source of truth for "how does a Dify-shape settings blob
     * become vendor params" — otherwise disabling reasoning would have to be
     * re-wired per node and drift out of sync.
     * <p>
     * Returns {@code null} when the node has no overrides so the caller can
     * skip attaching options entirely.
     */
    static ChatOptions perNodeOptions(NodeDef node) {
        NodeModelReference modelReference = NodeModelReference.from(node);
        if (modelReference == null || !modelReference.hasCompletionSettings()) {
            return null;
        }
        return ChatOptionsFactory.buildFromSettings(
                modelReference.provider(), modelReference.modelName(), modelReference.completionSettings());
    }
}
