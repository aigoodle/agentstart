package io.aigoodle.workflow.service;

import io.aigoodle.common.util.JsonUtils;
import io.aigoodle.workflow.entity.HumanInteractionEntity;
import io.aigoodle.workflow.entity.WorkflowCheckpointEntity;
import io.aigoodle.workflow.graph.NodeDef;
import io.aigoodle.workflow.interaction.HumanInteractionNotifier;
import io.aigoodle.workflow.mapper.HumanInteractionMapper;
import io.aigoodle.workflow.node.WorkflowWaitRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Persistence boundary kept separate from the resume service to avoid lifecycle cycles. */
public class HumanInteractionStore {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final HumanInteractionMapper mapper;
    private final HumanInteractionNotifier notifier;

    public HumanInteractionStore(HumanInteractionMapper mapper) {
        this(mapper, null);
    }

    public HumanInteractionStore(HumanInteractionMapper mapper, HumanInteractionNotifier notifier) {
        this.mapper = mapper;
        this.notifier = notifier;
    }

    public HumanInteractionEntity ensureWaiting(
            WorkflowCheckpointEntity checkpoint, NodeDef node, WorkflowWaitRequest wait) {
        if (!"HUMAN_INPUT".equals(wait.type().name())) return null;
        HumanInteractionEntity existing =
                mapper.findForNode(checkpoint.getTenantId(), checkpoint.getRunId(), node.getId());
        if (existing != null) return existing;
        Map<String, Object> schema = wait.inputSchema();
        HumanInteractionEntity value = new HumanInteractionEntity();
        value.setId(text(schema.get("x-interaction-id")));
        value.setTenantId(checkpoint.getTenantId());
        value.setWorkflowId(checkpoint.getWorkflowId());
        value.setAppId(mapper.findAppId(checkpoint.getTenantId(), checkpoint.getWorkflowId()));
        value.setRunId(checkpoint.getRunId());
        value.setNodeId(node.getId());
        value.setConversationId(checkpoint.getConversationId());
        value.setStatus("PENDING");
        value.setTitle(text(schema.get("title")));
        value.setDescription(text(schema.get("description")));
        value.setFormMode(node.getString("formMode", "FIXED"));
        value.setPresentationMode(text(schema.get("x-presentation-mode")));
        String shortCode = shortCode();
        Map<String, Object> persistedSchema = new java.util.LinkedHashMap<>(schema);
        persistedSchema.put("x-short-code", shortCode);
        persistedSchema.put("x-channel-reply-hint", "回复时请保留 #" + shortCode);
        value.setInputSchemaJson(JsonUtils.toJson(persistedSchema));
        value.setAccessTokenHash(sha256(text(schema.get("x-access-token"))));
        value.setDeliveryConfigJson(JsonUtils.toJson(schema.getOrDefault("x-delivery", Map.of())));
        Map<String, Object> delivery = schema.get("x-delivery") instanceof Map<?, ?> raw
                ? raw.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                entry -> String.valueOf(entry.getKey()), Map.Entry::getValue))
                : Map.of();
        value.setShortCode(shortCode);
        value.setChannelProvider(text(delivery.get("provider")));
        value.setChannelId(text(delivery.get("channelId")));
        value.setChannelConnectionId(text(delivery.get("connectionId")));
        value.setChannelTarget(text(delivery.get("target")));
        value.setChannelConversationId(text(delivery.get("conversationId")));
        value.setExpiresAt(local(wait.expiresAt()));
        mapper.insert(value);
        if (notifier != null && value.getChannelConnectionId() != null && value.getChannelTarget() != null) {
            try {
                String messageId = notifier.notify(value);
                if (messageId != null) {
                    value.setNotificationMessageId(messageId);
                    mapper.updateById(value);
                }
            } catch (RuntimeException ignored) {
                // The durable interaction remains available through the web fallback. Channel
                // delivery can be retried independently without losing the waiting workflow.
            }
        }
        return value;
    }

    public HumanInteractionEntity byAccessToken(String token) {
        return mapper.findByAccessTokenHash(sha256(token));
    }

    public HumanInteractionEntity byId(String id) {
        return mapper.selectById(id);
    }

    public List<HumanInteractionEntity> byConversation(String tenant, String conversation) {
        return mapper.findByConversation(tenant, conversation);
    }

    public List<HumanInteractionEntity> pendingChannel(String tenant, String connectionId, String senderId) {
        return mapper.findPendingChannel(tenant, connectionId, senderId, LocalDateTime.now());
    }

    public HumanInteractionEntity pendingChannel(String tenant, String connectionId, String senderId, String code) {
        return mapper.findPendingByShortCode(tenant, connectionId, senderId, code, LocalDateTime.now());
    }

    public boolean beginSubmit(
            HumanInteractionEntity interaction, Map<String, Object> values, String submittedText, String submittedBy) {
        return mapper.beginSubmit(
                        interaction.getId(), JsonUtils.toJson(values), submittedText, submittedBy, LocalDateTime.now())
                == 1;
    }

    public void finishSubmit(String id, boolean completed) {
        mapper.finishSubmit(id, completed ? "SUBMITTED" : "PENDING", LocalDateTime.now());
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String shortCode() {
        StringBuilder value = new StringBuilder(6);
        for (int index = 0; index < 6; index++) value.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        return value.toString();
    }

    private static LocalDateTime local(java.time.Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, java.time.ZoneId.systemDefault());
    }

    private static String sha256(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
