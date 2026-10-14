package io.aigoodle.knowledge.reader.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/** Format-independent intermediate representation between parsing and chunking. */
@Data
@Builder
@Jacksonized
public class ParsedDocument {
    private String filename;
    private String parser;
    private String mediaType;
    private String title;

    @Builder.Default
    private List<DocumentBlock> blocks = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public String text() {
        StringBuilder output = new StringBuilder();
        for (DocumentBlock block : blocks) {
            if (block.getText() == null || block.getText().isBlank()) continue;
            if (block.getType() == BlockType.HEADING) {
                output.append("#"
                                .repeat(Math.max(
                                        1, Math.min(6, block.getHeadingLevel() == null ? 2 : block.getHeadingLevel()))))
                        .append(' ');
            }
            output.append(block.getText().strip()).append("\n\n");
        }
        return output.toString().strip();
    }

    public int pageCount() {
        return blocks.stream()
                .map(DocumentBlock::getPage)
                .filter(p -> p != null)
                .max(Integer::compareTo)
                .orElse(0);
    }
}
