package com.requestmanagement.backend.request;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Component
public class RichDescriptionValidator {
    static final int MAX_SERIALIZED_BYTES = 100_000;
    static final int MAX_PLAIN_TEXT_LENGTH = 10_000;
    static final int MAX_NODES = 1_000;
    static final int MAX_DEPTH = 20;
    static final int MAX_TABLE_ROWS = 20;
    static final int MAX_TABLE_COLUMNS = 20;
    static final int MAX_LINK_LENGTH = 2_048;

    private static final Set<String> ALLOWED_NODES = Set.of(
            "doc", "paragraph", "text", "heading", "bulletList", "orderedList", "listItem",
            "table", "tableRow", "tableHeader", "tableCell", "hardBreak");
    private static final Set<String> ALLOWED_MARKS = Set.of("bold", "italic", "link");

    private final ObjectMapper objectMapper;

    public RichDescriptionValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidatedDocument validate(JsonNode document) {
        if (document == null || !document.isObject()) bad("Rich description must be a JSON object.");
        try {
            if (objectMapper.writeValueAsBytes(document).length > MAX_SERIALIZED_BYTES) {
                bad("Rich description is too large.");
            }
        } catch (Exception exception) {
            bad("Rich description could not be read.");
        }
        Counter counter = new Counter();
        validateNode(document, 1, counter, true);
        String plainText = render(document).strip();
        if (plainText.isEmpty()) bad("Description is required.");
        if (plainText.length() > MAX_PLAIN_TEXT_LENGTH) bad("Description must not exceed 10000 characters.");
        return new ValidatedDocument(document.deepCopy(), plainText);
    }

    private void validateNode(JsonNode node, int depth, Counter counter, boolean root) {
        if (!node.isObject()) bad("Every rich description node must be an object.");
        if (depth > MAX_DEPTH) bad("Rich description nesting is too deep.");
        if (++counter.nodes > MAX_NODES) bad("Rich description contains too many nodes.");
        requireOnlyFields(node, Set.of("type", "attrs", "content", "marks", "text"));
        String type = requiredText(node, "type");
        if (!ALLOWED_NODES.contains(type)) bad("Unsupported rich description node: " + type + ".");
        if (root && !type.equals("doc")) bad("Rich description root must be a doc node.");
        if (!root && type.equals("doc")) bad("A doc node is allowed only at the root.");

        validateAttributes(type, node.get("attrs"));
        validateMarks(type, node.get("marks"));
        JsonNode text = node.get("text");
        JsonNode content = node.get("content");
        if (type.equals("text")) {
            if (text == null || !text.isTextual() || text.textValue().isEmpty()) {
                bad("Text nodes must contain non-empty text.");
            }
            if (text.textValue().length() > MAX_PLAIN_TEXT_LENGTH) bad("A text node is too large.");
            if (content != null) bad("Text nodes cannot contain child nodes.");
            return;
        }
        if (text != null) bad("Only text nodes may contain text.");
        if (type.equals("hardBreak")) {
            if (content != null) bad("Hard breaks cannot contain child nodes.");
            return;
        }
        if (content == null || !content.isArray()) bad(type + " nodes must contain a content array.");
        validateStructure(type, content);
        for (JsonNode child : content) validateNode(child, depth + 1, counter, false);
    }

    private void validateStructure(String type, JsonNode content) {
        Set<String> allowedChildren = switch (type) {
            case "doc" -> Set.of("paragraph", "heading", "bulletList", "orderedList", "table");
            case "paragraph", "heading" -> Set.of("text", "hardBreak");
            case "bulletList", "orderedList" -> Set.of("listItem");
            case "listItem" -> Set.of("paragraph", "bulletList", "orderedList");
            case "table" -> Set.of("tableRow");
            case "tableRow" -> Set.of("tableHeader", "tableCell");
            case "tableHeader", "tableCell" -> Set.of("paragraph", "heading", "bulletList", "orderedList");
            default -> Set.of();
        };
        if ((type.equals("bulletList") || type.equals("orderedList") || type.equals("listItem")
                || type.equals("table") || type.equals("tableRow") || type.equals("tableHeader")
                || type.equals("tableCell")) && content.isEmpty()) {
            bad(type + " nodes cannot be empty.");
        }
        if (type.equals("table")) {
            if (content.size() > MAX_TABLE_ROWS) bad("Tables may contain at most 20 rows.");
            int columns = -1;
            for (JsonNode row : content) {
                JsonNode cells = row.get("content");
                if (cells == null || !cells.isArray() || cells.size() > MAX_TABLE_COLUMNS) {
                    bad("Tables may contain at most 20 columns.");
                }
                if (columns < 0) columns = cells.size();
                else if (cells.size() != columns) bad("Every table row must contain the same number of cells.");
            }
        }
        for (JsonNode child : content) {
            if (!child.isObject() || child.get("type") == null || !child.get("type").isTextual()
                    || !allowedChildren.contains(child.get("type").textValue())) {
                bad("Invalid child node in " + type + ".");
            }
        }
    }

