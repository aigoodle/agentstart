package io.aigoodle.skill.service;

import java.util.List;

@FunctionalInterface
public interface SkillResolver {
    SkillResolution resolve(String tenantId, List<String> skillIds);

    static SkillResolver none() {
        return (tenantId, ids) -> SkillResolution.empty();
    }
}
