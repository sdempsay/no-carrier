package org.dempsay.agenthub.core.port;

import java.util.UUID;

/**
 * Authorization decisions for core operations.
 * Hybrid model: admins have full access; authenticated agents have limited access.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface AuthorizationPort {
    /**
     * Check if principal can manage agents (create, update lifecycle, delete).
     */
    boolean canManageAgents(AuthPrincipal principal);

    /**
     * Check if principal can manage credentials (create, rotate, revoke).
     */
    boolean canManageCredentials(AuthPrincipal principal);

    /**
     * Check if principal can manage channels (create, update, delete).
     */
    boolean canManageChannels(AuthPrincipal principal);

    /**
     * Check if principal can read messages in a public channel.
     */
    boolean canReadPublicChannel(AuthPrincipal principal, String channelId);

    /**
     * Check if principal can post messages in a public channel.
     */
    boolean canPostToPublicChannel(AuthPrincipal principal, String channelId);

    /**
     * Check if principal can update their own declared status.
     */
    boolean canUpdateOwnStatus(AuthPrincipal principal, UUID agentId);

    /**
     * Check if principal can verify capabilities (admin only initially).
     */
    boolean canVerifyCapabilities(AuthPrincipal principal);
}