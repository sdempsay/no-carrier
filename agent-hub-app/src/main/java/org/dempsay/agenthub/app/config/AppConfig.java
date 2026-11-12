package org.dempsay.agenthub.app.config;

import java.nio.file.Path;
import java.util.Map;

import org.dempsay.agenthub.core.HubDefaults;

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
    public static final int DEFAULT_PBKDF2_ITERATIONS = HubDefaults.DEFAULT_PBKDF2_ITERATIONS;
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

    public static final String USAGE = """
            Usage: java -jar agent-hub-app.jar [options]
              --data-dir PATH          persistence root (env AGENT_HUB_DATA_DIR)
              --bind-address ADDR       loopback interface to bind (env AGENT_HUB_BIND_ADDRESS)
              --port N                 TCP port, 0 for ephemeral (env AGENT_HUB_PORT)
              --in-memory              do not persist to disk
              --max-message-size N     maximum message body length (env AGENT_HUB_MAX_MESSAGE_SIZE)
              --heartbeat-timeout N    expected heartbeat interval (env AGENT_HUB_HEARTBEAT_TIMEOUT)
              --pbkdf2-iterations N    API key hashing cost (env AGENT_HUB_PBKDF2_ITERATIONS)
              --help                   show this message
            """;

    private static final Map<String, String> ENV_BY_OPTION = Map.of(
            "--data-dir", "AGENT_HUB_DATA_DIR",
            "--bind-address", "AGENT_HUB_BIND_ADDRESS",
            "--port", "AGENT_HUB_PORT",
            "--max-message-size", "AGENT_HUB_MAX_MESSAGE_SIZE",
            "--heartbeat-timeout", "AGENT_HUB_HEARTBEAT_TIMEOUT",
            "--pbkdf2-iterations", "AGENT_HUB_PBKDF2_ITERATIONS");

    /**
     * Builds configuration from command-line arguments, falling back to environment
     * variables and then to defaults. Arguments take precedence over the environment.
     *
     * @param args command-line arguments
     * @return resolved configuration
     */
    public static AppConfig fromEnvironment(final String[] args) {
        if (hasFlag(args, "--help")) {
            throw new UsageRequested();
        }
        rejectUnknownOptions(args);

        final var persistent = !hasFlag(args, "--in-memory");
        final var dataDir = persistent ? HubDefaults.resolveDataDir(argValue(args, "--data-dir")) : null;

        return new AppConfig(
                dataDir,
                resolveString(args, "--bind-address", LOOPBACK),
                resolveInt(args, "--port", DEFAULT_PORT),
                resolveInt(args, "--max-message-size", DEFAULT_MAX_MESSAGE_SIZE),
                resolveInt(args, "--heartbeat-timeout", DEFAULT_HEARTBEAT_TIMEOUT_SECONDS),
                resolveInt(args, "--pbkdf2-iterations", DEFAULT_PBKDF2_ITERATIONS),
                persistent);
    }

    private static String resolveString(final String[] args, final String option, final String fallback) {
        final var value = argValue(args, option);
        if (value != null) {
            return value;
        }
        final var env = System.getenv(ENV_BY_OPTION.get(option));
        if (env != null && !env.isBlank()) {
            return env;
        }
        return fallback;
    }

    private static int resolveInt(final String[] args, final String option, final int fallback) {
        final var text = resolveString(args, option, null);
        if (text == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(option + " expects an integer but got: " + text, e);
        }
    }

    private static String argValue(final String[] args, final String option) {
        for (int i = 0; i < args.length - 1; i++) {
            if (option.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static boolean hasFlag(final String[] args, final String flag) {
        for (final var arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void rejectUnknownOptions(final String[] args) {
        for (int i = 0; i < args.length; i++) {
            final var arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg + "\n" + USAGE);
            }
            if ("--in-memory".equals(arg) || "--help".equals(arg)) {
                continue;
            }
            if (!ENV_BY_OPTION.containsKey(arg)) {
                throw new IllegalArgumentException("Unknown option: " + arg + "\n" + USAGE);
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + arg + "\n" + USAGE);
            }
            i++;
        }
    }

    /** Raised when {@code --help} is supplied. */
    public static final class UsageRequested extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UsageRequested() {
            super(USAGE);
        }
    }
}
