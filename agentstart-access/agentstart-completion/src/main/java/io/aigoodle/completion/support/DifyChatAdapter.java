package io.aigoodle.completion.support;

import io.aigoodle.completion.dto.dify.DifyChatMessagesRequest;
import io.aigoodle.completion.dto.openai.OpenAIChatRequest;
import io.aigoodle.completion.dto.openai.OpenAIChatResponse;
import io.aigoodle.completion.dto.openai.OpenAIChoice;
import io.aigoodle.completion.dto.openai.OpenAIMessage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Translates between Dify chat-messages payloads and the internal OpenAI model. */
public final class DifyChatAdapter {

    private DifyChatAdapter() {}

    public static OpenAIChatRequest toInternalRequest(DifyChatMessagesRequest source) {
        OpenAIChatRequest request = new OpenAIChatRequest();
        request.setStream(Boolean.TRUE);
        request.setConversationId(source.getConversationId());
        request.setTimeoutMillis(source.getTimeoutMillis());
        Map<String, Object> inputs = source.getInputs();
        request.setData(inputs == null ? new HashMap<>() : new HashMap<>(inputs));
        if (source.getUser() != null && !source.getUser().isBlank()) {
            request.getData().putIfAbsent("__dify_user", source.getUser());
        }
        String query = source.getQuery() == null ? "" : source.getQuery();
        request.setMessages(List.of(OpenAIMessage.user(query)));
        request.setInvokeFrom("dify-chat-messages");
        return request;
    }

    public static Map<String, Object> toBlockingResponse(OpenAIChatResponse response, String conversationId) {
        String answer = "";
        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
            OpenAIChoice firstChoice = response.getChoices().getFirst();
            OpenAIMessage responseMessage = firstChoice.getMessage();
            if (responseMessage != null && responseMessage.getContent() != null) {
                answer = responseMessage.getContent();
            }
        }
        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("event", "message");
        responseBody.put("id", response.getId());
        responseBody.put("message_id", response.getId());
        responseBody.put("conversation_id", conversationId);
        responseBody.put("mode", "chat");
        responseBody.put("answer", answer);
        responseBody.put("created_at", response.getCreated());
        return responseBody;
    }
}
