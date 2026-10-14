package io.aigoodle.web.dto;

import java.util.List;
import lombok.Data;

/** Body for POST/PUT on {@code /prompt-templates}. */
@Data
public class PromptTemplateRequest {

    private String name;
    private String category;
    private String description;
    private String content;
    private List<String> tags;
}
