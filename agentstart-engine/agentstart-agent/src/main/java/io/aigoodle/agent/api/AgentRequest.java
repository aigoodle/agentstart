package io.aigoodle.agent.api;

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input to an agent run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {

    private String query;

    /** Conversation ID for memory; a new one is generated when absent or blank. */
    private String conversationId;

    /** Maximum wall-clock duration of this execution segment; null or non-positive means unlimited. */
    private Long timeoutMillis;

    /** Extra variables available to the agent's instructions template. */
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    public static AgentRequest of(String query) {
        return AgentRequest.builder().query(query).build();
    }
}