    private void validateAttributes(String type, JsonNode attrs) {
        if (attrs == null || attrs.isNull()) return;
        if (!attrs.isObject()) bad("Node attributes must be an object.");
        switch (type) {
            case "heading" -> {
                requireOnlyFields(attrs, Set.of("level"));
                JsonNode level = attrs.get("level");
                if (level == null || !level.isIntegralNumber() || level.intValue() < 1 || level.intValue() > 3) {
                    bad("Heading level must be between 1 and 3.");
                }
            }
            case "orderedList" -> {
                requireOnlyFields(attrs, Set.of("start", "type"));
                if (attrs.hasNonNull("start") && (!attrs.get("start").isIntegralNumber()
                        || attrs.get("start").intValue() < 1 || attrs.get("start").intValue() > 10_000)) {
                    bad("Ordered list start is invalid.");
                }
                requireNullAttribute(attrs, "type");
            }
            case "bulletList" -> {
                requireOnlyFields(attrs, Set.of("type"));
                requireNullAttribute(attrs, "type");
            }
            case "tableCell", "tableHeader" -> validateCellAttributes(attrs);
            default -> {
                if (!attrs.isEmpty()) bad("Unsupported attributes on " + type + ".");
            }
        }
    }

    private void validateCellAttributes(JsonNode attrs) {
        requireOnlyFields(attrs, Set.of("colspan", "rowspan", "colwidth"));
        for (String name : Set.of("colspan", "rowspan")) {
            if (attrs.hasNonNull(name) && (!attrs.get(name).isIntegralNumber()
                    || attrs.get(name).intValue() < 1 || attrs.get(name).intValue() > MAX_TABLE_COLUMNS)) {
                bad("Invalid table cell span.");
            }
        }
        JsonNode widths = attrs.get("colwidth");
        if (widths != null && !widths.isNull()) {
            if (!widths.isArray() || widths.size() > MAX_TABLE_COLUMNS) bad("Invalid table column widths.");
            for (JsonNode width : widths) {
                if (!width.isIntegralNumber() || width.intValue() < 1 || width.intValue() > 2_000) {
                    bad("Invalid table column width.");
                }
            }
        }
    }

    private void validateMarks(String nodeType, JsonNode marks) {
        if (marks == null || marks.isNull()) return;
        if (!nodeType.equals("text") || !marks.isArray()) bad("Marks are allowed only on text nodes.");
        Set<String> seen = new HashSet<>();
        for (JsonNode mark : marks) {
            if (!mark.isObject()) bad("Marks must be objects.");
            requireOnlyFields(mark, Set.of("type", "attrs"));
            String type = requiredText(mark, "type");
            if (!ALLOWED_MARKS.contains(type) || !seen.add(type)) bad("Unsupported or duplicate mark: " + type + ".");
            JsonNode attrs = mark.get("attrs");
            if (type.equals("link")) validateLinkAttributes(attrs);
            else if (attrs != null && !attrs.isNull() && (!attrs.isObject() || !attrs.isEmpty())) {
                bad("Unsupported mark attributes.");
            }
        }
    }

    private void validateLinkAttributes(JsonNode attrs) {
        if (attrs == null || !attrs.isObject()) bad("Links require attributes.");
        requireOnlyFields(attrs, Set.of("href", "target", "rel", "class"));
        String href = requiredText(attrs, "href");
        if (href.length() > MAX_LINK_LENGTH) bad("Link URL is too long.");
        try {
            URI uri = URI.create(href);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                bad("Links must use an absolute HTTP or HTTPS URL.");
            }
        } catch (IllegalArgumentException exception) {
            bad("Link URL is invalid.");
        }
        requireSafeOptionalText(attrs, "target", Set.of("_blank"));
        requireSafeOptionalText(attrs, "rel", Set.of("noopener noreferrer nofollow", "noopener noreferrer"));
        requireNullAttribute(attrs, "class");
    }

    private String render(JsonNode node) {
        String type = node.get("type").textValue();
        if (type.equals("text")) return node.get("text").textValue();
        if (type.equals("hardBreak")) return "\n";
        JsonNode content = node.get("content");
        StringBuilder result = new StringBuilder();
        int index = 0;
        for (JsonNode child : content) {
            String rendered = render(child);
            if (type.equals("bulletList")) result.append("- ").append(rendered.strip());
            else if (type.equals("orderedList")) {
                JsonNode attrs = node.get("attrs");
                JsonNode startValue = attrs == null ? null : attrs.get("start");
                int start = startValue == null || !startValue.isIntegralNumber() ? 1 : startValue.intValue();
                result.append(start + index).append(". ").append(rendered.strip());
            } else result.append(rendered);
            index++;
            if (index < content.size()) {
                if (type.equals("doc") || type.endsWith("List") || type.equals("listItem") || type.equals("table")) result.append('\n');
                else if (type.equals("tableRow")) result.append('\t');
            }
        }
        return result.toString();
    }

    private String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) bad(field + " is required.");
        return value.textValue();
    }

    private void requireOnlyFields(JsonNode object, Set<String> allowed) {
        Iterator<String> names = object.propertyNames().iterator();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) bad("Unsupported JSON field: " + name + ".");
        }
    }

    private void requireNullAttribute(JsonNode attrs, String name) {
        if (attrs.has(name) && !attrs.get(name).isNull()) bad("Unsupported attribute: " + name + ".");
    }

    private void requireSafeOptionalText(JsonNode attrs, String name, Set<String> allowed) {
        JsonNode value = attrs.get(name);
        if (value != null && !value.isNull() && (!value.isTextual() || !allowed.contains(value.textValue()))) {
            bad("Unsafe link attribute: " + name + ".");
        }
    }

    private void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static final class Counter { private int nodes; }

    public record ValidatedDocument(JsonNode document, String plainText) { }
}
