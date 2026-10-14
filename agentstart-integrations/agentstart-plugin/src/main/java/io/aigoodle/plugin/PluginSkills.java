package io.aigoodle.plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Loads deployment-owned SKILL.md and explicitly packaged text references. Never executes scripts. */
public final class PluginSkills {
    private PluginSkills() {}

    public static PluginSkill load(Resource entrypoint, String... referencePaths) throws IOException {
        String text = read(entrypoint).replace("\r\n", "\n");
        if (!text.startsWith("---\n")) throw new IllegalArgumentException("SKILL.md requires YAML frontmatter");
        int end = text.indexOf("\n---\n", 4);
        if (end < 0) throw new IllegalArgumentException("SKILL.md frontmatter is not closed");
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(131072);
        Object parsed = new Yaml(new SafeConstructor(options)).load(text.substring(4, end));
        if (!(parsed instanceof Map<?, ?> metadata)
                || !(metadata.get("name") instanceof String name)
                || !(metadata.get("description") instanceof String description))
            throw new IllegalArgumentException("Skill name and description must be strings");
        Map<String, String> resources = new LinkedHashMap<>();
        for (String path : referencePaths) {
            if (path == null
                    || path.startsWith("/")
                    || path.contains("..")
                    || path.contains("\\")
                    || path.contains(":"))
                throw new IllegalArgumentException("Skill references must stay inside the skill directory");
            resources.put(path, read(entrypoint.createRelative(path)));
        }
        return new PluginSkill(name, name, description, text.substring(end + 5).trim(), resources);
    }

    private static String read(Resource resource) throws IOException {
        try (var input = resource.getInputStream()) {
            byte[] bytes = input.readNBytes(131073);
            if (bytes.length > 131072) throw new IllegalArgumentException("Skill text exceeds 128 KiB");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
