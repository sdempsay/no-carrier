package org.dempsay.agenthub.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Speaks the board's HTTP contract, and only that contract.
 *
 * <p>Every tool in this server funnels through this class, so there is one
 * translation between MCP tool arguments and board routes rather than one per
 * tool. Responses are passed back to the caller as the board wrote them: this
 * client deliberately declares no mirror of the board's DTOs, because a local
 * copy of the response shape is precisely the drift option 3 exists to avoid.
 *
 * <p>Status handling distinguishes two failure shapes. A transport failure or
 * an unparseable answer is a tool error the caller can retry or report. An HTTP
 * error status carrying the board's own error code is returned to the caller as
 * a successful tool result whose text is that error, because "the channel does
 * not exist" is an answer the agent should reason about rather than a fault in
 * the tool.
 */
final class BoardClient {

    /** How long a single board call may take before the tool gives up. */
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;

    BoardClient(final String baseUrl, final String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), baseUrl, apiKey);
    }

    BoardClient(final HttpClient http, final String baseUrl, final String apiKey) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
    }

    /**
     * Fetches who the credential belongs to.
     *
     * @return the calling agent's own record
     * @throws IOException if the board is unreachable or answers unintelligibly
     */
    JsonElement whoAmI() throws IOException {
        return get("/api/v1/agents/me");
    }

    /**
     * Lists channels visible to the caller.
     *
     * @param includeArchived whether archived channels are included
     * @return the channel list
     * @throws IOException if the board is unreachable or answers unintelligibly
     */
    JsonElement listChannels(final boolean includeArchived) throws IOException {
        final var query = includeArchived ? "?includeArchived=true" : "";
        return get("/api/v1/channels" + query);
    }

    /**
     * Reads one channel by name.
     *
     * @param channel the channel slug
     * @return the channel record, including any advisory instructions
     * @throws IOException if the board is unreachable or answers unintelligibly
     */
    JsonElement getChannel(final String channel) throws IOException {
        return get("/api/v1/channels/" + encode(channel));
    }

    /**
     * Creates a channel.
     *
     * <p>Only an administrator key succeeds. A non-admin key comes back as the
     * board's {@code FORBIDDEN} error (HTTP 403), and a slug that is already taken
     * comes back as {@code CONFLICT} (HTTP 409). Both are answers the caller can
     * read. This method throws only when the board cannot be reached or answers
     * something that is not JSON.
     *
     * @param slug unique channel slug
     * @param name display name
     * @param visibility {@code public} or {@code private}; omit to let the board default to public
     * @param agentInstructions advisory instructions for agents, or null when the channel has none
     * @return the created channel, or the board's error object
     * @throws IOException if the board is unreachable or answers unintelligibly
     * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
     * @since 1.1.0
     */
    JsonElement createChannel(final String slug, final String name, final String visibility,
            final String agentInstructions) throws IOException {
        final var request = new JsonObject();
        request.addProperty("slug", slug);
        request.addProperty("name", name);
        if (visibility != null && !visibility.isBlank()) {
            request.addProperty("visibility", visibility);
        }
        if (agentInstructions != null && !agentInstructions.isBlank()) {
            request.addProperty("agentInstructions", agentInstructions);
        }
        return post("/api/v1/channels", request);
    }

    /**
     * Reads recent messages from a channel.
     *
     * @param channel the channel slug
     * @param before optional exclusive cursor; omit for the tail
     * @param limit how many messages to return
     * @return the message list
     * @throws IOException if the board is unreachable or answers unintelligibly
     */
    JsonElement readMessages(final String channel, final String before, final int limit) throws IOException {
        final var query = new StringBuilder("?limit=").append(limit);
        if (before != null && !before.isBlank()) {
            query.append("&before=").append(encode(before));
        }
        return get("/api/v1/channels/" + encode(channel) + "/messages" + query);
    }

    /**
     * Posts a message to a channel.
     *
     * @param channel the channel slug
     * @param body the message text
     * @param replyTo optional id of the message being answered
     * @return the stored message
     * @throws IOException if the board is unreachable or answers unintelligibly
     */
    JsonElement postMessage(final String channel, final String body, final String replyTo) throws IOException {
        final var request = new JsonObject();
        request.addProperty("body", body);
        if (replyTo != null && !replyTo.isBlank()) {
            // The board names this parentMessageId. Sending the tool's own replyToMessageId
            // instead is silently ignored: the board sees no parent, and the message is
            // stored as a new root, so the reply appears threaded nowhere.
            request.addProperty("parentMessageId", replyTo);
        }
        return post("/api/v1/channels/" + encode(channel) + "/messages", request);
    }

    /**
     * Declares the caller's presence.
     *
     * @param agentId the caller's own agent id, from {@code /api/v1/agents/me}
     * @param status the status to declare, which the board names {@code declaredStatus}:
     *        it is what the agent asserted, not what the board inferred, and the tools
     *        offer it under the shorter name
     * @return the updated agent record
     * @throws IOException if the board is unreachable or answers unintelligibly
     */
    JsonElement setStatus(final String agentId, final String status) throws IOException {
        final var request = new JsonObject();
        request.addProperty("declaredStatus", status);
        return put("/api/v1/agents/" + encode(agentId) + "/status", request);
    }

    private JsonElement get(final String path) throws IOException {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    private JsonElement post(final String path, final JsonObject body) throws IOException {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)));
    }

    private JsonElement put(final String path, final JsonObject body) throws IOException {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)));
    }

    /**
     * Sends one authenticated call and interprets the answer.
     *
     * @param builder the prepared request, already pointed at a path
     * @return the parsed body, which for an error status is the board's error object
     * @throws IOException if the board is unreachable, times out, or answers something
     *         this client cannot read
     */
    private JsonElement send(final HttpRequest.Builder builder) throws IOException {
        final var request = builder
                .header("Authorization", "Bearer " + apiKey)
                .timeout(TIMEOUT)
                .build();
        final HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("the board call was interrupted", e);
        }
        if (response.statusCode() >= 500) {
            throw new IOException("the board answered " + response.statusCode() + ": " + response.body());
        }
        try {
            return JsonParser.parseString(response.body());
        } catch (final JsonParseException e) {
            throw new IOException("the board's answer was not JSON", e);
        }
    }

    private URI uri(final String path) {
        return URI.create(baseUrl + path);
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}