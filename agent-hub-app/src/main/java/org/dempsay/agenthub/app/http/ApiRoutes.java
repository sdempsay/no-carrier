package org.dempsay.agenthub.app.http;

import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.dempsay.agenthub.api.model.AgentCredentialDto;
import org.dempsay.agenthub.api.model.AgentDto;
import org.dempsay.agenthub.api.model.ChannelDto;
import org.dempsay.agenthub.api.model.MessageDto;
import org.dempsay.agenthub.app.ChannelWakeups;
import org.dempsay.agenthub.store.HubServiceGraph;
import org.dempsay.agenthub.app.http.dto.ApiAgentDto;
import org.dempsay.agenthub.app.http.dto.ApiChannelDto;
import org.dempsay.agenthub.app.http.dto.ApiMessageDto;
import org.dempsay.agenthub.app.http.dto.CreateAgentRequest;
import org.dempsay.agenthub.app.http.dto.CreateChannelRequest;
import org.dempsay.agenthub.app.http.dto.CreateMessageRequest;
import org.dempsay.agenthub.app.http.dto.UpdateChannelRequest;
import org.dempsay.agenthub.app.http.dto.UpdateStatusRequest;
import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.agenthub.core.port.AuthPrincipal;
import org.dempsay.agenthub.core.service.MessageCursor;

