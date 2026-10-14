package io.aigoodle.knowledge.retrieve;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Safe, model-free query cleanup that works for both Chinese and Latin text. */
public class DefaultQueryTransformer implements QueryTransformer {

    @Override
    public List<String> transform(String query) {
        if (query == null || query.isBlank()) return List.of();
        String normalized = Normalizer.normalize(query, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .trim();
        Set<String> variants = new LinkedHashSet<>();
        variants.add(normalized);
        String withoutQuestionWords = normalized
                .replaceFirst("^(请问|请告诉我|帮我查一下|帮我查询|我想知道|what is|how to|please find)\\s*", "")
                .trim();
        if (!withoutQuestionWords.isBlank()) variants.add(withoutQuestionWords);
        String withoutPunctuation = withoutQuestionWords
                .replaceAll("[，。！？、；：,.!?;:()（）\\[\\]{}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!withoutPunctuation.isBlank()) variants.add(withoutPunctuation.toLowerCase(Locale.ROOT));
        return new ArrayList<>(variants);
    }
}
