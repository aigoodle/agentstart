package io.aigoodle.completion.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * OpenAI-shaped token usage stats. Populated on a best-effort basis from
 * workflow LLM node outputs — currently emits zeros when the underlying nodes
 * don't propagate token counts (kept in the response so the field shape stays
 * OpenAI-parity for tooling that inspects it).
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAIUsage {

    @JsonProperty("prompt_tokens")
    private Integer promptTokens = 0;

    @JsonProperty("completion_tokens")
    private Integer completionTokens = 0;

    @JsonProperty("total_tokens")
    private Integer totalTokens = 0;
}
