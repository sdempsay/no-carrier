package org.dempsay.agenthub.core.service;

import java.util.List;
import java.util.UUID;

import org.dempsay.aether.api.access.AetherPrincipal;
import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.aether.api.store.UpdateOptions;
import org.dempsay.agenthub.api.model.AgentDto;
import org.dempsay.agenthub.api.model.AgentDtoStore;
import org.dempsay.agenthub.core.port.AgentRegistryPort;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Default agent registry service using Aether stores.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public class AgentRegistryService implements AgentRegistryPort {
    private final AgentDtoStore store;

    public AgentRegistryService(final AgentDtoStore store) {
        this.store = store;
    }

    @Override
    public ExceptionalResponse<AetherPersisted<AgentDto>> create(final AgentDto dto) {
        final var response = store.create(e -> { }, AetherPrincipal.system(), dto);
        if (response.wasNoError()) {
            return ExceptionalResponse.success(response.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }

    @Override
    public ExceptionalResponse<AetherPersisted<AgentDto>> get(final UUID agentId) {
        final var response = store.read(e -> { }, AetherPrincipal.system(), agentId.toString());
        if (response.wasNoError()) {
            return ExceptionalResponse.success(response.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }

    @Override
    public ExceptionalResponse<List<AetherPersisted<AgentDto>>> list() {
        return store.list(e -> { }, AetherPrincipal.system());
    }

    @Override
    public ExceptionalResponse<AetherPersisted<AgentDto>> updateLifecycle(final UUID agentId, final String lifecycleState) {
        final var response = store.read(e -> { }, AetherPrincipal.system(), agentId.toString());
        if (!response.wasNoError()) {
            return ExceptionalResponse.failure();
        }

        var agent = response.response().resource();
        var updated = new AgentDto(
                agent.name(),
                agent.kind(),
                agent.ownerAgentId(),
                agent.provider(),
                agent.model(),
                agent.endpoint(),
                agent.declaredStatus(),
                agent.lastSeenAt(),
                lifecycleState);

        final var updateResponse = store.update(e -> { }, AetherPrincipal.system(), agentId.toString(),
                updated, response.response().metadata().version(), UpdateOptions.defaults());
        if (updateResponse.wasNoError()) {
            return ExceptionalResponse.success(updateResponse.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }

    @Override
    public ExceptionalResponse<AetherPersisted<AgentDto>> updateStatus(final UUID agentId, final String declaredStatus, final String lastSeenAt) {
        final var response = store.read(e -> { }, AetherPrincipal.system(), agentId.toString());
        if (!response.wasNoError()) {
            return ExceptionalResponse.failure();
        }

        var agent = response.response().resource();
        var updated = new AgentDto(
                agent.name(),
                agent.kind(),
                agent.ownerAgentId(),
                agent.provider(),
                agent.model(),
                agent.endpoint(),
                declaredStatus,
                lastSeenAt,
                agent.lifecycleState());

        final var updateResponse = store.update(e -> { }, AetherPrincipal.system(), agentId.toString(),
                updated, response.response().metadata().version(), UpdateOptions.defaults());
        if (updateResponse.wasNoError()) {
            return ExceptionalResponse.success(updateResponse.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }
}