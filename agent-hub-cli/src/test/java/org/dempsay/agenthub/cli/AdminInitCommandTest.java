package org.dempsay.agenthub.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.dempsay.agenthub.core.HubDefaults;
import org.dempsay.agenthub.core.service.NoOpNotificationPort;
import org.dempsay.agenthub.store.HubServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@code agent-hub admin init}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
class AdminInitCommandTest {
    private static final String LONG_KEY = "correct-horse-battery-staple";

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    @Test
    void refusesWhenNoKeyCanBeObtained(@TempDir final Path dir) {
        final var exit = command(TestConsoles.nonInteractive()).run(dir.toString(), null, null, false);

        assertEquals(2, exit);
        assertTrue(err().contains("no API key was supplied"));
    }

    @Test
    void rejectsAShortKey(@TempDir final Path dir) {
        final var exit = command(TestConsoles.nonInteractive()).run(dir.toString(), null, "short", false);

        assertEquals(2, exit);
        assertTrue(err().contains("at least"));
    }

    @Test
    void createsAnAdministratorTheKeyAuthenticatesFor(@TempDir final Path dir) {
        final var exit = command(TestConsoles.nonInteractive()).run(dir.toString(), null, LONG_KEY, false);

        assertEquals(0, exit);
        assertTrue(out().contains("Created administrator 'admin'"));

        final var graph = graph(dir);
        final var principal = graph.auth().authenticate(LONG_KEY);
        assertTrue(principal.wasNoError());
        assertTrue(principal.response().isAdmin());
        assertTrue(graph.authorization().canManageAgents(principal.response()),
                "a key from admin init must be able to register agents");

        final var agent = graph.agents().get(principal.response().agentId());
        assertTrue(agent.wasNoError());
        assertEquals("admin", agent.response().resource().name());
    }

    @Test
    void showsAGeneratedKeyExactlyOnce(@TempDir final Path dir) {
        final var exit = command(TestConsoles.nonInteractive()).run(dir.toString(), null, null, true);

        assertEquals(0, exit);
        final var printed = out();
        assertTrue(printed.contains("API key: ah_"));
        assertTrue(printed.contains("shown once"));
    }

    @Test
    void doesNotEchoAKeyTheOperatorSupplied(@TempDir final Path dir) {
        final var exit = command(TestConsoles.nonInteractive()).run(dir.toString(), null, LONG_KEY, false);

        assertEquals(0, exit);
        assertFalse(out().contains(LONG_KEY));
    }

    @Test
    void refusesToRunTwice(@TempDir final Path dir) {
        assertEquals(0, command(TestConsoles.nonInteractive()).run(dir.toString(), null, LONG_KEY, false));

        final var second = command(TestConsoles.nonInteractive()).run(dir.toString(), null, "another-key-of-sufficient-length", false);

        assertEquals(1, second);
        assertTrue(err().contains("already exists"));
    }

    @Test
    void leavesExactlyOneAdministratorForTheServerToFind(@TempDir final Path dir) {
        assertEquals(0, command(TestConsoles.nonInteractive()).run(dir.toString(), null, LONG_KEY, false));

        final var graph = graph(dir);
        assertTrue(graph.bootstrap().adminExists());
        final var listed = graph.agents().list();
        assertTrue(listed.wasNoError());
        assertEquals(1, listed.response().size());
    }

    @Test
    void storesOnlyAHashOfTheKey(@TempDir final Path dir) throws Exception {
        assertEquals(0, command(TestConsoles.nonInteractive()).run(dir.toString(), null, LONG_KEY, false));

        final var files = Files.walk(dir).filter(Files::isRegularFile).toList();
        assertFalse(files.isEmpty(), "nothing was written at all");
        for (final var file : files) {
            final var content = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(content.contains(LONG_KEY), "plaintext key found in " + file);
        }
    }

    @Test
    void acceptsACustomAdministratorName(@TempDir final Path dir) {
        final var exit = command(TestConsoles.nonInteractive()).run(dir.toString(), "operator", LONG_KEY, false);

        assertEquals(0, exit);
        assertTrue(out().contains("Created administrator 'operator'"));
    }

    @Test
    void usesTheKeyTheOperatorTypedWhenInteractive(@TempDir final Path dir) {
        final var exit = command(TestConsoles.answering(LONG_KEY)).run(dir.toString(), null, null, false);

        assertEquals(0, exit);
        assertTrue(graph(dir).auth().authenticate(LONG_KEY).wasNoError());
    }

    @Test
    void refusesWhenTheTypedKeysDisagree(@TempDir final Path dir) {
        final var mismatched = new CliConsole() {
            @Override
            public boolean interactive() {
                return true;
            }

            @Override
            public Optional<String> promptSecret(final String prompt) {
                return Optional.of(LONG_KEY);
            }

            @Override
            public Optional<String> promptSecretTwice(final String prompt, final String confirmPrompt) {
                return Optional.empty();
            }
        };

        final var exit = command(mismatched).run(dir.toString(), null, null, false);

        assertEquals(2, exit);
        assertTrue(err().contains("no API key was supplied"));
    }

    private AdminInitCommand command(final CliConsole console) {
        return new AdminInitCommand(out, err, console);
    }

    private org.dempsay.agenthub.store.HubServiceGraph graph(final Path dir) {
        return HubServiceFactory.create(true, dir, HubDefaults.DEFAULT_PBKDF2_ITERATIONS, new NoOpNotificationPort());
    }

    private String out() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }
}