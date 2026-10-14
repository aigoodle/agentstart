package io.aigoodle.completion.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI chat message envelope: {@code {role, content, name?}}.
 * <p>
 * Roles follow the OpenAI convention — {@code system}, {@code user},
 * {@code assistant}, {@code tool}. Content is a plain string; multimodal parts
 * are out of scope for the initial /chat/completions surface.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAIMessage {

    private String role;
    private String content;
    private String name;

    public static OpenAIMessage assistant(String content) {
        OpenAIMessage m = new OpenAIMessage();
        m.setRole("assistant");
        m.setContent(content);
        return m;
    }

    public static OpenAIMessage user(String content) {
        OpenAIMessage m = new OpenAIMessage();
        m.setRole("user");
        m.setContent(content);
        return m;
    }
}
