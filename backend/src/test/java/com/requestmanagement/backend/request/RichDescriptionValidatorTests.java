package com.requestmanagement.backend.request;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RichDescriptionValidatorTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RichDescriptionValidator validator = new RichDescriptionValidator(mapper);

    @Test
    void acceptsApprovedTiptapNodesAndExtractsReadablePlainText() throws Exception {
        JsonNode document = json("""
                {"type":"doc","content":[
                  {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Summary","marks":[{"type":"bold"}]}]},
                  {"type":"bulletList","attrs":{"type":null},"content":[
                    {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"First"}]}]},
                    {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"Second","marks":[{"type":"link","attrs":{"href":"https://example.com","target":"_blank","rel":"noopener noreferrer nofollow","class":null}}]}]}]}
                  ]},
                  {"type":"table","content":[{"type":"tableRow","content":[
                    {"type":"tableHeader","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"A"}]}]},
                    {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"B"}]}]}
                  ]}]}
                ]}
                """);

        RichDescriptionValidator.ValidatedDocument result = validator.validate(document);

        assertThat(result.plainText()).isEqualTo("Summary\n- First\n- Second\nA\tB");
        assertThat(result.document()).isEqualTo(document);
    }

    @Test
    void rejectsExecutableOrEmbeddedContentAndUnknownAttributes() throws Exception {
        assertRejected("""
                {"type":"doc","content":[{"type":"image","attrs":{"src":"https://example.com/x.png"}}]}
                """);
        assertRejected("""
                {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"x","marks":[{"type":"code"}]}]}]}
                """);
        assertRejected("""
                {"type":"doc","content":[{"type":"paragraph","attrs":{"onclick":"alert(1)"},"content":[{"type":"text","text":"x"}]}]}
                """);
    }

    @Test
    void rejectsUnsafeLinksAndMalformedStructure() throws Exception {
        assertRejected("""
                {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"x","marks":[{"type":"link","attrs":{"href":"javascript:alert(1)"}}]}]}]}
                """);
        assertRejected("""
                {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"x","marks":[{"type":"link","attrs":{"href":"data:text/plain,x"}}]}]}]}
                """);
        assertRejected("""
                {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"x","marks":[{"type":"link","attrs":{"href":"file:///tmp/x"}}]}]}]}
                """);
        assertRejected("""
                {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"x","marks":[{"type":"link","attrs":{"href":"https://example.com","title":"unexpected"}}]}]}]}
                """);
        assertRejected("""
                {"type":"doc","content":[{"type":"table","content":[{"type":"tableRow","content":[
                  {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null,"align":"left"},"content":[{"type":"paragraph","content":[{"type":"text","text":"x"}]}]}
                ]}]}]}
                """);
        assertRejected("""
                {"type":"paragraph","content":[{"type":"text","text":"not a doc"}]}
                """);
        assertRejected("""
                {"type":"doc","content":[{"type":"table","content":[{"type":"tableRow","content":[]}]}]}
                """);
    }

    @Test
    void rejectsEmptyAndOversizedPlainText() throws Exception {
        assertRejected("""
                {"type":"doc","content":[{"type":"paragraph","content":[]}]}
                """);
        JsonNode oversized = json("""
                {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"%s"}]}]}
                """
                .formatted("x".repeat(RichDescriptionValidator.MAX_PLAIN_TEXT_LENGTH + 1)));
        assertThatThrownBy(() -> validator.validate(oversized)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsTablesBeyondTheApprovedDimensions() throws Exception {
        String cell = """
                {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"x"}]}]}
                """.strip();
        assertRejected("{\"type\":\"doc\",\"content\":[{\"type\":\"table\",\"content\":[{\"type\":\"tableRow\",\"content\":["
                + String.join(",", java.util.Collections.nCopies(21, cell)) + "]}]}]}");
    }

    private JsonNode json(String value) throws Exception { return mapper.readTree(value); }

    private void assertRejected(String value) throws Exception {
        JsonNode document = json(value);
        assertThatThrownBy(() -> validator.validate(document)).isInstanceOf(ResponseStatusException.class);
    }
}
