package org.dempsay.agenthub.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import org.dempsay.agenthub.app.HubRuntime;
import org.dempsay.agenthub.app.config.AppConfig;
import org.dempsay.utils.exceptional.api.ExceptionalResource;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;
import org.junit.jupiter.api.Test;

/**
 * The wake command talks to a running board and does not print the secret.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 */
class WakeAdminCommandTest {
    private static final String SECRET = "wk-cli-secret-not-printed";

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    @Test
    void setsTheDelayAndAWakeUrlWithoutPrintingTheSecret() {
        final var opened = ExceptionalSupplier.of(() -> {
            final var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/wake", exchange -> {
                final var handled = ExceptionalSupplier.of(() -> {
                    exchange.getRequestBody().readAllBytes();
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return Boolean.TRUE;
                }).execute();
                assertTrue(handled.wasNoError());
            });
            server.start();
            return server;
        }).execute();
        assertTrue(opened.wasNoError());
        final var stub = opened.response();
        final var ran = ExceptionalResource.<HubRuntime, Boolean>of(
                () -> HubRuntime.start(AppConfig.inMemoryForTests(0)),
                runtime -> {
            final var base = "http://127.0.0.1:" + runtime.port();
            final var adminKey = runtime.bootstrapAdminKey();
            final var agentId = whoAmI(base, adminKey);
            final var url = "http://127.0.0.1:" + stub.getAddress().getPort() + "/wake";

            final var delay = cli("admin", "wake", "delay", "--base", base, "--key", adminKey);
            assertEquals(0, delay.exit(), delay.err());
            assertTrue(delay.out().contains("wakeDelaySeconds: 30"), delay.out());

            final var setDelay = cli("admin", "wake", "delay", "--seconds", "0", "--base", base, "--key", adminKey);
            assertEquals(0, setDelay.exit(), setDelay.err());
            assertTrue(setDelay.out().contains("wakeDelaySeconds: 0"), setDelay.out());

            final var set = cli("admin", "wake", "set", "--agent", agentId, "--url", url, "--wake-key", SECRET,
                    "--base", base, "--key", adminKey);
            assertEquals(0, set.exit(), set.err());
            assertTrue(set.out().contains("wakeConfigured: true"), set.out());
            assertTrue(set.out().contains("wakeUrlHost: 127.0.0.1"), set.out());
            assertFalse(set.out().contains(SECRET), set.out());
            assertFalse(set.err().contains(SECRET), set.err());

            final var show = cli("admin", "wake", "show", "--agent", agentId, "--base", base, "--key", adminKey);
            assertEquals(0, show.exit(), show.err());
            assertFalse(show.out().contains(SECRET));
            assertFalse(show.err().contains(SECRET));

            final var tested = cli("admin", "wake", "test", "--agent", agentId, "--base", base, "--key", adminKey);
            assertEquals(0, tested.exit(), tested.err());
            assertTrue(tested.out().contains("statusCode: 204"), tested.out());
            assertFalse(tested.out().contains(SECRET));
            assertFalse(tested.err().contains(SECRET));

            final var cleared = cli("admin", "wake", "clear", "--agent", agentId, "--base", base, "--key", adminKey);
            assertEquals(0, cleared.exit(), cleared.err());
            assertTrue(cleared.out().contains("wakeConfigured: false"), cleared.out());
            assertFalse(cleared.out().contains(SECRET));
            return Boolean.TRUE;
                }).always(() -> stub.stop(0)).execute();
        assertTrue(ran.wasNoError());
    }

    @Test
    void refusesANonAdministrator() {
        final var ran = ExceptionalResource.<HubRuntime, Boolean>of(
                () -> HubRuntime.start(AppConfig.inMemoryForTests(0)),
                runtime -> {
            final var base = "http://127.0.0.1:" + runtime.port();
            final var registered = cli("agent", "register", "--name", "peyton", "--base", base,
                    "--key", runtime.bootstrapAdminKey());
            assertEquals(0, registered.exit(), registered.err());
            final var agentKey = extractApiKey(registered.out());
            final var agentId = extractAgentId(registered.out());

            final var denied = cli("admin", "wake", "set", "--agent", agentId, "--url", "http://127.0.0.1:9/wake",
                    "--wake-key", SECRET, "--base", base, "--key", agentKey);
            assertEquals(1, denied.exit(), denied.out());
            assertTrue(denied.err().contains("FORBIDDEN"), denied.err());
            assertFalse(denied.out().contains(SECRET));
            assertFalse(denied.err().contains(SECRET));
            return Boolean.TRUE;
                }).execute();
        assertTrue(ran.wasNoError());
    }

    private String whoAmI(final String base, final String key) {
        final var request = HttpRequest.newBuilder(URI.create(base + "/api/v1/agents/me"))
                .header("Authorization", "Bearer " + key)
                .GET()
                .build();
        final var response = ExceptionalSupplier.of(() ->
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())).execute();
        assertTrue(response.wasNoError());
        assertEquals(200, response.response().statusCode());
        return JsonParser.parseString(response.response().body()).getAsJsonObject().get("id").getAsString();
    }

    private Result cli(final String... args) {
        outBytes.reset();
        errBytes.reset();
        final var exit = AgentHubCli.run(args, out, err, TestConsoles.nonInteractive());
        return new Result(exit, outBytes.toString(StandardCharsets.UTF_8), errBytes.toString(StandardCharsets.UTF_8));
    }

    private static String extractApiKey(final String text) {
        for (final var line : text.split("\\R")) {
            final var trimmed = line.trim();
            if (trimmed.startsWith("API key:")) {
                return trimmed.substring("API key:".length()).trim();
            }
        }
        return fail("no API key in output: " + text);
    }

    private static String extractAgentId(final String text) {
        for (final var line : text.split("\\R")) {
            final var trimmed = line.trim();
            if (trimmed.startsWith("Agent id:")) {
                return trimmed.substring("Agent id:".length()).trim();
            }
        }
        return fail("no agent id in output: " + text);
    }

    private record Result(int exit, String out, String err) {
    }
}
