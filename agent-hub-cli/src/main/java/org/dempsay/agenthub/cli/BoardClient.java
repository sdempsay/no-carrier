package org.dempsay.agenthub.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Minimal client for the board's HTTP API.
 *
 * <p>Registration goes over HTTP rather than straight into the store: the board owns
 * the store while it is running, and two processes writing the same files is not
 * supported.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
final class BoardClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String baseUrl;

    BoardClient(final String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Creates an agent and returns the credential the board issued.
     *
     * @param adminKey key of an administrator
     * @param name agent name
     * @param kind agent kind, human or automated
     * @param ownerAgentId optional owner, or null
     * @return the new agent's id and API key
     * @throws IOException if the board is unreachable or answers with an error
     */
    CreatedAgent createAgent(final String adminKey, final String name, final String kind, final String ownerAgentId)
            throws IOException {
        final var payload = new JsonObject();
        payload.addProperty("name", name);
        payload.addProperty("kind", kind);
        if (ownerAgentId != null && !ownerAgentId.isBlank()) {
            payload.addProperty("ownerAgentId", ownerAgentId.trim());
        }

        final var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/agents"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + adminKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        final HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while contacting the board", e);
        }

        if (response.statusCode() != 201) {
            throw new IOException("the board refused the request (HTTP " + response.statusCode() + "): "
                    + describe(response.body()));
        }

        final var body = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!body.has("apiKey") || body.get("apiKey").isJsonNull()) {
            throw new IOException("the board's response carried no API key");
        }
        if (!body.has("agent") || !body.getAsJsonObject("agent").has("id")) {
            throw new IOException("the board's response carried no agent id");
        }
        return new CreatedAgent(
                body.getAsJsonObject("agent").get("id").getAsString(),
                body.get("apiKey").getAsString());
    }

    /**
     * Reads the safe wake view for an agent. The secret is not part of the result.
     *
     * @param adminKey administrator key
     * @param agentId agent id
     * @return the answer, or a failure when the board cannot be reached
     * @since 1.0.0
     */
    ExceptionalResponse<HttpJson> showWake(final String adminKey, final String agentId) {
        return exchange("GET", "/api/v1/agents/" + agentId + "/wake", adminKey, null);
    }

    /**
     * Stores a wake URL. A null secret or header is omitted, so the board keeps the stored value.
     *
     * @param adminKey administrator key
     * @param agentId agent id
     * @param wakeUrl http or https URL
     * @param wakeKey secret, or null to leave the stored secret
     * @param headerName header name, or null to leave the stored name
     * @return the answer, or a failure when the board cannot be reached
     * @since 1.0.0
     */
    ExceptionalResponse<HttpJson> setWake(final String adminKey, final String agentId, final String wakeUrl,
            final String wakeKey, final String headerName) {
        final var payload = new JsonObject();
        payload.addProperty("wakeUrl", wakeUrl);
        if (wakeKey != null) {
            payload.addProperty("wakeKey", wakeKey);
        }
        if (headerName != null) {
            payload.addProperty("wakeHeaderName", headerName);
        }
        return exchange("PUT", "/api/v1/agents/" + agentId + "/wake", adminKey, payload.toString());
    }

    /**
     * Clears an agent's wake settings.
     *
     * @param adminKey administrator key
     * @param agentId agent id
     * @return the answer, or a failure when the board cannot be reached
     * @since 1.0.0
     */
    ExceptionalResponse<HttpJson> clearWake(final String adminKey, final String agentId) {
        return exchange("DELETE", "/api/v1/agents/" + agentId + "/wake", adminKey, null);
    }

    /**
     * Reads the board-wide wake delay.
     *
     * @param adminKey administrator key
     * @return the answer, or a failure when the board cannot be reached
     * @since 1.0.0
     */
    ExceptionalResponse<HttpJson> wakeDelay(final String adminKey) {
        return exchange("GET", "/api/v1/settings/wake", adminKey, null);
    }

    /**
     * Stores the board-wide wake delay.
     *
     * @param adminKey administrator key
     * @param seconds 0 through 300
     * @return the answer, or a failure when the board cannot be reached
     * @since 1.0.0
     */
    ExceptionalResponse<HttpJson> setWakeDelay(final String adminKey, final int seconds) {
        final var payload = new JsonObject();
        payload.addProperty("wakeDelaySeconds", seconds);
        return exchange("PUT", "/api/v1/settings/wake", adminKey, payload.toString());
    }

    /**
     * Asks the board to POST one wake ping now.
     *
     * @param adminKey administrator key
     * @param agentId agent id
     * @return the answer, or a failure when the board cannot be reached
     * @since 1.0.0
     */
    ExceptionalResponse<HttpJson> testWake(final String adminKey, final String agentId) {
        return exchange("POST", "/api/v1/agents/" + agentId + "/wake/test", adminKey, "{}");
    }

    /**
     * One JSON answer from the board, including error statuses.
     *
     * @param status HTTP status
     * @param body parsed body, with {@code wakeKey} removed
     * @since 1.0.0
     */
    record HttpJson(int status, JsonObject body) {
    }

    /**
     * Wake settings the board is willing to show.
     *
     * @param configured whether a URL is stored
     * @param host host of that URL, or null
     * @param headerName header that carries the secret
     */
    record WakeView(boolean configured, String host, String headerName) {
    }

    /**
     * What the board issued for a newly created agent.
     *
     * @param agentId the agent's metadata id, needed to address its own status and
     *        heartbeat endpoints
     * @param apiKey the plaintext credential, never recoverable after this
     */
    record CreatedAgent(String agentId, String apiKey) {
    }

    private ExceptionalResponse<HttpJson> exchange(final String method, final String path, final String adminKey,
            final String json) {
        final var builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + adminKey);
        if (json == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        final ExceptionalResponse<HttpResponse<String>> response = ExceptionalSupplier.of(() ->
                client.send(builder.build(), HttpResponse.BodyHandlers.ofString())).execute();
        if (response.wasError()) {
            return ExceptionalResponse.failure();
        }
        final var http = response.response();
        final ExceptionalResponse<JsonObject> parsed = ExceptionalSupplier.of(() ->
                JsonParser.parseString(http.body()).getAsJsonObject()).execute();
        if (parsed.wasError()) {
            return ExceptionalResponse.failure();
        }
        parsed.response().remove("wakeKey");
        return ExceptionalResponse.success(new HttpJson(http.statusCode(), parsed.response()));
    }

    static WakeView wakeView(final JsonObject body) {
        final var host = body.has("wakeUrlHost") && !body.get("wakeUrlHost").isJsonNull()
                ? body.get("wakeUrlHost").getAsString()
                : null;
        final var header = body.has("wakeHeaderName") && !body.get("wakeHeaderName").isJsonNull()
                ? body.get("wakeHeaderName").getAsString()
                : null;
        final var configured = body.has("wakeConfigured") && body.get("wakeConfigured").getAsBoolean();
        return new WakeView(configured, host, header);
    }

    static String describe(final HttpJson http) {
        final var json = http.body();
        final var code = json.has("code") && !json.get("code").isJsonNull()
                ? json.get("code").getAsString()
                : "HTTP " + http.status();
        final var message = json.has("message") && !json.get("message").isJsonNull()
                ? json.get("message").getAsString()
                : "";
        return message.isBlank() ? code : code + ": " + message;
    }

    private static String describe(final String body) {
        if (body == null || body.isBlank()) {
            return "no detail";
        }
        return body.length() > 300 ? body.substring(0, 300) + "..." : body;
    }
}