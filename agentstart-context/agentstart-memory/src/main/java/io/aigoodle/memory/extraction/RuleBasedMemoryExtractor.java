package io.aigoodle.memory.extraction;

import io.aigoodle.memory.MemoryRole;
import io.aigoodle.memory.MemoryTier;
import io.aigoodle.memory.MemoryWrite;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Conservative zero-model-cost extractor for explicit identity and preference statements. */
public final class RuleBasedMemoryExtractor implements MemoryExtractor {

    private static final Pattern DURABLE_FACT = Pattern.compile(
            "(?iu)(?:^|[。！？.!?]\\s*)(?:我叫|我的名字是|我喜欢|我偏好|我希望|my name is|i prefer|i like)\\s*[^。！？.!?]{1,160}");

    @Override
    public List<MemoryWrite> extract(MemoryExchange exchange) {
        String content = exchange.userContent();
        if (content == null || content.isBlank()) return List.of();
        var matcher = DURABLE_FACT.matcher(content.strip());
        if (!matcher.find()) return List.of();
        String fact = matcher.group().replaceFirst("^[。！？.!?]\\s*", "").strip();
        return List.of(new MemoryWrite(
                exchange.tenantId(),
                exchange.ownerId(),
                null,
                MemoryTier.LONG_TERM,
                MemoryRole.FACT,
                fact,
                .9,
                Map.of("source", "explicit-user-statement", "extractor", "rule-based")));
    }
}
