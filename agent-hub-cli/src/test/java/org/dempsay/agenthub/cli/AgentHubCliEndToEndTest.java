package org.dempsay.agenthub.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.dempsay.agenthub.app.HubRuntime;
import org.dempsay.agenthub.app.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test of the two commands against a real board.
 *
 * <p>The failure this guards against is a credential the CLI writes but the server
 * cannot use, or a server that ignores the administrator the operator just made and
 * quietly generates one of its own.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
class AgentHubCliEndToEndTest {
    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    @Test
    void registersAnAgentWithTheKeyAdminInitCreated(@TempDir final Path dir) throws Exception {
        final var init = cli("admin", "init", "--data-dir", dir.toString(), "--generate");
        assertEquals(0, init.exit(), () -> "admin init failed: " + init.err());
        final var adminKey = extractKey(init.out());

        try (var runtime = HubRuntime.start(AppConfig.onDisk(dir, 0))) {
            assertNull(runtime.bootstrapAdminKey(),
                    "the server generated a key even though admin init already made one");

            final var base = "http://127.0.0.1:" + runtime.port();
            final var register = cli("agent", "register", "--name", "bob", "--kind", "automated",
                    "--base", base, "--key", adminKey);
            assertEquals(0, register.exit(), () -> "register failed: " + register.err());

            final var bobKey = extractKey(register.out());
            assertNotEquals(adminKey, bobKey, "the registered agent must not inherit the admin key");

            assertEquals(201, createAgent(base, adminKey), "the administrator key must mint agents");
            assertEquals(403, createAgent(base, bobKey),
                    "a registered agent authenticates but must not manage agents");
            assertEquals(401, createAgent(base, "not-a-real-key-at-all"),
                    "an unknown key must not authenticate");
        }
    }

    @Test
    void writesTheCredentialWhereTheAgentCanReadIt(@TempDir final Path dir) throws Exception {
        final var init = cli("admin", "init", "--data-dir", dir.toString(), "--generate");
        assertEquals(0, init.exit(), () -> "admin init failed: " + init.err());
        final var adminKey = extractKey(init.out());

        final var credentialFile = dir.resolve("bob.env");
        try (var runtime = HubRuntime.start(AppConfig.onDisk(dir, 0))) {
            final var register = cli("agent", "register", "--name", "bob",
                    "--base", "http://127.0.0.1:" + runtime.port(), "--key", adminKey,
                    "--out", credentialFile.toString());
            assertEquals(0, register.exit(), () -> "register failed: " + register.err());
            assertFalse(register.out().contains(extractKeyFromFile(credentialFile)),
                    "the key must not also be printed when it was written to a file");
        }

        final var bobKey = extractKeyFromFile(credentialFile);
        final var bobId = credentialFileValue(credentialFile, "NO_CARRIER_AGENT_ID");
        assertDoesNotThrow(() -> UUID.fromString(bobId),
                () -> "the file must carry a usable agent id: " + bobId);

        try (var runtime = HubRuntime.start(AppConfig.onDisk(dir, 0))) {
            assertEquals(403, createAgent("http://127.0.0.1:" + runtime.port(), bobKey),
                    "the written key must authenticate as a non-administrator");

            final var who = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + runtime.port()
                    + "/api/v1/agents/me")).header("Authorization", "Bearer " + bobKey).GET().build();
            final var me = JsonParser.parseString(
                            HttpClient.newHttpClient().send(who, HttpResponse.BodyHandlers.ofString()).body())
                    .getAsJsonObject();
            assertEquals(bobId, me.get("id").getAsString(),
                    "the written agent id must be the one /agents/me reports");
        }
    }

    @Test
    void refusesToRegisterWithANonAdministratorKey(@TempDir final Path dir) throws Exception {
        final var init = cli("admin", "init", "--data-dir", dir.toString(), "--generate");
        assertEquals(0, init.exit());
        final var adminKey = extractKey(init.out());

        try (var runtime = HubRuntime.start(AppConfig.onDisk(dir, 0))) {
            final var base = "http://127.0.0.1:" + runtime.port();
            final var register = cli("agent", "register", "--name", "carol",
                    "--base", base, "--key", adminKey);
            assertEquals(0, register.exit());
            final var bobKey = extractKey(register.out());

            final var second = cli("agent", "register", "--name", "dave",
                    "--base", base, "--key", bobKey);
            assertEquals(1, second.exit(), "a non-administrator must not be able to register agents");
            assertTrue(second.err().contains("403"), () -> "unexpected error: " + second.err());
        }
    }

    private static String credentialFileValue(final Path file, final String name) throws Exception {
        for (final var line : Files.readString(file, StandardCharsets.UTF_8).split("\\R")) {
            final var trimmed = line.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring(name.length() + 1);
            }
        }
        throw new IllegalStateException("no " + name + " in " + file);
    }

    private static String extractKeyFromFile(final Path file) throws Exception {
        return credentialFileValue(file, "NO_CARRIER_KEY");
    }

    private static int createAgent(final String base, final String key) throws Exception {
        final var request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/api/v1/agents"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"probe\",\"kind\":\"automated\"}"))
                .build();
        final var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    private Result cli(final String... args) {
        outBytes.reset();
        errBytes.reset();
        final var exit = AgentHubCli.run(args, out, err, TestConsoles.nonInteractive());
        return new Result(exit, outBytes.toString(StandardCharsets.UTF_8), errBytes.toString(StandardCharsets.UTF_8));
    }

    /**
     * Pulls the credential out of whatever the command printed. Both shapes end with
     * the key after a colon: "API key: x" and "API key for bob: x".
     */
    private static String extractKey(final String text) {
        for (final var line : text.split("\\R")) {
            final var trimmed = line.trim();
            if (trimmed.startsWith("API key:")) {
                return trimmed.substring("API key:".length()).trim();
            }
            if (trimmed.startsWith("API key for ")) {
                final var colon = trimmed.lastIndexOf(':');
                if (colon >= 0 && colon < trimmed.length() - 1) {
                    return trimmed.substring(colon + 1).trim();
                }
            }
        }
        throw new IllegalStateException("no API key in output: " + text);
    }

    private record Result(int exit, String out, String err) {
    }
}