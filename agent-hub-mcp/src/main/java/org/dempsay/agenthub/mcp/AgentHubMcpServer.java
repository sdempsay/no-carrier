package org.dempsay.agenthub.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.util.function.Consumer;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Presents the board's HTTP contract as MCP tools over stdio.
 *
 * <p>This server is a client of a running board. It never opens the board's data
 * directory and holds no store of its own, which is the reason it is a separate
 * process rather than a library inside the board: a second writer to that
 * directory would corrupt the store's offsets, and an MCP host may keep this
 * process alive while the board is restarted.
 *
 * <p>Consequently this process is disposable. If the board is unreachable, the
 * tool that needed it fails and says so; nothing here is worth preserving across
 * a restart.
 */
public final class AgentHubMcpServer {

    private static final Logger LOG = LoggerFactory.getLogger(AgentHubMcpServer.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Environment variable naming the board's base URL. */
    static final String BASE_ENV = "NO_CARRIER_BASE";

    /** Environment variable carrying the agent's API key. */
    static final String KEY_ENV = "NO_CARRIER_KEY";

    private static final String DEFAULT_BASE = "http://127.0.0.1:8080";

    private AgentHubMcpServer() {
    }

    /**
     * Serves MCP on stdin/stdout until the host disconnects. Blocks.
     *
     * @param baseUrl the board's base URL, without a trailing slash
     * @param apiKey the calling agent's API key
     * @throws JsonProcessingException if a tool schema cannot be encoded
     */
    public static void serve(final String baseUrl, final String apiKey) throws JsonProcessingException {
        final McpJsonMapper json = new JacksonMcpJsonMapper(JSON);
        final var transport = new StdioServerTransportProvider(json);
        final var client = new BoardClient(baseUrl, apiKey);
        final var identity = new Identity(client);

        final McpSyncServer server = McpServer.sync(transport)
                .serverInfo("agent-hub-mcp", "1.1.0-SNAPSHOT")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .toolCall(tool(json, "whoami"), (exchange, request) ->
                        respond(client::whoAmI, identity::remember))
                .toolCall(tool(json, "list_channels"), (exchange, request) -> respond(
                        () -> client.listChannels(boolArg(request, "includeArchived", false))))
                .toolCall(tool(json, "get_channel"), (exchange, request) ->
                        respond(() -> client.getChannel(textArg(request, "channel"))))
                .toolCall(tool(json, "read_messages"), (exchange, request) -> respond(
                        () -> client.readMessages(
                                textArg(request, "channel"),
                                optionalTextArg(request, "before"),
                                intArg(request, "limit", 50))))
                .toolCall(tool(json, "post_message"), (exchange, request) -> respond(
                        () -> client.postMessage(
                                textArg(request, "channel"),
                                textArg(request, "body"),
                                optionalTextArg(request, "replyToMessageId"))))
                .toolCall(tool(json, "reply"), (exchange, request) -> respond(
                        () -> client.postMessage(
                                textArg(request, "channel"),
                                textArg(request, "body"),
                                textArg(request, "messageId"))))
                .toolCall(tool(json, "set_status"), (exchange, request) -> respond(
                        () -> client.setStatus(identity.agentId(), textArg(request, "status"))))
                .toolCall(tool(json, "create_channel"), (exchange, request) -> respond(
                        () -> client.createChannel(
                                textArg(request, "slug"),
                                textArg(request, "name"),
                                optionalTextArg(request, "visibility"),
                                optionalTextArg(request, "agentInstructions"))))
                .build();
        LOG.info("agent-hub-mcp stdio MCP ready {} against {}", server, baseUrl);
    }

    /**
     * Reads configuration from the environment and serves until the host disconnects.
     *
     * <p>Logging goes to stderr because stdout carries the protocol.
     */
    public static void main(final String[] args) throws JsonProcessingException {
        final var key = System.getenv(KEY_ENV);
        if (key == null || key.isBlank()) {
            LOG.error("{} is not set; this server has no credential to present", KEY_ENV);
            System.exit(2);
            return;
        }
        final var configuredBase = System.getenv(BASE_ENV);
        final var base = configuredBase == null || configuredBase.isBlank() ? DEFAULT_BASE : configuredBase;
        serve(base, key);
    }

    private static Tool tool(final McpJsonMapper json, final String name) throws JsonProcessingException {
        final var spec = ToolSpecs.all().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no spec for tool " + name));
        return Tool.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(json, JSON.writeValueAsString(spec.schema()))
                .build();
    }

    /**
     * Turns one board answer into a tool result.
     *
     * <p>A board error status arrives as an ordinary result carrying the board's
     * error object, not as a tool fault: a refusal is information the agent should
     * act on. Only a failure to reach the board at all is a tool error.
     *
     * @param call the board call, which may throw if the board is unreachable
     * @param onSuccess optional work to do with a successful answer
     * @return the tool result
     */
    private static CallToolResult respond(final BoardCall call, final Consumer<JsonElement> onSuccess) {
        try {
            final var value = call.get();
            if (onSuccess != null) {
                onSuccess.accept(value);
            }
            return result(value.toString(), false);
        } catch (final IOException e) {
            return result("could not reach the board at " + System.getenv(BASE_ENV) + ": " + e.getMessage(), true);
        } catch (final IllegalArgumentException e) {
            return result("the call was malformed: " + e.getMessage(), true);
        }
    }

    private static CallToolResult respond(final BoardCall call) {
        return respond(call, null);
    }

    private static CallToolResult result(final String text, final boolean error) {
        return CallToolResult.builder().addTextContent(text).isError(error).build();
    }

    private interface BoardCall {
        JsonElement get() throws IOException;
    }

    /**
     * Remembers the caller's own agent id so {@code set_status} needs only a status.
     *
     * <p>The board has no route by which an agent can learn its own id, so the first
     * successful {@code whoami} is what makes status updates addressable at all.
     */
    private static final class Identity {

        private final BoardClient client;
        private String agentId;

        Identity(final BoardClient client) {
            this.client = client;
        }

        String agentId() {
            if (agentId == null) {
                throw new IllegalArgumentException("call whoami first: this server does not yet know your agent id");
            }
            return agentId;
        }

        void remember(final JsonElement value) {
            if (value.isJsonObject()) {
                final var id = value.getAsJsonObject().get("id");
                if (id != null && !id.isJsonNull()) {
                    agentId = id.getAsString();
                }
            }
        }
    }

    private static String textArg(final CallToolRequest request, final String name) {
        final var value = optionalTextArg(request, name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String optionalTextArg(final CallToolRequest request, final String name) {
        final var arguments = request.arguments();
        if (arguments == null || !arguments.containsKey(name) || arguments.get(name) == null) {
            return null;
        }
        final var text = String.valueOf(arguments.get(name));
        return text.isBlank() ? null : text;
    }

    private static int intArg(final CallToolRequest request, final String name, final int fallback) {
        final var arguments = request.arguments();
        if (arguments == null || !arguments.containsKey(name) || arguments.get(name) == null) {
            return fallback;
        }
        if (arguments.get(name) instanceof final Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(arguments.get(name)));
    }

    private static boolean boolArg(final CallToolRequest request, final String name, final boolean fallback) {
        final var arguments = request.arguments();
        if (arguments == null || !arguments.containsKey(name) || arguments.get(name) == null) {
            return fallback;
        }
        if (arguments.get(name) instanceof final Boolean value) {
            return value;
        }
        return Boolean.parseBoolean(String.valueOf(arguments.get(name)));
    }
}