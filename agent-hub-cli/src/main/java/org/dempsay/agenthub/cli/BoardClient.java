package org.dempsay.agenthub.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonObject;
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
     * What the board issued for a newly created agent.
     *
     * @param agentId the agent's metadata id, needed to address its own status and
     *        heartbeat endpoints
     * @param apiKey the plaintext credential, never recoverable after this
     */
    record CreatedAgent(String agentId, String apiKey) {
    }

    private static String describe(final String body) {
        if (body == null || body.isBlank()) {
            return "no detail";
        }
        return body.length() > 300 ? body.substring(0, 300) + "..." : body;
    }
}