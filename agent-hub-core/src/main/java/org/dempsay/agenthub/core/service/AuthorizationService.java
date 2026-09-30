package org.dempsay.agenthub.core.service;

import java.util.UUID;

import org.dempsay.agenthub.core.port.AuthPrincipal;
import org.dempsay.agenthub.core.port.AuthorizationPort;

/**
 * Default hybrid authorization service.
 * - Admins can do everything
 * - Authenticated agents can read/post to public channels
 * - Agents can update their own status
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public class AuthorizationService implements AuthorizationPort {
    @Override
    public boolean canManageAgents(final AuthPrincipal principal) {
        return principal.isAdmin();
    }

    @Override
    public boolean canManageCredentials(final AuthPrincipal principal) {
        return principal.isAdmin();
    }

    @Override
    public boolean canManageChannels(final AuthPrincipal principal) {
        return principal.isAdmin();
    }

    @Override
    public boolean canReadPublicChannel(final AuthPrincipal principal, final String channelId) {
        return true; // All authenticated agents can read public channels
    }

    @Override
    public boolean canPostToPublicChannel(final AuthPrincipal principal, final String channelId) {
        return true; // All authenticated agents can post to public channels
    }

    @Override
    public boolean canUpdateOwnStatus(final AuthPrincipal principal, final UUID agentId) {
        return principal.agentId().equals(agentId);
    }

    @Override
    public boolean canVerifyCapabilities(final AuthPrincipal principal) {
        return principal.isAdmin();
    }
}