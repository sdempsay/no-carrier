package org.dempsay.agenthub.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

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
     * @return configured flag, host, and header name
     * @throws IOException if the board refuses or cannot be reached
     */
    WakeView showWake(final String adminKey, final String agentId) throws IOException {
        return wakeView(send("GET", "/api/v1/agents/" + agentId + "/wake", adminKey, null));
    }

    /**
     * Stores a wake URL. A null secret or header is omitted, so the board keeps the stored value.
     *
     * @param adminKey administrator key
     * @param agentId agent id
     * @param wakeUrl http or https URL
     * @param wakeKey secret, or null to leave the stored secret
     * @param headerName header name, or null to leave the stored name
     * @return the safe view
     * @throws IOException if the board refuses or cannot be reached
     */
    WakeView setWake(final String adminKey, final String agentId, final String wakeUrl, final String wakeKey,
            final String headerName) throws IOException {
        final var payload = new JsonObject();
        payload.addProperty("wakeUrl", wakeUrl);
        if (wakeKey != null) {
            payload.addProperty("wakeKey", wakeKey);
        }
        if (headerName != null) {
            payload.addProperty("wakeHeaderName", headerName);
        }
        return wakeView(send("PUT", "/api/v1/agents/" + agentId + "/wake", adminKey, payload.toString()));
    }

    /**
     * Clears an agent's wake settings.
     *
     * @param adminKey administrator key
     * @param agentId agent id
     * @return the safe view
     * @throws IOException if the board refuses or cannot be reached
     */
    WakeView clearWake(final String adminKey, final String agentId) throws IOException {
        return wakeView(send("DELETE", "/api/v1/agents/" + agentId + "/wake", adminKey, null));
    }

    /**
     * Reads the board-wide wake delay.
     *
     * @param adminKey administrator key
     * @return delay in seconds
     * @throws IOException if the board refuses or cannot be reached
     */
    int wakeDelay(final String adminKey) throws IOException {
        return send("GET", "/api/v1/settings/wake", adminKey, null).get("wakeDelaySeconds").getAsInt();
    }

    /**
     * Stores the board-wide wake delay.
     *
     * @param adminKey administrator key
     * @param seconds 0 through 300
     * @return the stored delay
     * @throws IOException if the board refuses or cannot be reached
     */
    int setWakeDelay(final String adminKey, final int seconds) throws IOException {
        final var payload = new JsonObject();
        payload.addProperty("wakeDelaySeconds", seconds);
        return send("PUT", "/api/v1/settings/wake", adminKey, payload.toString()).get("wakeDelaySeconds").getAsInt();
    }

    /**
     * Asks the board to POST one wake ping now.
     *
     * @param adminKey administrator key
     * @param agentId agent id
     * @return the remote status, or an error string when the remote did not answer
     * @throws IOException if the board refuses or cannot be reached
     */
    WakeTest testWake(final String adminKey, final String agentId) throws IOException {
        final var body = send("POST", "/api/v1/agents/" + agentId + "/wake/test", adminKey, "{}");
        final Integer status = body.has("statusCode") && !body.get("statusCode").isJsonNull()
                ? body.get("statusCode").getAsInt()
                : null;
        final String error = body.has("error") && !body.get("error").isJsonNull()
                ? body.get("error").getAsString()
                : null;
        return new WakeTest(status, error);
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
     * Result of an administrator test ping.
     *
     * @param statusCode status from the wake URL, or null
     * @param error redacted error, or null
     */
    record WakeTest(Integer statusCode, String error) {
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

    private JsonObject send(final String method, final String path, final String adminKey, final String json)
            throws IOException {
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
        final HttpResponse<String> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while contacting the board", e);
        }
        if (response.statusCode() >= 400) {
            throw new IOException(describeError(response.statusCode(), response.body()));
        }
        final var parsed = JsonParser.parseString(response.body()).getAsJsonObject();
        parsed.remove("wakeKey");
        return parsed;
    }

    private static WakeView wakeView(final JsonObject body) {
        final var host = body.has("wakeUrlHost") && !body.get("wakeUrlHost").isJsonNull()
                ? body.get("wakeUrlHost").getAsString()
                : null;
        final var header = body.has("wakeHeaderName") && !body.get("wakeHeaderName").isJsonNull()
                ? body.get("wakeHeaderName").getAsString()
                : null;
        final var configured = body.has("wakeConfigured") && body.get("wakeConfigured").getAsBoolean();
        return new WakeView(configured, host, header);
    }

    private static String describeError(final int status, final String body) {
        if (body == null || body.isBlank()) {
            return "HTTP " + status;
        }
        try {
            final var json = JsonParser.parseString(body).getAsJsonObject();
            json.remove("wakeKey");
            final var code = json.has("code") ? json.get("code").getAsString() : "HTTP " + status;
            final var message = json.has("message") && !json.get("message").isJsonNull()
                    ? json.get("message").getAsString()
                    : "";
            return message.isBlank() ? code : code + ": " + message;
        } catch (final JsonParseException | IllegalStateException e) {
            return "HTTP " + status;
        }
    }

    private static String describe(final String body) {
        if (body == null || body.isBlank()) {
            return "no detail";
        }
        return body.length() > 300 ? body.substring(0, 300) + "..." : body;
    }
}