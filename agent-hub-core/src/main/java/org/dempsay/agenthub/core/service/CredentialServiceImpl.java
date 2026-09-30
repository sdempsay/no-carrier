package org.dempsay.agenthub.core.service;

import java.time.Instant;
import java.util.UUID;

import org.dempsay.aether.api.access.AetherPrincipal;
import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.aether.api.store.UpdateOptions;
import org.dempsay.agenthub.api.model.AgentCredentialDto;
import org.dempsay.agenthub.api.model.AgentCredentialDtoStore;
import org.dempsay.agenthub.core.port.CredentialHashPort;
import org.dempsay.agenthub.core.port.CredentialServicePort;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Default credential service using Aether stores and a hash port.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public class CredentialServiceImpl implements CredentialServicePort {
    private final AgentCredentialDtoStore store;
    private final CredentialHashPort hashPort;

    public CredentialServiceImpl(final AgentCredentialDtoStore store, final CredentialHashPort hashPort) {
        this.store = store;
        this.hashPort = hashPort;
    }

    @Override
    public ExceptionalResponse<AetherPersisted<AgentCredentialDto>> create(final UUID agentId, final AgentCredentialDto dto,
            final String plaintextKey) {
        final var hashedKey = hashPort.hash(plaintextKey);
        if (hashedKey.wasError()) {
            return ExceptionalResponse.failure();
        }

        final var newCredential = new AgentCredentialDto(
                agentId.toString(),
                hashedKey.response(),
                dto.label(),
                dto.lastUsedAt(),
                dto.expiresAt(),
                null,
                "active");

        final var response = store.create(e -> { }, AetherPrincipal.system(), newCredential);
        if (response.wasNoError()) {
            return ExceptionalResponse.success(response.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }

    @Override
    public ExceptionalResponse<AetherPersisted<AgentCredentialDto>> rotate(final UUID credentialId, final AgentCredentialDto dto,
            final String plaintextKey) {
        final var old = store.read(e -> { }, AetherPrincipal.system(), credentialId.toString());
        if (old.wasError()) {
            return ExceptionalResponse.failure();
        }

        final var hashedKey = hashPort.hash(plaintextKey);
        if (hashedKey.wasError()) {
            return ExceptionalResponse.failure();
        }

        final var oldCredential = old.response().resource();
        final var newCredential = new AgentCredentialDto(
                oldCredential.agentId(),
                hashedKey.response(),
                dto.label(),
                null,
                dto.expiresAt(),
                null,
                "active");

        final var created = store.create(e -> { }, AetherPrincipal.system(), newCredential);
        if (created.wasError()) {
            return ExceptionalResponse.failure();
        }

        if (revokePersisted(credentialId, old.response(), Instant.now().toString()).wasError()) {
            return ExceptionalResponse.failure();
        }

        return ExceptionalResponse.success(created.response());
    }

    @Override
    public ExceptionalResponse<AetherPersisted<AgentCredentialDto>> revoke(final UUID credentialId, final String revokedAt) {
        final var existing = store.read(e -> { }, AetherPrincipal.system(), credentialId.toString());
        if (existing.wasError()) {
            return ExceptionalResponse.failure();
        }

        final var at = revokedAt == null || revokedAt.isBlank() ? Instant.now().toString() : revokedAt;
        return revokePersisted(credentialId, existing.response(), at);
    }

    private ExceptionalResponse<AetherPersisted<AgentCredentialDto>> revokePersisted(final UUID credentialId,
            final AetherPersisted<AgentCredentialDto> existing, final String revokedAt) {
        final var current = existing.resource();
        final var revoked = new AgentCredentialDto(
                current.agentId(),
                current.keyHash(),
                current.label(),
                current.lastUsedAt(),
                current.expiresAt(),
                revokedAt,
                "revoked");

        final var response = store.update(e -> { }, AetherPrincipal.system(), credentialId.toString(),
                revoked, existing.metadata().version(), UpdateOptions.defaults());
        if (response.wasNoError()) {
            return ExceptionalResponse.success(response.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }
}
