package org.dempsay.agenthub.core.port;

import java.util.List;
import java.util.UUID;

import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.agenthub.api.model.AgentDto;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Agent registry operations.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface AgentRegistryPort {
    /**
     * Create a new agent.
     *
     * @param dto agent data (excluding metadata)
     * @return created agent with id and metadata, or failure
     */
    ExceptionalResponse<AetherPersisted<AgentDto>> create(AgentDto dto);

    /**
     * Get an agent by id.
     *
     * @param agentId agent metadata id
     * @return agent with metadata or failure
     */
    ExceptionalResponse<AetherPersisted<AgentDto>> get(UUID agentId);

    /**
     * List all agents.
     *
     * @return list of agents with metadata
     */
    ExceptionalResponse<List<AetherPersisted<AgentDto>>> list();

    /**
     * Update agent lifecycle state.
     *
     * @param agentId agent metadata id
     * @param lifecycleState new state: pending, active, suspended, revoked
     * @return updated agent with metadata or failure
     */
    ExceptionalResponse<AetherPersisted<AgentDto>> updateLifecycle(UUID agentId, String lifecycleState);

    /**
     * Update agent status and last seen timestamp.
     *
     * @param agentId agent metadata id
     * @param declaredStatus new declared status
     * @param lastSeenAt ISO-8601 UTC timestamp
     * @return updated agent with metadata or failure
     */
    ExceptionalResponse<AetherPersisted<AgentDto>> updateStatus(UUID agentId, String declaredStatus, String lastSeenAt);
}
