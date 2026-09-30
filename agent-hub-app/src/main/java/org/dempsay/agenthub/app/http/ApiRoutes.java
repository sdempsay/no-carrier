package org.dempsay.agenthub.app.http;

import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.dempsay.agenthub.api.model.AgentCredentialDto;
import org.dempsay.agenthub.api.model.AgentDto;
import org.dempsay.agenthub.api.model.ChannelDto;
import org.dempsay.agenthub.api.model.MessageDto;
import org.dempsay.agenthub.app.HubServices;
import org.dempsay.agenthub.app.http.dto.ApiAgentDto;
import org.dempsay.agenthub.app.http.dto.ApiChannelDto;
import org.dempsay.agenthub.app.http.dto.ApiMessageDto;
import org.dempsay.agenthub.app.http.dto.CreateAgentRequest;
import org.dempsay.agenthub.app.http.dto.CreateChannelRequest;
import org.dempsay.agenthub.app.http.dto.CreateMessageRequest;
import org.dempsay.agenthub.core.port.AuthPrincipal;

/**
 * HTTP route handlers for the Agent Hub API.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class ApiRoutes {
    private static final String CHANNEL = "channel";
    private static final String AGENT = "agent";
    private static final int MAX_LIMIT = 100;

    private final HttpAdapter http;
    private final HubServices services;
    private final int maxMessageSize;

    public ApiRoutes(final HttpAdapter http, final HubServices services, final int maxMessageSize) {
        this.http = http;
        this.services = services;
        this.maxMessageSize = maxMessageSize;
    }

    public void register() {
        http.get("/health", context -> sendJson(context, 200, Map.of("status", "UP", "ready", true)));

        http.get("/api/v1/agents", this::handleListAgents);
        http.post("/api/v1/agents", this::handleCreateAgent);
        http.post("/api/v1/agents/{" + AGENT + "}/heartbeat", this::handleHeartbeat);

        http.get("/api/v1/channels", this::handleListChannels);
        http.post("/api/v1/channels", this::handleCreateChannel);
        http.get("/api/v1/channels/{" + CHANNEL + "}", this::handleGetChannel);

        http.get("/api/v1/channels/{" + CHANNEL + "}/messages", this::handleListMessages);
        http.post("/api/v1/channels/{" + CHANNEL + "}/messages", this::handleCreateMessage);
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

        final var result = services.channels().create(new ChannelDto(request.slug(), request.name(), visibility,
                principal.agentId().toString(), false, null, null, null, null));
        if (result.wasError()) {
            sendError(context, 409, "CONFLICT", "Channel slug is already taken");
            return;
        }

        final var channel = result.response();
        sendCreated(context, "/api/v1/channels/" + channel.metadata().id(), toApiChannel(channel));
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
        if (!"public".equals(channel.resource().visibility()) && !principal.isAdmin()) {
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
        if (!"public".equals(channel.resource().visibility()) && !principal.isAdmin()) {
            forbidden(context, "Private channel access denied");
            return;
        }

        final var limit = intParam(context, "limit", 50);
        if (limit < 1 || limit > MAX_LIMIT) {
            validationFailed(context,
                    List.of(new ErrorResponse.Violation("limit", "limit must be between 1 and " + MAX_LIMIT)));
            return;
        }

        final var result = services.messages().list(channel.metadata().id(), limit, param(context, "before"),
                param(context, "after"));
        if (result.wasError()) {
            serverError(context, "Failed to list messages");
            return;
        }

        sendJson(context, 200, result.response().stream().map(ApiRoutes::toApiMessage).collect(Collectors.toList()));
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
        if (!"public".equals(channel.resource().visibility())
                && !services.authorization().canPostToPublicChannel(principal, channel.metadata().id())) {
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
        http.sendSuccess(context.exchange(), status, http.gson().toJson(body));
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
                channel.deletedByAgentId() == null ? null : UUID.fromString(channel.deletedByAgentId()));
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
                message.deletedByAgentId() == null ? null : UUID.fromString(message.deletedByAgentId()));
    }
}
