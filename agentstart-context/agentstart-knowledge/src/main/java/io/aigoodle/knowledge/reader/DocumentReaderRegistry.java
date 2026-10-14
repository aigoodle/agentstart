package io.aigoodle.knowledge.reader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds every {@link DocumentReader} bean and picks the right one for a given
 * filename or source type. Lookup order: exact name match → {@code supports(filename)}
 * scan → generic Tika fallback (if registered).
 */
public class DocumentReaderRegistry {

    public static final String FALLBACK_NAME = "tika";

    private final Map<String, DocumentReader> byName = new LinkedHashMap<>();
    private final List<DocumentReader> readers;

    public DocumentReaderRegistry(List<DocumentReader> readers) {
        this.readers = readers == null ? List.of() : List.copyOf(readers);
        for (DocumentReader r : this.readers) {
            byName.putIfAbsent(r.getName().toLowerCase(), r);
        }
    }

    /** Pick by explicit name, e.g. {@code "markdown"}, or return {@code null}. */
    public DocumentReader byName(String name) {
        return name == null ? null : byName.get(name.toLowerCase());
    }

    /** Pick the first reader whose {@link DocumentReader#supports(String)} matches. */
    public DocumentReader pick(String filename) {
        for (DocumentReader r : readers) {
            if (r.supports(filename)) {
                return r;
            }
        }
        return byName.get(FALLBACK_NAME);
    }

    public List<String> names() {
        return List.copyOf(byName.keySet());
    }
}
