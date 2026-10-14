package io.aigoodle.model.provider;

import io.aigoodle.model.enums.ModelFeature;
import io.aigoodle.model.enums.ModelType;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

/**
 * One model returned by a vendor's live catalog (typically {@code GET /v1/models}
 * on OpenAI-compatible providers, or a provider's own listing API).
 * <p>
 * Contrast with {@link PredefinedModel}: predefined models are the hardcoded catalog a
 * provider ships with; remote models are what the vendor's key actually unlocks at
 * runtime. The two are merged during discovery — a remote model matching a preset
 * inherits typed metadata (context window, embedding dimensions, capability flags),
 * while unmatched entries fall back to keyword-based type inference.
 */
@Data
@Builder
public class RemoteModel {

    /** The vendor-side identifier used when invoking the model. */
    private String modelId;

    /** Human-friendly label. Defaults to {@link #modelId} when unspecified. */
    private String label;

    /** Best-known modality; inferred by keyword when the vendor API doesn't disclose it. */
    private ModelType modelType;

    /** Context window (LLM) when known. */
    private Integer contextLength;

    /** Output vector size (embedding) when known. */
    private Integer dimensions;

    /** Capability flags (STREAM / TOOL_CALL / VISION / ...) known ahead of time. */
    @Builder.Default
    private Set<ModelFeature> features = Set.of();

    /** Raw {@code owned_by} / group label the vendor returned, if any — surfaced for the UI. */
    private String ownedBy;

    /**
     * Whether the type was authoritatively provided (matched a preset or vendor-declared)
     * vs guessed by keyword. The UI can nudge users to confirm inferred types before sync.
     */
    private boolean typeInferred;

    /**
     * Filled with keyword-inference below when the caller has no better source.
     * <p>
     * The rerank check runs FIRST — {@code bge-reranker-v2-m3} contains both "bge"
     * and "rerank", but it's a reranker, not an embedding model. Similarly speech
     * checks precede TTS so a "voice-to-text" model isn't misclassified.
     * <p>
     * Embedding keywords are deliberately broad: many Chinese vendors ship embeddings
     * without the "embed" prefix (nomic-*, gte-*, e5-*, stella, bce-* from Netease,
     * text2vec-*, sentence-transformers). Missing one here shows up as an embedding
     * model classified as LLM — the visible failure mode is subtler than the reverse,
     * so we err toward over-including embedding markers.
     */
    public static ModelType inferModelType(String modelId) {
        if (modelId == null) {
            return ModelType.LLM;
        }
        String s = modelId.toLowerCase();
        if (s.contains("rerank")) {
            return ModelType.RERANK;
        }
        if (s.contains("embedding")
                || s.contains("embed")
                || s.contains("bge")
                || s.contains("m3e")
                || s.contains("bce")
                || s.contains("nomic")
                || s.contains("gte-")
                || s.contains("e5-")
                || s.contains("stella")
                || s.contains("text2vec")
                || s.contains("sentence")
                || s.contains("piccolo")
                || s.startsWith("jina-")) {
            return ModelType.TEXT_EMBEDDING;
        }
        if (s.contains("whisper")
                || s.contains("speech-to-text")
                || s.contains("s2t")
                || s.contains("asr")
                || s.contains("stt")) {
            return ModelType.SPEECH2TEXT;
        }
        if (s.contains("tts") || s.contains("text-to-speech")) {
            return ModelType.TTS;
        }
        if (s.contains("moderation")) {
            return ModelType.MODERATION;
        }
        return ModelType.LLM;
    }

    /** Convenience: build a remote-model entry from a matching {@link PredefinedModel}. */
    public static RemoteModel fromPredefined(PredefinedModel preset) {
        return RemoteModel.builder()
                .modelId(preset.getModel())
                .label(preset.getLabel() == null ? preset.getModel() : preset.getLabel())
                .modelType(preset.getModelType())
                .contextLength(preset.getContextLength())
                .dimensions(preset.getDimensions())
                .features(preset.getFeatures())
                .typeInferred(false)
                .build();
    }
}
