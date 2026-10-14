package io.aigoodle.completion.dto.dify;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Dify {@code POST /v1/chat-messages} 兼容请求体。
 * <p>
 * 字段与
 * <a href="https://docs.dify.ai/guides/application-publishing/developing-with-apis">
 * Dify 服务 API</a> 对齐。
 */
@Data
public class DifyChatMessagesRequest {

    /** 本轮用户提问 —— 会作为最后一条 user message 灌到内部请求 */
    private String query;

    @JsonAlias({"data"})
    private Map<String, Object> inputs = new HashMap<>();

    /** streaming | blocking；缺省视为 streaming */
    @JsonProperty("response_mode")
    private String responseMode;

    private String user;

    @JsonProperty("conversation_id")
    private String conversationId;

    private List<Object> files = new ArrayList<>();

    @JsonProperty("auto_generate_name")
    private Boolean autoGenerateName;

    /** spring-agent-start extension: maximum active Agent execution duration. */
    @JsonProperty("timeout_ms")
    @JsonAlias({"timeoutMillis"})
    private Long timeoutMillis;

    @JsonProperty("debug")
    @JsonAlias({"debug_mode", "debugMode"})
    private Boolean debug;

    @JsonProperty("workflow_id")
    @JsonAlias({"workflowId"})
    private String workflowId;

    @JsonProperty("app_id")
    @JsonAlias({"appId"})
    private String appId;

    public boolean streaming() {
        return responseMode == null || !"blocking".equalsIgnoreCase(responseMode);
    }
}
