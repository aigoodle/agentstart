package io.aigoodle.skill.service;

import java.util.List;
import lombok.Data;

@Data
public class SaveSkillRequest {
    private String code;
    private String name;
    private String description;
    private String instructions;
    private String status;
    private List<String> toolNames = List.of();
}
