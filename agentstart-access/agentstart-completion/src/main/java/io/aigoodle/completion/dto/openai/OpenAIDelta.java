package io.aigoodle.completion.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Delta payload used inside streaming {@code chat.completion.chunk} events.
 * First chunk typically carries {@code role="assistant"} without content;
 * subsequent chunks carry {@code content} deltas; the final chunk has neither
 * and only a {@code finish_reason} on the enclosing choice.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAIDelta {

    private String role;
    private String content;

    public static OpenAIDelta role(String role) {
        OpenAIDelta d = new OpenAIDelta();
        d.setRole(role);
        return d;
    }

    public static OpenAIDelta content(String content) {
        OpenAIDelta d = new OpenAIDelta();
        d.setContent(content);
        return d;
    }
}
