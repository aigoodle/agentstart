package io.aigoodle.web.dto;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/** Body for both {@code /agents/{id}/chat} and its SSE variant. */
@Data
public class ChatRequest {

    private String query;
    private String conversationId;
    private Map<String, Object> variables = new HashMap<>();
}
