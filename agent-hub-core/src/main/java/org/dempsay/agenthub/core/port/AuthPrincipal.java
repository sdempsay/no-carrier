package org.dempsay.agenthub.core.port;

import java.util.UUID;

/**
 * Authenticated principal returned after successful credential validation.
 *
 * @param agentId the authenticated agent's metadata id
 * @param isAdmin whether this principal has administrator privileges
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record AuthPrincipal(
        UUID agentId,
        boolean isAdmin) {
}