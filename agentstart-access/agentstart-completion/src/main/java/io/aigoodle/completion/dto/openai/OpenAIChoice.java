package io.aigoodle.completion.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * One choice in the OpenAI {@code choices[]} array. For blocking responses
 * carries {@link #message}; for streaming chunks carries {@link #delta}. The
 * {@link #finishReason} is populated on the final chunk / blocking response
 * ({@code stop} for a normal completion, {@code error} when the run failed).
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAIChoice {

    private Integer index;
    private OpenAIMessage message;
    private OpenAIDelta delta;

    @JsonProperty("finish_reason")
    private String finishReason;

    public static OpenAIChoice message(int index, OpenAIMessage message, String finishReason) {
        OpenAIChoice c = new OpenAIChoice();
        c.setIndex(index);
        c.setMessage(message);
        c.setFinishReason(finishReason);
        return c;
    }

    public static OpenAIChoice delta(int index, OpenAIDelta delta, String finishReason) {
        OpenAIChoice c = new OpenAIChoice();
        c.setIndex(index);
        c.setDelta(delta);
        c.setFinishReason(finishReason);
        return c;
    }
}
