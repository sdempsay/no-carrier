package org.dempsay.agenthub.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.dempsay.agenthub.app.config.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HTTP contract tests against a real server on an ephemeral port.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
class HttpApiTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private HubRuntime runtime;
    private HttpClient client;
    private String base;
    private String adminKey;

    @BeforeEach
    void setUp() throws IOException {
        runtime = HubRuntime.start(AppConfig.inMemoryForTests(0));
        client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        base = "http://127.0.0.1:" + runtime.port();
        adminKey = runtime.bootstrapAdminKey();
        assertNotNull(adminKey, "first start must bootstrap an administrator");
    }

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.close();
        }
    }

    @Test
    void healthIsOpenAndReady() throws Exception {
        final var response = get("/health", null, null);

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        final var body = json(response);
        assertEquals("UP", body.get("status").getAsString());
        assertTrue(body.get("ready").getAsBoolean());
        assertNotNull(response.headers().firstValue("X-Request-Id").orElse(null));
    }

    @Test
    void requestIdIsEchoedWhenValidAndMintedOtherwise() throws Exception {
        final var supplied = UUID.randomUUID().toString();
        final var echoed = get("/health", null, supplied);
        assertEquals(supplied, echoed.headers().firstValue("X-Request-Id").orElse(null));

        final var minted = get("/health", null, "not-a-uuid");
        final var value = minted.headers().firstValue("X-Request-Id").orElse(null);
        assertNotNull(value);
        assertFalse(supplied.equals(value));
        assertEquals(36, value.length());
    }

    @Test
    void missingCredentialsAreRejected() throws Exception {
        for (final var path : new String[] {"/api/v1/agents", "/api/v1/channels", "/api/v1/channels/general"}) {
            final var response = get(path, null, null);
            assertEquals(401, response.statusCode(), path);
            assertEquals("UNAUTHORIZED", json(response).get("code").getAsString());
        }
    }

    @Test
    void wrongKeyIsRejected() throws Exception {
        final var response = get("/api/v1/channels", "not-the-key", null);
        assertEquals(401, response.statusCode());
        assertEquals("UNAUTHORIZED", json(response).get("code").getAsString());
    }

    @Test
    void nonAdminCannotManageChannelsOrListAgents() throws Exception {
        final var agentKey = registerAgent("helper");

        final var channels = post("/api/v1/channels", "{\"slug\":\"general\",\"name\":\"General\"}", agentKey);
        assertEquals(403, channels.statusCode());
        assertEquals("FORBIDDEN", json(channels).get("code").getAsString());

        final var agents = get("/api/v1/agents", agentKey, null);
        assertEquals(403, agents.statusCode());
        assertEquals("FORBIDDEN", json(agents).get("code").getAsString());
    }

    @Test
    void adminRegistersAgentAndAgentAuthenticates() throws Exception {
        final var agentKey = registerAgent("scout");

        final var channels = get("/api/v1/channels", agentKey, null);
        assertEquals(200, channels.statusCode());
    }

    @Test
    void adminSeesAgents() throws Exception {
        registerAgent("scout");

        final var response = get("/api/v1/agents", adminKey, null);
        assertEquals(200, response.statusCode());
        assertEquals(2, array(response).size());
    }

    @Test
    void channelIsResolvableBySlugAndById() throws Exception {
        final var created = post("/api/v1/channels", "{\"slug\":\"general\",\"name\":\"General\"}", adminKey);
        assertEquals(201, created.statusCode());

        final var body = json(created);
        final var id = body.get("id").getAsString();
        assertEquals("general", body.get("slug").getAsString());
        assertEquals("/api/v1/channels/" + id, created.headers().firstValue("Location").orElse(null));

        final var bySlug = get("/api/v1/channels/general", adminKey, null);
        assertEquals(200, bySlug.statusCode());
        assertEquals(id, json(bySlug).get("id").getAsString());

        final var byId = get("/api/v1/channels/" + id, adminKey, null);
        assertEquals(200, byId.statusCode());
        assertEquals("general", json(byId).get("slug").getAsString());
    }

    @Test
    void duplicateChannelSlugConflicts() throws Exception {
        assertEquals(201, post("/api/v1/channels", "{\"slug\":\"general\",\"name\":\"General\"}", adminKey)
                .statusCode());

        final var duplicate = post("/api/v1/channels", "{\"slug\":\"general\",\"name\":\"Other\"}", adminKey);
        assertEquals(409, duplicate.statusCode());
        assertEquals("CONFLICT", json(duplicate).get("code").getAsString());
    }

    @Test
    void unknownChannelIsNotFound() throws Exception {
        final var response = get("/api/v1/channels/nope", adminKey, null);
        assertEquals(404, response.statusCode());
        assertEquals("NOT_FOUND", json(response).get("code").getAsString());
        assertNotNull(json(response).get("requestId").getAsString());
    }

    @Test
    void unknownRouteIsNotFound() throws Exception {
        final var response = get("/api/v1/nope", adminKey, null);
        assertEquals(404, response.statusCode());
        assertEquals("NOT_FOUND", json(response).get("code").getAsString());
    }

    @Test
    void messagePostAndRetrieveRoundTrip() throws Exception {
        final var channel = createChannel("general");

        final var posted = post("/api/v1/channels/general/messages",
                "{\"body\":\"hello from no-carrier\",\"format\":\"plain\"}", adminKey);
        assertEquals(201, posted.statusCode());

        final var created = json(posted);
        final var messageId = created.get("id").getAsString();
        assertEquals("/api/v1/messages/" + messageId, posted.headers().firstValue("Location").orElse(null));
        assertEquals("hello from no-carrier", created.get("body").getAsString());
        assertEquals(channel, created.get("channelId").getAsString());
        assertFalse(created.get("deleted").getAsBoolean());

        final var listed = get("/api/v1/channels/general/messages", adminKey, null);
        assertEquals(200, listed.statusCode());
        final var messages = array(listed);
        assertEquals(1, messages.size());
        assertEquals(messageId, messages.get(0).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void replyKeepsThreadRoot() throws Exception {
        createChannel("general");

        final var root = json(post("/api/v1/channels/general/messages", "{\"body\":\"root\"}", adminKey));
        final var rootId = root.get("id").getAsString();

        final var reply = post("/api/v1/channels/general/messages",
                "{\"body\":\"reply\",\"parentMessageId\":\"" + rootId + "\"}", adminKey);
        assertEquals(201, reply.statusCode());

        final var body = json(reply);
        assertEquals(rootId, body.get("threadId").getAsString());
        assertEquals(rootId, body.get("parentMessageId").getAsString());
    }

    @Test
    void messagesPaginateWithLimit() throws Exception {
        createChannel("general");
        for (int i = 0; i < 4; i++) {
            assertEquals(201, post("/api/v1/channels/general/messages", "{\"body\":\"m" + i + "\"}", adminKey)
                    .statusCode());
        }

        final var page = get("/api/v1/channels/general/messages?limit=2", adminKey, null);
        assertEquals(200, page.statusCode());
        assertEquals(2, array(page).size());

        final var invalid = get("/api/v1/channels/general/messages?limit=500", adminKey, null);
        assertEquals(422, invalid.statusCode());
        assertFalse(json(invalid).getAsJsonArray("violations").isEmpty());
    }

    @Test
    void oversizedMessageIsRejectedBeforePersistence() throws Exception {
        createChannel("general");

        final var big = "x".repeat(runtime.config().maxMessageSize() + 1);
        final var response = post("/api/v1/channels/general/messages",
                "{\"body\":\"" + big + "\"}", adminKey);
        assertEquals(413, response.statusCode());
        assertEquals("PAYLOAD_TOO_LARGE", json(response).get("code").getAsString());

        final var listed = get("/api/v1/channels/general/messages", adminKey, null);
        assertEquals(0, array(listed).size());
    }

    @Test
    void emptyMessageIsRejectedWithViolations() throws Exception {
        createChannel("general");

        final var response = post("/api/v1/channels/general/messages", "{\"body\":\"  \"}", adminKey);
        assertEquals(422, response.statusCode());

        final var error = json(response);
        assertEquals("VALIDATION_FAILED", error.get("code").getAsString());
        assertEquals("body", error.getAsJsonArray("violations").get(0).getAsJsonObject().get("field").getAsString());
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        createChannel("general");

        final var response = post("/api/v1/channels/general/messages", "{not json", adminKey);
        assertEquals(422, response.statusCode());
    }

    @Test
    void heartbeatUpdatesLastSeenOnly() throws Exception {
        final var agent = json(post("/api/v1/agents", "{\"name\":\"pulse\",\"kind\":\"automated\"}", adminKey));
        final var agentId = agent.getAsJsonObject("agent").get("id").getAsString();
        final var before = agent.getAsJsonObject("agent");

        final var response = post("/api/v1/agents/" + agentId + "/heartbeat", null, adminKey);
        assertEquals(200, response.statusCode());

        final var after = json(response);
        assertNull(before.get("lastSeenAt"), "a new agent has never been seen");
        assertNotNull(after.get("lastSeenAt"), "heartbeat records lastSeenAt");
        assertEquals(before.get("declaredStatus").getAsString(), after.get("declaredStatus").getAsString());
        assertEquals(before.get("lifecycleState").getAsString(), after.get("lifecycleState").getAsString());
    }

    @Test
    void agentCannotHeartbeatForAnotherAgent() throws Exception {
        final var agentId = json(post("/api/v1/agents", "{\"name\":\"one\",\"kind\":\"automated\"}", adminKey))
                .getAsJsonObject("agent").get("id").getAsString();
        final var otherKey = registerAgent("two");

        final var response = post("/api/v1/agents/" + agentId + "/heartbeat", null, otherKey);
        assertEquals(403, response.statusCode());
    }

    @Test
    void privateChannelIsHiddenFromAgents() throws Exception {
        final var created = post("/api/v1/channels",
                "{\"slug\":\"backroom\",\"name\":\"Backroom\",\"visibility\":\"private\"}", adminKey);
        assertEquals(201, created.statusCode());
        final var id = json(created).get("id").getAsString();

        final var agentKey = registerAgent("nosy");
        assertEquals(403, get("/api/v1/channels/" + id, agentKey, null).statusCode());
        assertEquals(403, get("/api/v1/channels/backroom", agentKey, null).statusCode());

        final var listed = get("/api/v1/channels", agentKey, null);
        assertEquals(200, listed.statusCode());
        assertEquals(0, array(listed).size());
    }

    private String registerAgent(final String name) throws Exception {
        final var response = post("/api/v1/agents",
                "{\"name\":\"" + name + "\",\"kind\":\"automated\"}", adminKey);
        assertEquals(201, response.statusCode(), response.body());
        return json(response).get("apiKey").getAsString();
    }

    private String createChannel(final String slug) throws Exception {
        final var response = post("/api/v1/channels", "{\"slug\":\"" + slug + "\",\"name\":\"General\"}", adminKey);
        assertEquals(201, response.statusCode());
        return json(response).get("id").getAsString();
    }

    private HttpResponse<String> get(final String path, final String key, final String requestId) throws Exception {
        final var builder = HttpRequest.newBuilder(URI.create(base + path)).GET().timeout(TIMEOUT);
        if (key != null) {
            builder.header("Authorization", "Bearer " + key);
        }
        if (requestId != null) {
            builder.header("X-Request-Id", requestId);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(final String path, final String body, final String key) throws Exception {
        final var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(TIMEOUT);
        final var publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        builder.header("Content-Type", "application/json");
        if (key != null) {
            builder.header("Authorization", "Bearer " + key);
        }
        return client.send(builder.POST(publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(final HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static JsonArray array(final HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonArray();
    }

    @Test
    void unknownRouteWithQueryStillNotFound() throws Exception {
        assertEquals(404, get("/api/v1/channels/general/nope", adminKey, null).statusCode());
        assertNull(json(get("/health", null, null)).get("violations"));
    }
}
