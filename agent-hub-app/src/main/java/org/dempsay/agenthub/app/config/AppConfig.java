package org.dempsay.agenthub.app.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application configuration.
 *
 * @param dataDir persistence root; ignored when {@code persistent} is false
 * @param bindAddress loopback interface to bind
 * @param port TCP port; 0 selects an ephemeral port
 * @param maxMessageSize maximum accepted message body length
 * @param heartbeatTimeoutSeconds expected heartbeat interval
 * @param pbkdf2Iterations API key hashing cost
 * @param persistent whether stores are backed by the filesystem
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record AppConfig(
        Path dataDir,
        String bindAddress,
        int port,
        int maxMessageSize,
        int heartbeatTimeoutSeconds,
        int pbkdf2Iterations,
        boolean persistent) {

    public static final int DEFAULT_PORT = 8080;
    public static final int DEFAULT_MAX_MESSAGE_SIZE = 16384;
    public static final int DEFAULT_HEARTBEAT_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_PBKDF2_ITERATIONS = 600_000;
    public static final String LOOPBACK = "127.0.0.1";

    public AppConfig(final Path dataDir, final String bindAddress, final int port, final int maxMessageSize,
            final int heartbeatTimeoutSeconds, final int pbkdf2Iterations) {
        this(dataDir, bindAddress, port, maxMessageSize, heartbeatTimeoutSeconds, pbkdf2Iterations, true);
    }

    /**
     * Filesystem-backed configuration rooted at {@code dataDir}.
     *
     * @param dataDir persistence root
     * @param port TCP port, or 0 for ephemeral
     * @return persistent configuration
     */
    public static AppConfig onDisk(final Path dataDir, final int port) {
        return new AppConfig(dataDir, LOOPBACK, port, DEFAULT_MAX_MESSAGE_SIZE,
                DEFAULT_HEARTBEAT_TIMEOUT_SECONDS, DEFAULT_PBKDF2_ITERATIONS, true);
    }

    /**
     * Ephemeral in-memory configuration for tests.
     *
     * @param port TCP port, or 0 for ephemeral
     * @return in-memory configuration
     */
    public static AppConfig inMemory(final int port) {
        return new AppConfig(null, LOOPBACK, port, DEFAULT_MAX_MESSAGE_SIZE,
                DEFAULT_HEARTBEAT_TIMEOUT_SECONDS, DEFAULT_PBKDF2_ITERATIONS, false);
    }

    /**
     * In-memory configuration with reduced hashing cost, for tests.
     *
     * @param port TCP port, or 0 for ephemeral
     * @return in-memory configuration
     */
    public static AppConfig inMemoryForTests(final int port) {
        return new AppConfig(null, LOOPBACK, port, DEFAULT_MAX_MESSAGE_SIZE,
                DEFAULT_HEARTBEAT_TIMEOUT_SECONDS, 1000, false);
    }

    public static AppConfig fromEnvironment(final String[] args) {
        final var dataDir = resolveDataDir(args);
        final var bindAddress = System.getenv().getOrDefault("AGENT_HUB_BIND_ADDRESS", LOOPBACK);
        final var port = Integer.parseInt(System.getenv().getOrDefault("AGENT_HUB_PORT", String.valueOf(DEFAULT_PORT)));
        final var maxMessageSize = Integer.parseInt(
                System.getenv().getOrDefault("AGENT_HUB_MAX_MESSAGE_SIZE", String.valueOf(DEFAULT_MAX_MESSAGE_SIZE)));
        final var heartbeatTimeoutSeconds = Integer.parseInt(
                System.getenv().getOrDefault("AGENT_HUB_HEARTBEAT_TIMEOUT", String.valueOf(DEFAULT_HEARTBEAT_TIMEOUT_SECONDS)));
        final var pbkdf2Iterations = Integer.parseInt(
                System.getenv().getOrDefault("AGENT_HUB_PBKDF2_ITERATIONS", String.valueOf(DEFAULT_PBKDF2_ITERATIONS)));

        return new AppConfig(dataDir, bindAddress, port, maxMessageSize, heartbeatTimeoutSeconds, pbkdf2Iterations, true);
    }

    private static Path resolveDataDir(final String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--data-dir".equals(args[i])) {
                return Paths.get(args[i + 1]);
            }
        }
        final var envDir = System.getenv("AGENT_HUB_DATA_DIR");
        if (envDir != null && !envDir.isBlank()) {
            return Paths.get(envDir);
        }
        return Paths.get(System.getProperty("user.home"), ".agent-hub", "data");
    }
}
