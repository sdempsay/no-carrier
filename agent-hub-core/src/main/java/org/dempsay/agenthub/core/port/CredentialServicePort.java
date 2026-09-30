package org.dempsay.agenthub.core.port;

import java.util.UUID;

import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.agenthub.api.model.AgentCredentialDto;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Credential lifecycle operations. Rotation immediately revokes the selected
 * old credential.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface CredentialServicePort {
    /**
     * Create a new credential for an agent.
     *
     * @param agentId agent metadata id
     * @param dto credential data (excluding metadata)
     * @param plaintextKey the raw API key; hashed before persistence and never echoed back
     * @return new credential with metadata, or failure
     */
    ExceptionalResponse<AetherPersisted<AgentCredentialDto>> create(UUID agentId, AgentCredentialDto dto, String plaintextKey);

    /**
     * Rotate an existing credential. The old credential is immediately revoked.
     *
     * @param credentialId credential metadata id to rotate
     * @param dto new credential data (excluding metadata)
     * @param plaintextKey the raw API key; hashed before persistence and never echoed back
     * @return new credential with metadata, or failure
     */
    ExceptionalResponse<AetherPersisted<AgentCredentialDto>> rotate(UUID credentialId, AgentCredentialDto dto, String plaintextKey);

    /**
     * Revoke a credential.
     *
     * @param credentialId credential metadata id
     * @param revokedAt ISO-8601 UTC revocation timestamp
     * @return updated credential with metadata, or failure
     */
    ExceptionalResponse<AetherPersisted<AgentCredentialDto>> revoke(UUID credentialId, String revokedAt);
}
