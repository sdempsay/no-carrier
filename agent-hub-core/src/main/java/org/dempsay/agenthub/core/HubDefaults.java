package org.dempsay.agenthub.core;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Defaults shared by every entry point that opens a store.
 *
 * <p>The server and the command-line adapter must agree on where data lives and how
 * expensive credential hashing is. A divergence is silent and severe: a mismatch in
 * hashing cost makes every issued key fail to authenticate, and a mismatch in data
 * directory has the administrator initializing a store the server never reads.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public final class HubDefaults {
    /** Credential hashing cost. Must match on both sides of a credential. */
    public static final int DEFAULT_PBKDF2_ITERATIONS = 600_000;

    /** Environment variable overriding the persistence root. */
    public static final String DATA_DIR_ENV = "AGENT_HUB_DATA_DIR";

    private HubDefaults() {
    }

    /**
     * Resolves the data directory from an explicit override, the environment, or the
     * per-user default.
     *
     * @param override explicit path, or null/blank to fall through
     * @return the resolved data directory
     */
    public static Path resolveDataDir(final String override) {
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim());
        }
        final var envDir = System.getenv(DATA_DIR_ENV);
        if (envDir != null && !envDir.isBlank()) {
            return Paths.get(envDir.trim());
        }
        return Paths.get(System.getProperty("user.home"), ".agent-hub", "data");
    }
}