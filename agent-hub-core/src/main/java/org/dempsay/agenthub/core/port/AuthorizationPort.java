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
     * Check if principal can read the agent directory (names, status, kind, identity).
     *
     * <p>Every authenticated agent may read the directory so a message author can be
     * shown by name. Creating agents, rotating credentials, and lifecycle changes stay
     * behind {@link #canManageAgents(AuthPrincipal)}.
     *
     * @param principal authenticated caller
     * @return true when the caller may list agents and resolve an agent by id
     * @since 1.0.0
     */
    boolean canReadAgentDirectory(AuthPrincipal principal);

    /**
     * Check if principal can manage credentials (create, rotate, revoke).
     */
    boolean canManageCredentials(AuthPrincipal principal);

    /**
     * Check if principal can manage channels (create, update, delete).
     */
    boolean canManageChannels(AuthPrincipal principal);

    /**
     * Check if principal can see a channel and read its messages.
     *
     * <p>Admins always pass. Authenticated agents pass for public channels only.
     * Private channels are admin-only until membership grants land in issue #10.
     *
     * @param principal authenticated caller
     * @param channelId target channel
     * @param isPublic whether the channel visibility is {@code public}
     * @return true when the caller may read the channel
     */
    boolean canAccessChannel(AuthPrincipal principal, String channelId, boolean isPublic);

    /**
     * Check if principal can post messages to a channel.
     *
     * <p>Read access and write access are decided by the same rule so that a caller can
     * never post into a channel it is unable to read.
     *
     * @param principal authenticated caller
     * @param channelId target channel
     * @param isPublic whether the channel visibility is {@code public}
     * @return true when the caller may post to the channel
     */
    boolean canPostToChannel(AuthPrincipal principal, String channelId, boolean isPublic);

    /**
     * Check if principal can update their own declared status.
     */
    boolean canUpdateOwnStatus(AuthPrincipal principal, UUID agentId);

    /**
     * Check if principal can verify capabilities (admin only initially).
     */
    boolean canVerifyCapabilities(AuthPrincipal principal);
}