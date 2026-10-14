package io.aigoodle.workflow.node.builtin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Normalized categories and response-matching rules for a classifier node. */
final class ClassifierCategorySet {

    private final List<ClassifierCategory> categories;

    private ClassifierCategorySet(List<ClassifierCategory> categories) {
        this.categories = categories;
    }

    static ClassifierCategorySet from(List<Map<String, Object>> configuredCategories) {
        List<ClassifierCategory> categories = new ArrayList<>();
        for (Map<String, Object> configuredCategory : configuredCategories) {
            String id = text(configuredCategory.get("id"));
            if (id == null) {
                continue;
            }
            String name = text(configuredCategory.get("name"));
            categories.add(new ClassifierCategory(id, name == null ? id : name));
        }
        return new ClassifierCategorySet(List.copyOf(categories));
    }

    boolean isEmpty() {
        return categories.isEmpty();
    }

    String menu() {
        StringBuilder menu = new StringBuilder();
        categories.forEach(category -> menu.append("- ")
                .append(category.id())
                .append(": ")
                .append(category.name())
                .append('\n'));
        return menu.toString();
    }

    ClassifierCategory match(String modelResponse) {
        String normalizedResponse =
                modelResponse == null ? "" : modelResponse.trim().toLowerCase(Locale.ROOT);
        for (ClassifierCategory category : categories) {
            if (containsCompleteTerm(normalizedResponse, category.normalizedId())
                    || containsCompleteTerm(normalizedResponse, category.normalizedName())) {
                return category;
            }
        }
        return categories.get(categories.size() - 1);
    }

    private static boolean containsCompleteTerm(String response, String categoryTerm) {
        Pattern completeTerm =
                Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(categoryTerm) + "(?![\\p{L}\\p{N}_])");
        return completeTerm.matcher(response).find();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    record ClassifierCategory(String id, String name) {

        String normalizedId() {
            return id.toLowerCase(Locale.ROOT);
        }

        String normalizedName() {
            return name.toLowerCase(Locale.ROOT);
        }
    }
}
