package io.aigoodle.skill.service;

import java.util.List;

public record SkillResolution(String prompt, List<String> toolNames) {
    public static SkillResolution empty() {
        return new SkillResolution("", List.of());
    }
}
