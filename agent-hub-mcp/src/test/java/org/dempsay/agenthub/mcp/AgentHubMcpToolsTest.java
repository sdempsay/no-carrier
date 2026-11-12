package org.dempsay.agenthub.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.dempsay.agenthub.app.HubRuntime;
import org.dempsay.agenthub.app.config.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the MCP tool layer against a real board over HTTP.
 *
 * <p>These tests stop short of spawning a stdio transport and driving the protocol,
 * because what is worth checking here is that the tools speak the board's contract
 * correctly. The board is the only party that can contradict that claim, so the
 * board is what the tests run against. Protocol framing belongs to the SDK.
 */
class AgentHubMcpToolsTest {

    @TempDir
    Path dataDir;

    private final HttpClient http = HttpClient.newHttpClient();

    private HubRuntime runtime;
    private String adminKey;
    private String baseUrl;

    @BeforeEach
    void startBoard() throws Exception {
        runtime = HubRuntime.start(AppConfig.onDisk(dataDir, 0));
        adminKey = runtime.bootstrapAdminKey();
        baseUrl = "http://127.0.0.1:" + runtime.port();
    }

    @AfterEach
    void stopBoard() {
        if (runtime != null) {
            runtime.close();
        }
    }

    @Test
    void resolvesItsOwnIdentityFromTheCredentialItWasGiven() throws Exception {
        final var tester = createAgent("mcp-tester", "automated");

        final var me = client(tester.key()).whoAmI();

        assertEquals(tester.id(), me.getAsJsonObject().get("id").getAsString());
        assertEquals("mcp-tester", me.getAsJsonObject().get("name").getAsString());
    }

    @Test
    void refusesAKeyTheBoardDoesNotRecognise() throws Exception {
        final var answer = client("ah_not-a-real-key").whoAmI();

        assertEquals("UNAUTHORIZED", code(answer));
    }

    @Test
    void listsAndReadsChannels() throws Exception {
        createChannel("general");

        final var channels = client(adminKey).listChannels(false);
        final var general = client(adminKey).getChannel("general");

        assertTrue(channels.getAsJsonArray().toString().contains("general"));
        assertEquals("general", general.getAsJsonObject().get("slug").getAsString());
    }

    @Test
    void surfacesAChannelsAdvisoryInstructions() throws Exception {
        createChannel("quiet");
        admin("PUT", "/api/v1/channels/quiet",
                "{\"agentInstructions\":\"Ask before posting between 22:00 and 06:00 UTC.\"}");

        final var channel = client(adminKey).getChannel("quiet");

        assertEquals("Ask before posting between 22:00 and 06:00 UTC.",
                channel.getAsJsonObject().get("agentInstructions").getAsString());
    }

    @Test
    void postsAndReadsBackAMessage() throws Exception {
        createChannel("general");

        final var posted = client(adminKey).postMessage("general", "hello from the tool layer", null);
        final var read = client(adminKey).readMessages("general", null, 50);

        assertEquals("hello from the tool layer", posted.getAsJsonObject().get("body").getAsString());
        assertTrue(read.toString().contains("hello from the tool layer"));
    }

    @Test
    void replyingAttachesTheMessageToTheParentThread() throws Exception {
        createChannel("general");
        final var parent = client(adminKey).postMessage("general", "the question", null);
        final var parentId = parent.getAsJsonObject().get("id").getAsString();

        final var reply = client(adminKey).postMessage("general", "the answer", parentId);

        final var stored = reply.getAsJsonObject();
        assertEquals(parentId, stored.get("threadId").getAsString());
        assertEquals(parentId, stored.get("parentMessageId").getAsString());
    }

    @Test
    void pagesBackwardsWithACursor() throws Exception {
        createChannel("general");
        client(adminKey).postMessage("general", "first", null);
        client(adminKey).postMessage("general", "second", null);

        final var tail = client(adminKey).readMessages("general", null, 1);
        final var newest = tail.getAsJsonArray().get(0).getAsJsonObject();
        final var cursor = newest.get("cursor").getAsString();
        final var older = client(adminKey).readMessages("general", cursor, 1);

        assertTrue(tail.toString().contains("second"));
        assertTrue(older.toString().contains("first"));
    }

    @Test
    void answersAnUnreadableCursorWithAnErrorObject() throws Exception {
        createChannel("general");

        final var read = client(adminKey).readMessages("general", "not-a-real-cursor", 50);

        assertEquals("VALIDATION_FAILED", code(read));
    }

