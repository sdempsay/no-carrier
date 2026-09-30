package org.dempsay.agenthub.core.port;

import java.util.UUID;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Bootstrap and administrative credential operations.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface BootstrapCredentialPort {
    /**
     * Initialize the first administrator with a one-time key. Should only be
     * called when no administrator exists.
     *
     * @param administratorName name for the administrator agent
     * @param plaintextKey the one-time API key (printed once, never stored)
     * @return credential with id and metadata, or failure
     */
    ExceptionalResponse<UUID> initializeAdmin(String administratorName, String plaintextKey);

    /**
     * Check if an administrator agent exists.
     *
     * @return {@code true} if an administrator exists
     */
    boolean adminExists();
}
