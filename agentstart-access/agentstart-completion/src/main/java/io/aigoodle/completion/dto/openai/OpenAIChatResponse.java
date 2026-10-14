package io.aigoodle.completion.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/**
 * OpenAI-compatible chat completion response — both the blocking response
 * ({@code object = chat.completion}) and each streaming chunk
 * ({@code object = chat.completion.chunk}) share this envelope.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAIChatResponse {

    public static final String OBJECT_COMPLETION = "chat.completion";
    public static final String OBJECT_CHUNK = "chat.completion.chunk";

    private String id;
    private String object;
    private Long created;
    private String model;
    private List<OpenAIChoice> choices = new ArrayList<>();
    private OpenAIUsage usage;

    public static OpenAIChatResponse completion(String model, String answer) {
        OpenAIChatResponse response = new OpenAIChatResponse();
        response.setId("chatcmpl-" + UUID.randomUUID().toString().replace("-", ""));
        response.setObject(OBJECT_COMPLETION);
        response.setCreated(Instant.now().getEpochSecond());
        response.setModel(resolveModelName(model));
        response.getChoices().add(OpenAIChoice.message(0, OpenAIMessage.assistant(answer), "stop"));
        response.setUsage(new OpenAIUsage());
        return response;
    }

    public static OpenAIChatResponse chunk(
            String id, String model, String role, String contentDelta, String finishReason) {
        OpenAIChatResponse responseChunk = new OpenAIChatResponse();
        responseChunk.setId(id);
        responseChunk.setObject(OBJECT_CHUNK);
        responseChunk.setCreated(Instant.now().getEpochSecond());
        responseChunk.setModel(resolveModelName(model));
        OpenAIDelta delta = new OpenAIDelta();
        if (role != null) {
            delta.setRole(role);
        }
        if (contentDelta != null) {
            delta.setContent(contentDelta);
        }
        responseChunk.getChoices().add(OpenAIChoice.delta(0, delta, finishReason));
        return responseChunk;
    }

    private static String resolveModelName(String model) {
        return model == null ? "spring-agent" : model;
    }
}
