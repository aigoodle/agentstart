package io.aigoodle.agent.service;

import java.util.List;

public record AppPermissionSettings(String mode, List<Grant> grants) {
    public record Grant(String type, String subjectId, boolean includeDescendants) {}
}