/**
 * HTTP route handlers for the Agent Hub API.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class ApiRoutes {
    private static final String CHANNEL = "channel";
    private static final int MAX_AGENT_INSTRUCTIONS = 600;
    private static final String AGENT = "agent";
    private static final int MAX_LIMIT = 100;

    private static final String PUBLIC_VISIBILITY = "public";

    private static final List<String> DECLARED_STATUSES = List.of("online", "busy", "away", "offline");

    private static final int MAX_WAIT_SECONDS = 60;

    private static final String CONSOLE_RESOURCE = "/web/console.html";

    private final HttpAdapter http;
    private final HubServiceGraph services;
    private final int maxMessageSize;
    private final ChannelWakeups wakeups;

    public ApiRoutes(final HttpAdapter http, final HubServiceGraph services, final int maxMessageSize,
            final ChannelWakeups wakeups) {
        this.http = http;
        this.services = services;
        this.maxMessageSize = maxMessageSize;
        this.wakeups = wakeups;
    }

    public void register() {
        http.get("/health", context -> sendJson(context, 200, Map.of("status", "UP", "ready", true)));
        http.get("/index.html", this::handleConsole);
        http.get("/", this::handleConsole);

        http.get("/api/v1/agents", this::handleListAgents);
        http.get("/api/v1/agents/me", this::handleWhoAmI);
        http.post("/api/v1/agents", this::handleCreateAgent);
        http.post("/api/v1/agents/{" + AGENT + "}/heartbeat", this::handleHeartbeat);
        http.put("/api/v1/agents/{" + AGENT + "}/status", this::handleUpdateStatus);

        http.get("/api/v1/channels", this::handleListChannels);
        http.post("/api/v1/channels", this::handleCreateChannel);
        http.get("/api/v1/channels/{" + CHANNEL + "}", this::handleGetChannel);
        http.put("/api/v1/channels/{" + CHANNEL + "}", this::handleUpdateChannel);

        http.get("/api/v1/channels/{" + CHANNEL + "}/messages", this::handleListMessages);
        http.post("/api/v1/channels/{" + CHANNEL + "}/messages", this::handleCreateMessage);
    }

    /**
     * Serves the human console. No authentication is required to fetch the shell; the page
     * asks for an API key and keeps it in the browser, so the HTML itself contains no secrets.
     */
    private void handleConsole(final HttpAdapter.RequestContext context) throws IOException {
        try (final var in = ApiRoutes.class.getResourceAsStream(CONSOLE_RESOURCE)) {
            if (in == null) {
                notFound(context, "Console not packaged in this build");
                return;
            }
            final var html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            final var bytes = html.getBytes(StandardCharsets.UTF_8);
            final var headers = context.exchange().getResponseHeaders();
            headers.set("Content-Type", "text/html; charset=utf-8");
            headers.set("Cache-Control", "no-store");
            // The console holds a credential in browser storage; keep it out of shared caches.
            headers.set("Referrer-Policy", "no-referrer");
            context.exchange().sendResponseHeaders(200, bytes.length);
            try (final var out = context.exchange().getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private void handleListAgents(final HttpAdapter.RequestContext context) throws IOException {
        final var principal = authenticate(context);
        if (principal == null) {
            return;
        }
        if (!services.authorization().canManageAgents(principal)) {
            forbidden(context, "Listing agents requires administrator credentials");
            return;
        }

        final var result = services.agents().list();
        if (result.wasError()) {
            serverError(context, "Failed to list agents");
            return;
        }

        sendJson(context, 200, result.response().stream().map(ApiRoutes::toApiAgent).collect(Collectors.toList()));
    }


    /**
     * Resolves the calling agent from its own credential.
     *
     * <p>An agent cannot look itself up by id, because it does not know its id: the id
     * only appears in the response that created it. Without this route there is no way
     * for an agent to confirm its key works, learn its own id, or address the status
     * and heartbeat endpoints that require that id in the path.
     */
    private void handleWhoAmI(final HttpAdapter.RequestContext context) throws IOException {
        final var principal = authenticate(context);
        if (principal == null) {
            return;
        }

        final var found = services.agents().get(principal.agentId());
        if (found.wasError()) {
            sendError(context, 404, "NOT_FOUND", "The credential names an agent that no longer exists");
            return;
        }

        sendJson(context, 200, toApiAgent(found.response()));
    }

    private void handleCreateAgent(final HttpAdapter.RequestContext context) throws IOException {
        final var principal = authenticate(context);
        if (principal == null) {
            return;
        }
        if (!services.authorization().canManageAgents(principal)) {
            forbidden(context, "Creating agents requires administrator credentials");
            return;
        }

        final var request = readBody(context, CreateAgentRequest.class);
        if (request == null || isBlank(request.name()) || isBlank(request.kind())) {
            validationFailed(context, List.of(
                    new ErrorResponse.Violation("name", "name is required"),
                    new ErrorResponse.Violation("kind", "kind is required and must be human or automated")));
            return;
        }
        if (!"human".equals(request.kind()) && !"automated".equals(request.kind())) {
            validationFailed(context, List.of(new ErrorResponse.Violation("kind", "kind must be human or automated")));
            return;
        }

        final var created = services.agents().create(new AgentDto(
                request.name(),
                request.kind(),
                request.ownerAgentId(),
                request.provider(),
                request.model(),
                request.endpoint(),
                isBlank(request.declaredStatus()) ? "offline" : request.declaredStatus(),
                null,
                "active"));
        if (created.wasError()) {
            serverError(context, "Failed to create agent");
            return;
        }

        final var agentId = created.response().metadata().id();
        final var apiKey = UUID.randomUUID().toString().replace("-", "");
        final var credential = services.credentials().create(UUID.fromString(agentId),
                new AgentCredentialDto(agentId, "unset", request.name() + "-key", null, null, null, "active"), apiKey);
        if (credential.wasError()) {
            serverError(context, "Failed to create agent credential");
            return;
        }

        sendCreated(context, "/api/v1/agents/" + agentId, Map.of("agent", toApiAgent(created.response()), "apiKey", apiKey));
    }

    private void handleHeartbeat(final HttpAdapter.RequestContext context) throws IOException {
        final var principal = authenticate(context);
        if (principal == null) {
            return;
        }

        final var raw = context.pathVariables().get(AGENT);
        if (!HttpAdapter.isUuid(raw)) {
            notFound(context, "Agent not found");
            return;
        }

        final var agentId = UUID.fromString(raw);
        if (!principal.agentId().equals(agentId) && !principal.isAdmin()) {
            forbidden(context, "Cannot record a heartbeat for another agent");
            return;
        }

        final var existing = services.agents().get(agentId);
        if (existing.wasError()) {
            notFound(context, "Agent not found");
            return;
        }

        final var updated = services.agents().updateStatus(agentId, existing.response().resource().declaredStatus(),
                Instant.now().toString());
        if (updated.wasError()) {
            serverError(context, "Failed to record heartbeat");
            return;
        }

        sendJson(context, 200, toApiAgent(updated.response()));
    }

    /**
     * Declares the caller's own status. Unlike a heartbeat, this never touches {@code lastSeenAt},
     * so an agent can report that it is busy without appearing recently active.
     */
    private void handleUpdateStatus(final HttpAdapter.RequestContext context) throws IOException {
        final var principal = authenticate(context);
        if (principal == null) {
            return;
        }

        final var raw = context.pathVariables().get(AGENT);
        if (!HttpAdapter.isUuid(raw)) {
            notFound(context, "Agent not found");
            return;
        }
        final var agentId = UUID.fromString(raw);

        if (!services.authorization().canUpdateOwnStatus(principal, agentId) && !principal.isAdmin()) {
            forbidden(context, "Cannot change the status of another agent");
            return;
        }

        final var existing = services.agents().get(agentId);
        if (existing.wasError()) {
            notFound(context, "Agent not found");
            return;
        }

        final var request = readBody(context, UpdateStatusRequest.class);
        if (request == null || isBlank(request.declaredStatus())) {
            validationFailed(context,
                    List.of(new ErrorResponse.Violation("declaredStatus", "declaredStatus is required")));
            return;
        }
        if (!DECLARED_STATUSES.contains(request.declaredStatus())) {
            validationFailed(context, List.of(new ErrorResponse.Violation("declaredStatus",
                    "declaredStatus must be one of " + String.join(", ", DECLARED_STATUSES))));
            return;
        }

        final var updated = services.agents().updateStatus(agentId, request.declaredStatus(),
                existing.response().resource().lastSeenAt());
        if (updated.wasError()) {
            serverError(context, "Failed to update status");
            return;
        }

        sendJson(context, 200, toApiAgent(updated.response()));
    }

    private void handleListChannels(final HttpAdapter.RequestContext context) throws IOException {
        final var principal = authenticate(context);
        if (principal == null) {
            return;
        }

        final var result = services.channels().listPublic();
        if (result.wasError()) {
            serverError(context, "Failed to list channels");
            return;
        }

        sendJson(context, 200, result.response().stream().map(ApiRoutes::toApiChannel).collect(Collectors.toList()));
    }

    private void handleCreateChannel(final HttpAdapter.RequestContext context) throws IOException {
        final var principal = authenticate(context);
        if (principal == null) {
            return;
        }
        if (!services.authorization().canManageChannels(principal)) {
            forbidden(context, "Creating channels requires administrator credentials");
            return;
        }

        final var request = readBody(context, CreateChannelRequest.class);
        if (request == null || isBlank(request.slug()) || isBlank(request.name())) {
            validationFailed(context, List.of(
                    new ErrorResponse.Violation("slug", "slug is required"),
                    new ErrorResponse.Violation("name", "name is required")));
            return;
        }

        final var visibility = isBlank(request.visibility()) ? "public" : request.visibility();
        if (!"public".equals(visibility) && !"private".equals(visibility)) {
            validationFailed(context,
                    List.of(new ErrorResponse.Violation("visibility", "visibility must be public or private")));
            return;
        }

        final var agentInstructions = isBlank(request.agentInstructions()) ? null : request.agentInstructions();
        final var result = services.channels().create(new ChannelDto(request.slug(), request.name(), visibility,
                principal.agentId().toString(), false, null, null, null, null, agentInstructions));
        if (result.wasError()) {
            sendError(context, 409, "CONFLICT", "Channel slug is already taken");
            return;
        }

        final var channel = result.response();
        sendCreated(context, "/api/v1/channels/" + channel.metadata().id(), toApiChannel(channel));
    }


    private void handleUpdateChannel(final HttpAdapter.RequestContext context) throws IOException {
        final var principal = authenticate(context);
        if (principal == null) {
            return;
        }
        if (!services.authorization().canManageChannels(principal)) {
            forbidden(context, "Updating channels requires administrator credentials");
            return;
        }

        final var channel = resolveChannel(context.pathVariables().get(CHANNEL));
        if (channel == null) {
            notFound(context, "Channel not found");
            return;
        }

        final var request = readBody(context, UpdateChannelRequest.class);
        if (request == null) {
            validationFailed(context, List.of(new ErrorResponse.Violation("body", "a request body is required")));
            return;
        }

        final var channelId = UUID.fromString(channel.metadata().id());

        if (!isBlank(request.name())) {
            final var renamed = services.channels().updateName(channelId, request.name().trim());
            if (renamed.wasError()) {
                sendError(context, 409, "CONFLICT", "Unable to rename the channel");
                return;
            }
        }

        if (Boolean.TRUE.equals(request.clearAgentInstructions())) {
            final var cleared = services.channels().updateAgentInstructions(channelId, null);
            if (cleared.wasError()) {
                sendError(context, 409, "CONFLICT", "Unable to clear the channel instructions");
                return;
            }
        } else if (!isBlank(request.agentInstructions())) {
            final var instructions = request.agentInstructions().trim();
            if (instructions.length() > MAX_AGENT_INSTRUCTIONS) {
                validationFailed(context, List.of(new ErrorResponse.Violation("agentInstructions",
                        "instructions must be at most " + MAX_AGENT_INSTRUCTIONS + " characters")));
                return;
            }
            final var updated = services.channels().updateAgentInstructions(channelId, instructions);
            if (updated.wasError()) {
                sendError(context, 409, "CONFLICT", "Unable to update the channel instructions");
                return;
            }
        }

        sendJson(context, 200, toApiChannel(resolveChannel(channel.metadata().id())));
    }

    private void handleGetChannel(final HttpAdapter.RequestContext context) throws IOException {
        final var principal = authenticate(context);
        if (principal == null) {
            return;
        }

        final var channel = resolveChannel(context.pathVariables().get(CHANNEL));
        if (channel == null) {
            notFound(context, "Channel not found");
            return;
        }
        if (!canAccessChannel(principal, channel)) {
            forbidden(context, "Private channel access denied");
            return;
        }

        sendJson(context, 200, toApiChannel(channel));
    }

    private void handleListMessages(final HttpAdapter.RequestContext context) throws IOException {
        final var principal = authenticate(context);
        if (principal == null) {
            return;
        }

        final var channel = resolveChannel(context.pathVariables().get(CHANNEL));
        if (channel == null) {
            notFound(context, "Channel not found");
            return;
        }
        if (!canAccessChannel(principal, channel)) {
            forbidden(context, "Private channel access denied");
            return;
        }

        final var limit = intParam(context, "limit", 50);
        if (limit < 1 || limit > MAX_LIMIT) {
            validationFailed(context,
                    List.of(new ErrorResponse.Violation("limit", "limit must be between 1 and " + MAX_LIMIT)));
            return;
        }

        final String before = param(context, "before");
        final String after = param(context, "after");
        for (final var entry : new String[][] { { "before", before }, { "after", after } }) {
            try {
                MessageCursor.decodeOptional(entry[1]);
            } catch (final IllegalArgumentException e) {
                validationFailed(context, List.of(new ErrorResponse.Violation(entry[0], e.getMessage())));
                return;
            }
        }

        final var channelId = channel.metadata().id();

        // Sample the wake-up epoch before the first read so a post that lands during the read
        // is never missed between checking and waiting.
        final var waitSeconds = intParam(context, "wait", 0);
        if (waitSeconds < 0 || waitSeconds > MAX_WAIT_SECONDS) {
            validationFailed(context,
                    List.of(new ErrorResponse.Violation("wait", "wait must be between 0 and " + MAX_WAIT_SECONDS)));
            return;
        }
        final var epoch = wakeups.epoch(channelId);

        var batch = listMessages(channelId, limit, before, after);
        if (batch == null) {
            serverError(context, "Failed to list messages");
            return;
        }

        if (batch.isEmpty() && waitSeconds > 0) {
            try {
                wakeups.awaitChange(channelId, epoch, Duration.ofSeconds(waitSeconds));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            batch = listMessages(channelId, limit, before, after);
            if (batch == null) {
                serverError(context, "Failed to list messages");
                return;
            }
        }

        sendJson(context, 200, batch.stream().map(ApiRoutes::toApiMessage).collect(Collectors.toList()));
    }

    private List<AetherPersisted<MessageDto>> listMessages(final String channelId, final int limit,
            final String before, final String after) {
        final var result = services.messages().list(channelId, limit, before, after);
        return result.wasError() ? null : result.response();
    }

    private void handleCreateMessage(final HttpAdapter.RequestContext context) throws IOException {
        final var principal = authenticate(context);
        if (principal == null) {
            return;
        }

        final var channel = resolveChannel(context.pathVariables().get(CHANNEL));
        if (channel == null) {
            notFound(context, "Channel not found");
            return;
        }
        if (!canPostToChannel(principal, channel)) {
            forbidden(context, "Posting to this channel is not allowed");
            return;
        }

        final var request = readBody(context, CreateMessageRequest.class);
        if (request == null || isBlank(request.body())) {
            validationFailed(context, List.of(new ErrorResponse.Violation("body", "body is required")));
            return;
        }
        if (request.body().length() > maxMessageSize) {
            sendError(context, 413, "PAYLOAD_TOO_LARGE", "Message body exceeds " + maxMessageSize + " characters");
            return;
        }

        final var format = isBlank(request.format()) ? "plain" : request.format();
        if (!"plain".equals(format) && !"markdown".equals(format)) {
            validationFailed(context, List.of(new ErrorResponse.Violation("format", "format must be plain or markdown")));
            return;
        }

        final var parent = isBlank(request.parentMessageId()) ? null : request.parentMessageId();
        if (parent != null && !HttpAdapter.isUuid(parent)) {
            validationFailed(context,
                    List.of(new ErrorResponse.Violation("parentMessageId", "parentMessageId must be a UUID")));
            return;
        }

        final var dto = new MessageDto(channel.metadata().id(), null, parent, principal.agentId().toString(),
                request.body(), format, false, null, null);
        final var result = parent == null ? services.messages().create(dto) : services.messages().reply(dto);
        if (result.wasError()) {
            serverError(context, parent == null ? "Failed to create message" : "Failed to create reply");
            return;
        }

        final var created = result.response();
        sendCreated(context, "/api/v1/messages/" + created.metadata().id(), toApiMessage(created));
    }

    private org.dempsay.aether.api.store.AetherPersisted<ChannelDto> resolveChannel(final String idOrSlug) {
        if (HttpAdapter.isUuid(idOrSlug)) {
            final var byId = services.channels().get(UUID.fromString(idOrSlug));
            return byId.wasError() ? null : byId.response();
        }
        final var bySlug = services.channels().getBySlug(idOrSlug);
        return bySlug.wasError() ? null : bySlug.response();
    }

    /**
     * Delegates channel visibility to {@link org.dempsay.agenthub.core.port.AuthorizationPort} so that
     * read and write decisions can never diverge.
     */
    private boolean canAccessChannel(final AuthPrincipal principal,
            final org.dempsay.aether.api.store.AetherPersisted<ChannelDto> channel) {
        return services.authorization().canAccessChannel(principal, channel.metadata().id(),
                PUBLIC_VISIBILITY.equals(channel.resource().visibility()));
    }

    /** Delegates channel write access to the authorization port; private channels stay admin-only. */
    private boolean canPostToChannel(final AuthPrincipal principal,
            final org.dempsay.aether.api.store.AetherPersisted<ChannelDto> channel) {
        return services.authorization().canPostToChannel(principal, channel.metadata().id(),
                PUBLIC_VISIBILITY.equals(channel.resource().visibility()));
    }

    /**
     * Validates the bearer credential, writing a {@code 401} and returning {@code null} on failure.
     */
    private AuthPrincipal authenticate(final HttpAdapter.RequestContext context) throws IOException {
        final var header = context.exchange().getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            sendError(context, 401, "UNAUTHORIZED", "Missing or malformed Authorization header");
            return null;
        }

        final var result = services.auth().authenticate(header.substring("Bearer ".length()));
        if (result.wasError()) {
            sendError(context, 401, "UNAUTHORIZED", "Invalid or expired API key");
            return null;
        }
        return result.response();
    }

    private <T> T readBody(final HttpAdapter.RequestContext context, final Class<T> type) {
        final String body;
        try {
            body = new String(context.exchange().getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
        if (body.isBlank()) {
            return null;
        }
        try {
            return http.gson().fromJson(body, type);
        } catch (JsonParseException e) {
            return null;
        }
    }

    private static String param(final HttpAdapter.RequestContext context, final String name) {
        final var query = context.exchange().getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (final var pair : query.split("&")) {
            final var index = pair.indexOf('=');
            if (index > 0 && pair.substring(0, index).equals(name)) {
                final var value = pair.substring(index + 1);
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private static int intParam(final HttpAdapter.RequestContext context, final String name, final int fallback) {
        final var value = param(context, name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private void sendCreated(final HttpAdapter.RequestContext context, final String location, final Object body)
            throws IOException {
        context.exchange().getResponseHeaders().add("Location", location);
        sendJson(context, 201, body);
    }

    private void sendJson(final HttpAdapter.RequestContext context, final int status, final Object body)
            throws IOException {
        http.sendSuccess(context, status, http.gson().toJson(body));
    }

    private void sendError(final HttpAdapter.RequestContext context, final int status, final String code,
            final String message) throws IOException {
        sendError(context, status, code, message, List.of());
    }

    private void sendError(final HttpAdapter.RequestContext context, final int status, final String code,
            final String message, final List<ErrorResponse.Violation> violations) throws IOException {
        final var error = violations.isEmpty()
                ? ErrorResponse.of(code, message, context.requestId())
                : ErrorResponse.of(code, message, context.requestId(), violations);
        sendJson(context, status, error);
    }

    private void validationFailed(final HttpAdapter.RequestContext context, final List<ErrorResponse.Violation> violations)
            throws IOException {
        sendError(context, 422, "VALIDATION_FAILED", "Request validation failed", violations);
    }

    private void forbidden(final HttpAdapter.RequestContext context, final String message) throws IOException {
        sendError(context, 403, "FORBIDDEN", message);
    }

    private void notFound(final HttpAdapter.RequestContext context, final String message) throws IOException {
        sendError(context, 404, "NOT_FOUND", message);
    }

    private void serverError(final HttpAdapter.RequestContext context, final String message) throws IOException {
        sendError(context, 500, "INTERNAL_ERROR", message);
    }

    private static ApiAgentDto toApiAgent(final org.dempsay.aether.api.store.AetherPersisted<AgentDto> persisted) {
        final var metadata = persisted.metadata();
        final var agent = persisted.resource();
        return new ApiAgentDto(
                UUID.fromString(metadata.id()),
                agent.name(),
                agent.kind(),
                agent.ownerAgentId() == null ? null : UUID.fromString(agent.ownerAgentId()),
                agent.provider(),
                agent.model(),
                agent.endpoint(),
                agent.declaredStatus(),
                agent.lastSeenAt(),
                agent.lifecycleState(),
                metadata.createdAt().toString(),
                metadata.updatedAt().toString());
    }

    private static ApiChannelDto toApiChannel(final org.dempsay.aether.api.store.AetherPersisted<ChannelDto> persisted) {
        final var metadata = persisted.metadata();
        final var channel = persisted.resource();
        return new ApiChannelDto(
                UUID.fromString(metadata.id()),
                channel.slug(),
                channel.name(),
                channel.visibility(),
                UUID.fromString(channel.createdByAgentId()),
                metadata.createdAt().toString(),
                metadata.updatedAt().toString(),
                Boolean.TRUE.equals(channel.deleted()),
                channel.deletedAt(),
                channel.deletedByAgentId() == null ? null : UUID.fromString(channel.deletedByAgentId()),
                channel.agentInstructions());
    }

    private static ApiMessageDto toApiMessage(final org.dempsay.aether.api.store.AetherPersisted<MessageDto> persisted) {
        final var metadata = persisted.metadata();
        final var message = persisted.resource();
        return new ApiMessageDto(
                UUID.fromString(metadata.id()),
                UUID.fromString(message.channelId()),
                message.threadId() == null ? null : UUID.fromString(message.threadId()),
                message.parentMessageId() == null ? null : UUID.fromString(message.parentMessageId()),
                UUID.fromString(message.authorAgentId()),
                message.body(),
                message.format(),
                metadata.createdAt().toString(),
                metadata.updatedAt().toString(),
                Boolean.TRUE.equals(message.deleted()),
                message.deletedAt(),
                message.deletedByAgentId() == null ? null : UUID.fromString(message.deletedByAgentId()),
                MessageCursor.of(metadata.createdAt(), metadata.id()).encode());
    }
}
