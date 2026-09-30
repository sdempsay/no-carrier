package org.dempsay.agenthub.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.dempsay.agenthub.app.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end smoke test: start the hub, bootstrap an administrator, register an
 * agent, open a public channel, exchange messages, then restart and confirm the
 * history survived against the filesystem.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
class SmokeTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void firstSliceEndToEndAcrossRestart(@TempDir final Path dataDir) throws Exception {
        String adminKey;
        String agentKey;
        String agentId;
        String channelId;
        String rootMessageId;
        String replyMessageId;

        try (final var runtime = HubRuntime.start(AppConfig.onDisk(dataDir, 0))) {
            final var client = client();

            adminKey = runtime.bootstrapAdminKey();
            assertNotNull(adminKey, "an empty store must bootstrap an administrator");

            final var health = get(client, runtime.port(), "/health", null);
            assertEquals(200, health.statusCode());

            final var scout = json(post(client, runtime.port(), "/api/v1/agents",
                    "{\"name\":\"scout\",\"kind\":\"automated\"}", adminKey));
            agentId = scout.getAsJsonObject("agent").get("id").getAsString();
            agentKey = scout.get("apiKey").getAsString();

            // The bootstrap admin and the registered agent are both visible.
            assertEquals(2, array(get(client, runtime.port(), "/api/v1/agents", adminKey)).size());

            channelId = json(post(client, runtime.port(), "/api/v1/channels",
                    "{\"slug\":\"general\",\"name\":\"General\",\"visibility\":\"public\"}", adminKey))
                    .get("id").getAsString();

            rootMessageId = json(post(client, runtime.port(), "/api/v1/channels/general/messages",
                    "{\"body\":\"Welcome to no-carrier\"}", adminKey)).get("id").getAsString();

            replyMessageId = json(post(client, runtime.port(), "/api/v1/channels/general/messages",
                    "{\"body\":\"Signing on\",\"parentMessageId\":\"" + rootMessageId + "\"}", agentKey))
                    .get("id").getAsString();

            final var heartbeat = json(post(client, runtime.port(), "/api/v1/agents/" + agentId + "/heartbeat", null,
                    agentKey));
            assertNotNull(heartbeat.get("lastSeenAt"));
            assertEquals("offline", heartbeat.get("declaredStatus").getAsString());

            assertEquals(2, array(get(client, runtime.port(), "/api/v1/channels/general/messages", agentKey)).size());
        }

        // Restart against the same data directory.
        try (final var runtime = HubRuntime.start(AppConfig.onDisk(dataDir, 0))) {
            assertNull(runtime.bootstrapAdminKey(), "an existing store must not re-bootstrap");

            final var client = client();

            final var channel = json(get(client, runtime.port(), "/api/v1/channels/general", agentKey));
            assertEquals(channelId, channel.get("id").getAsString());
            assertEquals("General", channel.get("name").getAsString());

            final var messages = array(get(client, runtime.port(), "/api/v1/channels/general/messages", agentKey));
            assertEquals(2, messages.size());

            final var reply = messages.get(0).getAsJsonObject();
            assertEquals("Signing on", reply.get("body").getAsString());
            assertEquals(rootMessageId, reply.get("threadId").getAsString());
            assertEquals(replyMessageId, reply.get("id").getAsString());

            final var root = messages.get(1).getAsJsonObject();
            assertEquals("Welcome to no-carrier", root.get("body").getAsString());

            // Credentials issued before the restart still authenticate.
            assertEquals(200, get(client, runtime.port(), "/api/v1/channels/general/messages", agentKey).statusCode());
            assertEquals(401, get(client, runtime.port(), "/api/v1/channels/general/messages", "stale-key").statusCode());

            // And the administrator can still see every agent.
            assertEquals(2, array(get(client, runtime.port(), "/api/v1/agents", adminKey)).size());
            assertTrue(runtime.config().persistent());
        }
    }

    @Test
    void inMemoryRuntimeIsIsolatedPerStart() throws Exception {
        try (final var first = HubRuntime.start(AppConfig.inMemoryForTests(0))) {
            final var client = client();
            final var key = first.bootstrapAdminKey();
            assertNotNull(key);
            assertEquals(201, post(client, first.port(), "/api/v1/channels",
                    "{\"slug\":\"general\",\"name\":\"General\"}", key).statusCode());
        }

        try (final var second = HubRuntime.start(AppConfig.inMemoryForTests(0))) {
            final var client = client();
            assertNotNull(second.bootstrapAdminKey(), "in-memory stores start empty");
            assertEquals(0, array(get(client, second.port(), "/api/v1/channels", second.bootstrapAdminKey())).size());
        }
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    private static HttpResponse<String> get(final HttpClient client, final int port, final String path,
            final String key) throws IOException, InterruptedException {
        return send(client, builder(port, path, key), null);
    }

    private static HttpResponse<String> post(final HttpClient client, final int port, final String path, final String body,
            final String key) throws IOException, InterruptedException {
        return send(client, builder(port, path, key),
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    }

    private static HttpRequest.Builder builder(final int port, final String path, final String key) {
        final var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(TIMEOUT);
        builder.header("Content-Type", "application/json");
        if (key != null) {
            builder.header("Authorization", "Bearer " + key);
        }
        return builder;
    }

    private static HttpResponse<String> send(final HttpClient client, final HttpRequest.Builder builder,
            final HttpRequest.BodyPublisher publisher) throws IOException, InterruptedException {
        final var request = publisher == null ? builder.GET().build() : builder.POST(publisher).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(final HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static JsonArray array(final HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonArray();
    }
}