    @Test
    void aRefusalIsReturnedAsAnAnswerRatherThanAFault() throws Exception {
        createChannel("private-room", "private");
        final var outsider = createAgent("outsider", "automated");

        final var posted = client(outsider.key()).postMessage("private-room", "let me in", null);

        assertEquals("FORBIDDEN", code(posted));
    }

    @Test
    void aMissingChannelIsAnsweredNotHung() throws Exception {
        final var missing = client(adminKey).getChannel("no-such-channel");

        assertEquals("NOT_FOUND", code(missing));
        assertTrue(missing.getAsJsonObject().get("message").getAsString().toLowerCase().contains("not found"));
    }

    @Test
    void setStatusAddressesTheAgentByItsOwnId() throws Exception {
        final var agent = createAgent("statusful", "automated");

        final var updated = client(agent.key()).setStatus(agent.id(), "busy");

        assertNull(code(updated), () -> "the board refused a self-addressed status update: " + updated);
        assertEquals("busy", updated.getAsJsonObject().get("declaredStatus").getAsString());
    }

    @Test
    void reportsAnUnreachableBoardRatherThanHanging() throws Exception {
        final int deadPort;
        try (var socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }

        final long startedAt = System.nanoTime();
        assertThrows(IOException.class,
                () -> new BoardClient("http://127.0.0.1:" + deadPort, adminKey).whoAmI());
        final long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertTrue(elapsedMillis < 10_000L,
                () -> "a dead board should be reported quickly, not after a timeout: " + elapsedMillis + "ms");
    }

    @Test
    void toleratesABoardUrlWithATrailingSlash() throws Exception {
        final var trailing = new BoardClient(baseUrl + "/", adminKey);

        assertEquals("admin", trailing.whoAmI().getAsJsonObject().get("name").getAsString());
    }

    @Test
    void sendsTheBearerCredentialOnEveryCall() throws Exception {
        createChannel("general");

        final var viaTool = client(adminKey).getChannel("general");
        final var viaRaw = JsonParser.parseString(
                raw("GET", "/api/v1/channels/general", null, adminKey));

        assertEquals(viaRaw.getAsJsonObject().get("id"), viaTool.getAsJsonObject().get("id"));
    }

    private BoardClient client(final String key) {
        return new BoardClient(baseUrl, key);
    }

    /**
     * Reads the error code the board reports.
     *
     * <p>The board answers an error with a code such as {@code NOT_FOUND} rather than
     * a numeric status, so this is what distinguishes one refusal from another. A
     * forbidden post and a missing channel are both failures, and an agent needs to
     * be able to tell them apart.
     *
     * @param element a board answer
     * @return the error code, or null if the answer was not an error
     */
    private static String code(final JsonElement element) {
        final var body = element.getAsJsonObject();
        return body.has("code") ? body.get("code").getAsString() : null;
    }

    private void createChannel(final String slug) throws Exception {
        createChannel(slug, "public");
    }

    private void createChannel(final String slug, final String visibility) throws Exception {
        admin("POST", "/api/v1/channels",
                "{\"slug\":\"" + slug + "\",\"name\":\"" + slug + "\",\"visibility\":\"" + visibility + "\"}");
    }

    private Credential createAgent(final String name, final String kind) throws Exception {
        final var created = JsonParser.parseString(
                admin("POST", "/api/v1/agents",
                        "{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\"}"))
                .getAsJsonObject();
        return new Credential(
                created.getAsJsonObject("agent").get("id").getAsString(),
                created.get("apiKey").getAsString());
    }

    /**
     * An agent the test registered, with the credential the board issued for it.
     *
     * @param id the agent's metadata id
     * @param key its plaintext API key
     */
    private record Credential(String id, String key) {
    }

    private String admin(final String method, final String path, final String body) throws Exception {
        return raw(method, path, body, adminKey);
    }

    /**
     * Makes a raw board call, so tests can assert on the contract without going
     * through the code under test.
     *
     * @param method the HTTP method
     * @param path the path
     * @param body the request body, or null
     * @param key the bearer credential
     * @return the response body
     * @throws IOException if the call cannot be made
     * @throws InterruptedException if the call is interrupted
     */
    private String raw(final String method, final String path, final String body, final String key)
            throws IOException, InterruptedException {
        final var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + key);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }
}