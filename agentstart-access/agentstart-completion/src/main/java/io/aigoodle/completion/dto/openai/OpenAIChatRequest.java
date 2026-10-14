package io.aigoodle.completion.dto.openai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.ai.chat.messages.MessageType;

/**
 * OpenAI-compatible chat completions request body accepted by
 * {@code POST /api/v1/chat/completions/{appId}}. Keeps the standard OpenAI
 * fields (messages / stream / temperature …) and adds a couple of Dify-parity
 * extensions:
 *
 * <ul>
 *   <li>{@code conversation_id} — reuse an existing chat session; a fresh
 *       UUID is minted when absent</li>
 *   <li>{@code data} — form-style variables consumed by the workflow's START
 *       node (also accepted as {@code inputs} to mirror the Dify REST shape)</li>
 * </ul>
 *
 * <p>The request never carries the {@code appId} — it comes from the path so
 * the dispatch layer can look up the {@code AppEntity} and decide whether to
 * route to the workflow engine or the agent runtime.</p>
 */
@Data
public class OpenAIChatRequest {

    private String model;

    private List<OpenAIMessage> messages = new ArrayList<>();

    /** {@code true} = SSE, {@code false} / absent = blocking JSON. */
    private Boolean stream;

    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /** Agent execution deadline extension; ignored for workflow-only applications. */
    @JsonProperty("timeout_ms")
    @JsonAlias({"timeoutMillis"})
    private Long timeoutMillis;

    @JsonProperty("top_p")
    private Double topP;

    private List<String> stop;

    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    /** Existing conversation to append to; new one is minted when null / blank. */
    @JsonProperty("conversation_id")
    private String conversationId;

    /**
     * Workflow input variables. Accepts both {@code data} and {@code inputs} to
     * align with Dify's REST body while staying friendly to the legacy chat contract.
     */
    @JsonAlias({"inputs"})
    private Map<String, Object> data = new HashMap<>();

    /** Resume a durable HUMAN_INPUT checkpoint through the same chat endpoint. */
    @JsonProperty("human_input")
    @JsonAlias({"humanInput"})
    private HumanInputResume humanInput;

    @Data
    public static class HumanInputResume {
        @JsonProperty("run_id")
        @JsonAlias({"runId"})
        private String runId;

        @JsonProperty("resume_token")
        @JsonAlias({"resumeToken"})
        private String resumeToken;

        @JsonProperty("event_id")
        @JsonAlias({"eventId"})
        private String eventId;

        private Map<String, Object> payload = new HashMap<>();
    }

    @JsonProperty("invoke_from")
    private String invokeFrom;

    /**
     * Debug-mode flag. When {@code true}, workflow / chatflow apps route to
     * the DRAFT workflow (id == appId by invariant) instead of whatever
     * {@code apps.workflow_id} currently points to.
     */
    @JsonProperty("debug")
    @JsonAlias({"debug_mode", "debugMode"})
    private Boolean debug;

    /**
     * Explicit workflow-id override. Wins over both {@link #debug} and the
     * app's default binding.
     */
    @JsonProperty("workflow_id")
    @JsonAlias({"workflowId"})
    private String workflowId;

    @JsonProperty("app_id")
    @JsonAlias({"appId"})
    private String appId;

    /**
     * The last user turn — every workflow / agent input is keyed off it.
     */
    public String lastUserMessage() {
        if (messages == null) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            OpenAIMessage m = messages.get(i);
            if (m != null && MessageType.USER.getValue().equalsIgnoreCase(m.getRole())) {
                return m.getContent() == null ? "" : m.getContent();
            }
        }
        return "";
    }

    public boolean streaming() {
        return Boolean.TRUE.equals(stream);
    }
}
