package org.dempsay.agenthub.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import org.dempsay.agenthub.app.config.AppConfig;
import org.dempsay.agenthub.core.port.WakeCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Wake pings against a local stub. The post path must return before the stub answers,
 * and the secret must not appear in any board response.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
class WakePingTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String SECRET = "wk-secret-do-not-leak";

    private HubRuntime runtime;
    private HttpClient client;
    private String base;
    private String adminKey;
    private HttpServer stub;
    private final List<Hit> hits = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        runtime = HubRuntime.start(AppConfig.inMemoryForTests(0));
        client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        base = "http://127.0.0.1:" + runtime.port();
        adminKey = runtime.bootstrapAdminKey();
        assertNotNull(adminKey);
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/wake", exchange -> {
            final var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            hits.add(new Hit(exchange.getRequestMethod(), body, exchange.getRequestHeaders().getFirst("X-Agent-Hub-Wake-Key"),
                    exchange.getRequestHeaders().getFirst("X-Test-Wake")));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        stub.start();
    }

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.close();
        }
        if (stub != null) {
            stub.stop(0);
        }
    }

    @Test
    void homeChannelAndThreadRepliesPingAndIrrelevantMessagesDoNot() throws Exception {
        final var peyton = register("peyton");
        final var nanobot = register("nanobot");
        createChannel("peyton");
        createChannel("general");
        setDelay(0);
        setWake(peyton.id(), stubUrl(), SECRET, null);

        postMessage("general", "nothing to see", nanobot.key(), null);
        Thread.sleep(600);
        assertEquals(0, hits.size(), "a post outside the home channel and outside a thread they joined does not ping");

        postMessage("peyton", "hello peyton", nanobot.key(), null);
        assertEquals(1, awaitCount(1, 2_000));
        assertEquals("POST", hits.get(0).method());
        assertEquals("{}", hits.get(0).body());
        assertFalse(hits.get(0).body().contains("hello peyton"));
        assertEquals(SECRET, hits.get(0).defaultHeader());

        final var root = postMessage("general", "peyton starts a thread", peyton.key(), null);
        Thread.sleep(400);
        assertEquals(1, hits.size(), "the author is not pinged for their own message");

        postMessage("general", "reply in peyton's thread", nanobot.key(), root);
        assertEquals(2, awaitCount(2, 2_000), "a reply in a thread the agent started pings them");

        final var otherRoot = postMessage("general", "someone else's topic", nanobot.key(), null);
        Thread.sleep(400);
        assertEquals(2, hits.size(), "a reply target the agent has not touched does not ping");
        postMessage("general", "still not peyton's thread", nanobot.key(), otherRoot);
        Thread.sleep(400);
        assertEquals(2, hits.size());

        postMessage("peyton", "peyton talking to themselves", peyton.key(), null);
        Thread.sleep(400);
        assertEquals(2, hits.size(), "a home-channel post by the agent does not ping that agent");
    }

    @Test
    void aBurstProducesOnePingAndDelayZeroSendsWithoutWaiting() throws Exception {
        final var peyton = register("peyton");
        final var nanobot = register("nanobot");
        createChannel("peyton");
        setWake(peyton.id(), stubUrl(), SECRET, null);

        final var defaults = get("/api/v1/settings/wake", adminKey);
        assertEquals(200, defaults.statusCode());
        assertEquals(30, json(defaults).get("wakeDelaySeconds").getAsInt());

        setDelay(2);
        final var started = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            final var posted = post("/api/v1/channels/peyton/messages",
                    "{\"body\":\"burst " + i + "\"}", nanobot.key());
            assertEquals(201, posted.statusCode(), posted.body());
        }
        final var postingMillis = (System.nanoTime() - started) / 1_000_000L;
        assertTrue(postingMillis < 1_000, "scheduling the ping must not wait out the delay; took " + postingMillis + "ms");
        assertEquals(1, awaitCount(1, 3_000));
        Thread.sleep(400);
        assertEquals(1, hits.size(), "messages inside the delay add no extra pings");

        hits.clear();
        setDelay(0);
        final var immediateStart = System.nanoTime();
        final var immediate = post("/api/v1/channels/peyton/messages", "{\"body\":\"now\"}", nanobot.key());
        final var immediateMillis = (System.nanoTime() - immediateStart) / 1_000_000L;
        assertEquals(201, immediate.statusCode());
        assertTrue(immediateMillis < 1_000, "delay 0 still returns the post before the ping; took " + immediateMillis + "ms");
        assertEquals(1, awaitCount(1, 2_000));
    }

    @Test
    void theSecretNeverAppearsInResponsesAndIsSentOnlyOnThePing() throws Exception {
        final var peyton = register("peyton");
        final var nanobot = register("nanobot");
        createChannel("peyton");
        setDelay(0);
        final var bodies = new ArrayList<String>();

        final var logs = new CopyOnWriteArrayList<String>();
        final var handler = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                if (record.getMessage() != null) {
                    logs.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.ALL);
        final var previous = WakePinger.LOG.getLevel();
        WakePinger.LOG.setLevel(Level.ALL);
        WakePinger.LOG.addHandler(handler);
        try {
            bodies.add(setWake(peyton.id(), stubUrl(), SECRET, "X-Test-Wake").body());
            bodies.add(get("/api/v1/agents/" + peyton.id() + "/wake", adminKey).body());
            bodies.add(put("/api/v1/agents/" + peyton.id() + "/wake",
                    "{\"wakeUrl\":\"" + stubUrl() + "\"}", adminKey).body());
            bodies.add(get("/api/v1/agents", adminKey).body());
            bodies.add(get("/api/v1/agents/" + peyton.id(), adminKey).body());
            bodies.add(get("/api/v1/agents/me", peyton.key()).body());
            bodies.add(put("/api/v1/agents/" + peyton.id() + "/status",
                    "{\"declaredStatus\":\"busy\"}", peyton.key()).body());
            bodies.add(post("/api/v1/channels/peyton/messages", "{\"body\":\"visible\"}", nanobot.key()).body());
            assertEquals(1, awaitCount(1, 2_000));
            bodies.add(post("/api/v1/agents/" + peyton.id() + "/wake/test", "{}", adminKey).body());
            bodies.add(get("/api/v1/settings/wake", adminKey).body());
        } finally {
            WakePinger.LOG.removeHandler(handler);
            WakePinger.LOG.setLevel(previous);
        }

        for (final var body : bodies) {
            assertFalse(body.contains(SECRET), body);
            assertFalse(body.contains("wakeKey"), body);
        }
        for (final var line : logs) {
            assertFalse(line.contains(SECRET), line);
        }
        assertEquals(SECRET, hits.get(0).customHeader(), "the ping itself carries the secret, in the configured header");
        assertEquals("{}", hits.get(0).body());
        final var view = json(bodies.get(0));
        assertTrue(view.get("wakeConfigured").getAsBoolean());
        assertEquals("127.0.0.1", view.get("wakeUrlHost").getAsString());
        assertEquals("X-Test-Wake", view.get("wakeHeaderName").getAsString());
        final var tested = json(bodies.get(bodies.size() - 2));
        assertEquals(204, tested.get("statusCode").getAsInt());
    }

    @Test
    void anUnreachableWakeUrlDoesNotFailOrBlockThePost() throws Exception {
        final var peyton = register("peyton");
        final var nanobot = register("nanobot");
        createChannel("peyton");
        setDelay(0);
        final int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        setWake(peyton.id(), "http://127.0.0.1:" + closedPort + "/wake", SECRET, null);

        final var started = System.nanoTime();
        final var posted = post("/api/v1/channels/peyton/messages", "{\"body\":\"still stored\"}", nanobot.key());
        final var elapsed = (System.nanoTime() - started) / 1_000_000L;
        assertEquals(201, posted.statusCode(), posted.body());
        assertTrue(elapsed < 1_000, "the post must not wait on the wake call; took " + elapsed + "ms");
        assertFalse(posted.body().contains(SECRET));

        final var health = get("/health", null);
        assertEquals(200, health.statusCode());
        final var messages = get("/api/v1/channels/peyton/messages", nanobot.key());
        assertEquals(200, messages.statusCode());
        assertTrue(messages.body().contains("still stored"));
    }

    @Test
    void onlyAnAdministratorCanChangeWakeSettings() throws Exception {
        final var peyton = register("peyton");
        final var body = "{\"wakeUrl\":\"" + stubUrl() + "\",\"wakeKey\":\"" + SECRET + "\"}";
        for (final var call : List.of(
                put("/api/v1/agents/" + peyton.id() + "/wake", body, peyton.key()),
                get("/api/v1/agents/" + peyton.id() + "/wake", peyton.key()),
                delete("/api/v1/agents/" + peyton.id() + "/wake", peyton.key()),
                post("/api/v1/agents/" + peyton.id() + "/wake/test", "{}", peyton.key()),
                get("/api/v1/settings/wake", peyton.key()),
                put("/api/v1/settings/wake", "{\"wakeDelaySeconds\":0}", peyton.key()))) {
            assertEquals(403, call.statusCode(), call.body());
            assertEquals("FORBIDDEN", json(call).get("code").getAsString());
            assertFalse(call.body().contains(SECRET), call.body());
        }

        final var ftp = put("/api/v1/agents/" + peyton.id() + "/wake",
                "{\"wakeUrl\":\"ftp://example/wake\",\"wakeKey\":\"" + SECRET + "\"}", adminKey);
        assertEquals(422, ftp.statusCode());
        assertFalse(ftp.body().contains(SECRET));

        final var tooHigh = put("/api/v1/settings/wake", "{\"wakeDelaySeconds\":301}", adminKey);
        assertEquals(422, tooHigh.statusCode());
        final var negative = put("/api/v1/settings/wake", "{\"wakeDelaySeconds\":-1}", adminKey);
        assertEquals(422, negative.statusCode());

        setWake(peyton.id(), stubUrl(), SECRET, null);
        final var cleared = delete("/api/v1/agents/" + peyton.id() + "/wake", adminKey);
        assertEquals(200, cleared.statusCode());
        assertFalse(json(cleared).get("wakeConfigured").getAsBoolean());
        assertFalse(cleared.body().contains(SECRET));
    }

    @Test
    void redactStripsTheSecretAndTheUrl() {
        final var text = WakePinger.redact(
                new IOException("POST wk-secret-do-not-leak failed for http://127.0.0.1:9/wake"),
                new WakeCredentials("http://127.0.0.1:9/wake", "X-Agent-Hub-Wake-Key", SECRET));
        assertFalse(text.contains(SECRET));
        assertFalse(text.contains("http://127.0.0.1:9/wake"));
        assertTrue(text.contains("[redacted]"));
        assertTrue(text.contains("[url]"));
    }

    private String stubUrl() {
        return "http://127.0.0.1:" + stub.getAddress().getPort() + "/wake";
    }

    private void setDelay(final int seconds) throws Exception {
        final var response = put("/api/v1/settings/wake", "{\"wakeDelaySeconds\":" + seconds + "}", adminKey);
        assertEquals(200, response.statusCode(), response.body());
    }

    private HttpResponse<String> setWake(final String agentId, final String url, final String secret, final String header)
            throws Exception {
        final var payload = new StringBuilder("{\"wakeUrl\":\"").append(url).append("\"");
        if (secret != null) {
            payload.append(",\"wakeKey\":\"").append(secret).append("\"");
        }
        if (header != null) {
            payload.append(",\"wakeHeaderName\":\"").append(header).append("\"");
        }
        payload.append("}");
        final var response = put("/api/v1/agents/" + agentId + "/wake", payload.toString(), adminKey);
        assertEquals(200, response.statusCode(), response.body());
        return response;
    }

    private String postMessage(final String channel, final String body, final String key, final String parent)
            throws Exception {
        final var payload = parent == null
                ? "{\"body\":\"" + body + "\"}"
                : "{\"body\":\"" + body + "\",\"parentMessageId\":\"" + parent + "\"}";
        final var response = post("/api/v1/channels/" + channel + "/messages", payload, key);
        assertEquals(201, response.statusCode(), response.body());
        return json(response).get("id").getAsString();
    }

    private Agent register(final String name) throws Exception {
        final var response = post("/api/v1/agents", "{\"name\":\"" + name + "\",\"kind\":\"automated\"}", adminKey);
        assertEquals(201, response.statusCode(), response.body());
        final var body = json(response);
        return new Agent(body.getAsJsonObject("agent").get("id").getAsString(), body.get("apiKey").getAsString());
    }

    private void createChannel(final String slug) throws Exception {
        final var response = post("/api/v1/channels", "{\"slug\":\"" + slug + "\",\"name\":\"" + slug + "\"}", adminKey);
        assertEquals(201, response.statusCode(), response.body());
    }

    private int awaitCount(final int expected, final long timeoutMs) throws InterruptedException {
        final var deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (hits.size() >= expected) {
                return hits.size();
            }
            Thread.sleep(20);
        }
        return hits.size();
    }

    private HttpResponse<String> get(final String path, final String key) throws Exception {
        final var builder = HttpRequest.newBuilder(URI.create(base + path)).GET().timeout(TIMEOUT);
        if (key != null) {
            builder.header("Authorization", "Bearer " + key);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(final String path, final String body, final String key) throws Exception {
        return send("POST", path, body, key);
    }

    private HttpResponse<String> put(final String path, final String body, final String key) throws Exception {
        return send("PUT", path, body, key);
    }

    private HttpResponse<String> delete(final String path, final String key) throws Exception {
        return send("DELETE", path, null, key);
    }

    private HttpResponse<String> send(final String method, final String path, final String body, final String key)
            throws Exception {
        final var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(TIMEOUT)
                .header("Authorization", "Bearer " + key);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(final HttpResponse<String> response) {
        return json(response.body());
    }

    private static JsonObject json(final String body) {
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private record Agent(String id, String key) {
    }

    private record Hit(String method, String body, String defaultHeader, String customHeader) {
    }
}
