package org.dempsay.agenthub.app.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP server with path-template routing, request ids, and structured errors.
 *
 * <p>Route templates use {@code {name}} segments, for example
 * {@code /api/v1/channels/{channel}/messages}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class HttpAdapter implements AutoCloseable {
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String RESPONDED = "org.dempsay.agenthub.app.http.responded";

    private final HttpServer server;
    private final Gson gson;
    private final ExecutorService executor;
    private final List<Route> routes = new CopyOnWriteArrayList<>();

    /**
     * Per-request state handed to a route handler.
     *
     * @param exchange the live exchange
     * @param requestId the echoed or minted request id
     * @param pathVariables values captured from the route template
     */
    public record RequestContext(HttpExchange exchange, String requestId, Map<String, String> pathVariables) {
    }

    /**
     * Route handler. May throw to abort with a {@code 500}.
     */
    @FunctionalInterface
    public interface RouteHandler {
        void handle(RequestContext context) throws IOException;
    }

    public HttpAdapter(final int port, final String bindAddress) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        this.gson = new GsonBuilder().create();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.server.setExecutor(executor);
        this.server.createContext("/", new RouterHandler());
    }

    public void get(final String template, final RouteHandler handler) {
        add("GET", template, handler);
    }

    public void post(final String template, final RouteHandler handler) {
        add("POST", template, handler);
    }

    public void patch(final String template, final RouteHandler handler) {
        add("PATCH", template, handler);
    }

    public void delete(final String template, final RouteHandler handler) {
        add("DELETE", template, handler);
    }

    public void start() {
        server.start();
    }

    /**
     * Returns the port actually bound, which differs from the requested port when 0 was used.
     *
     * @return the bound port
     */
    public int port() {
        return server.getAddress().getPort();
    }

    public Gson gson() {
        return gson;
    }

    /**
     * Writes a JSON response. A second write for the same exchange is ignored.
     *
     * @param exchange target exchange
     * @param status HTTP status
     * @param body serialized body
     * @throws IOException on transport failure
     */
    public void sendSuccess(final HttpExchange exchange, final int status, final String body) throws IOException {
        final var bytes = body.getBytes(StandardCharsets.UTF_8);
        if (Boolean.TRUE.equals(exchange.getAttribute(RESPONDED))) {
            return;
        }
        exchange.setAttribute(RESPONDED, Boolean.TRUE);
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void add(final String method, final String template, final RouteHandler handler) {
        routes.add(new Route(method, split(template), handler));
    }

    private static String[] split(final String path) {
        final var trimmed = path.startsWith("/") ? path.substring(1) : path;
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/", -1);
    }

    private final class RouterHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            final var requestId = extractRequestId(exchange);
            exchange.getResponseHeaders().add(REQUEST_ID_HEADER, requestId);

            try {
                final var path = exchange.getRequestURI().getPath();
                final var match = match(exchange.getRequestMethod(), split(path));
                if (match == null) {
                    sendError(exchange, 404, "NOT_FOUND", "No handler for " + exchange.getRequestMethod() + " " + path, requestId);
                    return;
                }

                match.route().handler().handle(new RequestContext(exchange, requestId, match.variables()));
            } catch (IOException e) {
                sendError(exchange, 500, "INTERNAL_ERROR", String.valueOf(e.getMessage()), requestId);
            }
        }

        private Match match(final String method, final String[] segments) {
            for (final var route : routes) {
                if (!route.method().equals(method)) {
                    continue;
                }
                final var variables = new LinkedHashMap<String, String>();
                if (binds(route.segments(), segments, variables)) {
                    return new Match(route, Collections.unmodifiableMap(variables));
                }
            }
            return null;
        }
    }

    private static boolean binds(final String[] template, final String[] actual, final Map<String, String> variables) {
        if (template.length != actual.length) {
            return false;
        }
        for (int i = 0; i < template.length; i++) {
            final var expected = template[i];
            if (isVariable(expected)) {
                variables.put(variableName(expected), actual[i]);
            } else if (!expected.equals(actual[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isVariable(final String segment) {
        return segment.length() > 2 && segment.charAt(0) == '{' && segment.charAt(segment.length() - 1) == '}';
    }

    private static String variableName(final String segment) {
        return segment.substring(1, segment.length() - 1);
    }

    private String extractRequestId(final HttpExchange exchange) {
        final var clientId = exchange.getRequestHeaders().getFirst(REQUEST_ID_HEADER);
        if (clientId != null && !clientId.isBlank() && isUuid(clientId)) {
            return clientId;
        }
        return UUID.randomUUID().toString();
    }

    static boolean isUuid(final String value) {
        if (value.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void sendError(final HttpExchange exchange, final int status, final String code, final String message,
            final String requestId) throws IOException {
        sendSuccess(exchange, status, gson.toJson(ErrorResponse.of(code, message, requestId)));
    }

    private record Route(String method, String[] segments, RouteHandler handler) {
    }

    private record Match(Route route, Map<String, String> variables) {
    }
}
