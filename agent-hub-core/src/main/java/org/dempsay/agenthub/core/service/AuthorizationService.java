package org.dempsay.agenthub.core.service;

import java.util.Objects;
import java.util.UUID;

import org.dempsay.agenthub.core.port.AuthPrincipal;
import org.dempsay.agenthub.core.port.AuthorizationPort;

/**
 * Default hybrid authorization service.
 * - Admins can do everything
 * - Authenticated agents can read the agent directory and read/post to public channels
 * - Private channels are admin-only until membership grants land in issue #10
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
    public boolean canReadAgentDirectory(final AuthPrincipal principal) {
        return Objects.nonNull(principal);
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
    public boolean canAccessChannel(final AuthPrincipal principal, final String channelId, final boolean isPublic) {
        return principal.isAdmin() || isPublic;
    }

    @Override
    public boolean canPostToChannel(final AuthPrincipal principal, final String channelId, final boolean isPublic) {
        return principal.isAdmin() || isPublic;
    }

    @Override
    public boolean canUpdateOwnStatus(final AuthPrincipal principal, final UUID agentId) {
        return principal.agentId().equals(agentId);
    }

    @Override
    public boolean canVerifyCapabilities(final AuthPrincipal principal) {
        return principal.isAdmin();
    }

    @Override
    public boolean canConfigureWake(final AuthPrincipal principal) {
        return principal.isAdmin() || canManageChannels(principal);
    }
}