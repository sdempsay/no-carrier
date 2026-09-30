package org.dempsay.agenthub.core.service;

import java.util.UUID;

import org.dempsay.aether.api.access.AetherPrincipal;
import org.dempsay.agenthub.api.model.AgentCredentialDtoStore;
import org.dempsay.agenthub.api.model.AgentDtoStore;
import org.dempsay.agenthub.core.port.AuthPrincipal;
import org.dempsay.agenthub.core.port.BearerAuthPort;
import org.dempsay.agenthub.core.port.CredentialHashPort;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Default bearer token authentication service.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public class BearerAuthService implements BearerAuthPort {
    private final AgentCredentialDtoStore credentialStore;
    private final AgentDtoStore agentStore;
    private final CredentialHashPort hashPort;

    public BearerAuthService(
            final AgentCredentialDtoStore credentialStore,
            final AgentDtoStore agentStore,
            final CredentialHashPort hashPort) {
        this.credentialStore = credentialStore;
        this.agentStore = agentStore;
        this.hashPort = hashPort;
    }

    @Override
    public ExceptionalResponse<AuthPrincipal> authenticate(final String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return ExceptionalResponse.failure();
        }

        // Find credential by scanning all (in a real impl we'd have an index)
        final var credResponse = credentialStore.list(e -> { }, AetherPrincipal.system());
        if (!credResponse.wasNoError()) {
            return ExceptionalResponse.failure();
        }

        var foundCred = credResponse.response().stream()
            .filter(c -> {
                var match = hashPort.matches(apiKey, c.resource().keyHash());
                return match.wasNoError() && match.response();
            })
            .findFirst();

        if (foundCred.isEmpty()) {
            return ExceptionalResponse.failure();
        }

        var cred = foundCred.get().resource();
        
        // Check credential state
        if (!"active".equals(cred.state())) {
            return ExceptionalResponse.failure();
        }

        // Get agent and check lifecycle state
        final var agentResponse = agentStore.read(e -> { }, AetherPrincipal.system(), cred.agentId());
        if (!agentResponse.wasNoError()) {
            return ExceptionalResponse.failure();
        }

        var agent = agentResponse.response().resource();
        if (!"active".equals(agent.lifecycleState())) {
            return ExceptionalResponse.failure();
        }

        // Check if admin (for now, agent with specific name or first agent)
        final boolean isAdmin = "admin".equalsIgnoreCase(agent.name());
        final var principal = new AuthPrincipal(UUID.fromString(cred.agentId()), isAdmin);
        
        return ExceptionalResponse.success(principal);
    }
}