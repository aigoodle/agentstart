package io.aigoodle.web.dto.dify;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Dify {@code GET /messages} 单条消息响应 —— 一行 = 一问一答（{@code query} +
 * {@code answer}），与 {@code messages} 表里两行 USER / ASSISTANT 记录的映射
 * 关系由 controller 层配对生成。id 使用 ASSISTANT 那条 row 的 message id，
 * 便于游标翻页（{@code first_id} 语义就是"当前页最早一条 message id"）。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class DifyMessageVO {

    private String id;

    @JsonProperty("conversation_id")
    private String conversationId;

    private Map<String, Object> inputs;

    private String query;

    private String answer;

    @JsonProperty("message_files")
    private List<Object> messageFiles;

    /** {@code {"rating":"like"}} / {@code {"rating":"dislike"}} / {@code null}. */
    private Map<String, Object> feedback;

    @JsonProperty("retriever_resources")
    private List<Object> retrieverResources;

    @JsonProperty("created_at")
    private Long createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<Object> getMessageFiles() {
        return messageFiles;
    }

    public void setMessageFiles(List<Object> messageFiles) {
        this.messageFiles = messageFiles;
    }

    public Map<String, Object> getFeedback() {
        return feedback;
    }

    public void setFeedback(Map<String, Object> feedback) {
        this.feedback = feedback;
    }

    public List<Object> getRetrieverResources() {
        return retrieverResources;
    }

    public void setRetrieverResources(List<Object> retrieverResources) {
        this.retrieverResources = retrieverResources;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
