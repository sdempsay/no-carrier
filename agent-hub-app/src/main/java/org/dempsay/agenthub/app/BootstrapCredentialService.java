package org.dempsay.agenthub.app;

import java.util.UUID;

import org.dempsay.agenthub.api.model.AgentCredentialDto;
import org.dempsay.agenthub.api.model.AgentDto;
import org.dempsay.agenthub.core.port.AgentRegistryPort;
import org.dempsay.agenthub.core.port.BootstrapCredentialPort;
import org.dempsay.agenthub.core.port.CredentialServicePort;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * First-start administrator bootstrap. Creates one human administrator and a
 * single credential whose plaintext key is shown to the operator exactly once.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class BootstrapCredentialService implements BootstrapCredentialPort {
    /** Name reserved for the bootstrapped administrator. */
    public static final String ADMIN_NAME = "admin";

    private final AgentRegistryPort agents;
    private final CredentialServicePort credentials;

    public BootstrapCredentialService(final AgentRegistryPort agents, final CredentialServicePort credentials) {
        this.agents = agents;
        this.credentials = credentials;
    }

    @Override
    public ExceptionalResponse<UUID> initializeAdmin(final String administratorName, final String plaintextKey) {
        final var created = agents.create(new AgentDto(administratorName, "human", null, null, null, null, "online", null,
                "active"));
        if (created.wasError()) {
            return ExceptionalResponse.failure();
        }

        final var agentId = created.response().metadata().id();
        final var credential = credentials.create(UUID.fromString(agentId),
                new AgentCredentialDto(agentId, "unset", "bootstrap", null, null, null, "active"), plaintextKey);
        if (credential.wasError()) {
            return ExceptionalResponse.failure();
        }

        return ExceptionalResponse.success(UUID.fromString(agentId));
    }

    @Override
    public boolean adminExists() {
        final var listed = agents.list();
        if (listed.wasError()) {
            return false;
        }
        return listed.response().stream()
                .anyMatch(agent -> ADMIN_NAME.equalsIgnoreCase(agent.resource().name()));
    }
}
