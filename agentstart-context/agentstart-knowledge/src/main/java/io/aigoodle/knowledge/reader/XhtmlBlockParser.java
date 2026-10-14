package io.aigoodle.knowledge.reader;

import io.aigoodle.knowledge.reader.model.BlockType;
import io.aigoodle.knowledge.reader.model.DocumentBlock;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Converts Tika XHTML into semantic blocks using only JDK XML APIs. */
final class XhtmlBlockParser {
    private XhtmlBlockParser() {}

    static List<DocumentBlock> parse(String xhtml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        var document =
                factory.newDocumentBuilder().parse(new ByteArrayInputStream(xhtml.getBytes(StandardCharsets.UTF_8)));
        List<DocumentBlock> blocks = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        walk(document.getDocumentElement(), blocks, headings);
        return blocks;
    }

    private static void walk(Node node, List<DocumentBlock> blocks, List<String> headings) {
        if (node instanceof Element element) {
            String tag = element.getTagName().toLowerCase().replaceFirst("^.*:", "");
            if (tag.matches("h[1-6]")) {
                int level = Integer.parseInt(tag.substring(1));
                String text = directText(element);
                String path = ParserSupport.updateHeading(headings, level, text);
                ParserSupport.add(blocks, BlockType.HEADING, text, null, level, path, Map.of());
                return;
            }
            if (tag.equals("table")) {
                List<List<String>> rows = new ArrayList<>();
                NodeList rowNodes = element.getElementsByTagName("tr");
                for (int i = 0; i < rowNodes.getLength(); i++) {
                    List<String> cells = new ArrayList<>();
                    NodeList children = rowNodes.item(i).getChildNodes();
                    for (int c = 0; c < children.getLength(); c++) {
                        Node child = children.item(c);
                        if (child instanceof Element ce
                                && (ce.getTagName().endsWith("td")
                                        || ce.getTagName().endsWith("th")))
                            cells.add(ce.getTextContent().strip());
                    }
                    if (!cells.isEmpty()) rows.add(cells);
                }
                ParserSupport.add(
                        blocks,
                        BlockType.TABLE,
                        ParserSupport.markdownTable(rows),
                        null,
                        null,
                        ParserSupport.headingPath(headings),
                        Map.of("rows", rows.size()));
                return;
            }
            BlockType type =
                    switch (tag) {
                        case "p", "div", "blockquote" -> BlockType.PARAGRAPH;
                        case "li" -> BlockType.LIST_ITEM;
                        case "pre", "code" -> BlockType.CODE;
                        case "img" -> BlockType.IMAGE;
                        default -> null;
                    };
            if (type != null) {
                String text = tag.equals("img") ? element.getAttribute("alt") : directText(element);
                ParserSupport.add(blocks, type, text, null, null, ParserSupport.headingPath(headings), Map.of());
                return;
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) walk(children.item(i), blocks, headings);
    }

    private static String directText(Element element) {
        return element.getTextContent()
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t]+", " ")
                .strip();
    }
}
