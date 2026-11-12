package org.dempsay.agenthub.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Guards the claims the tool descriptions make.
 *
 * <p>A tool description is the only documentation an agent reads, so a description
 * that is merely plausible is a defect: it will send agents to call something that
 * cannot work. These checks are deliberately about the descriptions rather than
 * about behaviour, because behaviour is the board's business and its tests cover
 * that.
 */
class ToolSpecsTest {

    @Test
    void offersTheToolsTheBoardCanActuallyServe() {
        final var names = ToolSpecs.all().stream().map(ToolSpecs.Spec::name).toList();

        assertEquals(List.of("whoami", "list_channels", "get_channel", "read_messages", "post_message", "reply",
                        "set_status"),
                names);
    }

    @Test
    void namesAreUniqueAndSnakeCased() {
        final var names = ToolSpecs.all().stream().map(ToolSpecs.Spec::name).toList();

        assertEquals(names.size(), names.stream().distinct().count());
        for (final var name : names) {
            assertTrue(name.matches("[a-z][a-z0-9_]*"), () -> "tool name is not snake_case: " + name);
        }
    }

    @Test
    void everySpecCarriesADescriptionAndAnObjectSchema() {
        for (final var spec : ToolSpecs.all()) {
            assertFalse(spec.description().isBlank(), () -> spec.name() + " has no description");
            assertEquals("object", spec.schema().get("type"), () -> spec.name() + " needs an object schema");
            assertEquals(Boolean.FALSE, spec.schema().get("additionalProperties"),
                    () -> spec.name() + " should reject arguments it does not declare");
            assertTrue(spec.schema().containsKey("properties"),
                    () -> spec.name() + " declares no arguments");
        }
    }

    @Test
    void everyRequiredArgumentIsAlsoDeclaredAsAProperty() {
        for (final var spec : ToolSpecs.all()) {
            final var properties = properties(spec);
            for (final var required : required(spec)) {
                assertTrue(properties.containsKey(required),
                        () -> spec.name() + " requires " + required + " but does not declare it");
            }
        }
    }

    @Test
    void everyDeclaredArgumentExplainsItself() {
        for (final var spec : ToolSpecs.all()) {
            properties(spec).forEach((name, definition) -> {
                @SuppressWarnings("unchecked")
                final var property = (Map<String, Object>) definition;
                assertTrue(property.containsKey("description"),
                        () -> spec.name() + " argument " + name + " has no description");
            });
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theStatusEnumIsExactlyWhatTheBoardAccepts() {
        @SuppressWarnings("unchecked")
        final var status = (Map<String, Object>) properties(specNamed("set_status")).get("status");
        @SuppressWarnings("unchecked")
        final var allowed = (List<String>) status.get("enum");

        assertEquals(List.of("online", "busy", "away", "offline"), allowed,
                "the board rejects any other status, so the enum must not offer one it will not take");
    }

    @Test
    void setStatusTellsTheAgentThatNothingExpiresOnItsOwn() {
        assertTrue(specNamed("set_status").description().contains("never expires"),
                "an agent that leaves a status behind will mislead the board, so the description must warn it");
    }

    @Test
    void getChannelPointsAtTheAdvisoryInstructions() {
        assertTrue(specNamed("get_channel").description().contains("agentInstructions"),
                "the advisory instructions are the reason to read a channel before posting to it");
    }

    @Test
    void offersNoToolThatCouldMintCredentials() {
        for (final var spec : ToolSpecs.all()) {
            assertFalse(spec.name().contains("register") || spec.name().contains("create_agent")
                            || spec.name().contains("key") || spec.name().contains("credential"),
                    () -> "an agent that can issue credentials can escalate past every other control: " + spec.name());
        }
    }

    @Test
    void readingAdviceMentionsTheCursorBecausePagingIsTheUsualCase() {
        assertTrue(specNamed("read_messages").description().contains("cursor"));
    }

    private static ToolSpecs.Spec specNamed(final String name) {
        return ToolSpecs.all().stream()
                .filter(spec -> spec.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no tool named " + name));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(final ToolSpecs.Spec spec) {
        return (Map<String, Object>) spec.schema().get("properties");
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(final ToolSpecs.Spec spec) {
        return (List<String>) spec.schema().getOrDefault("required", List.of());
    }
}
