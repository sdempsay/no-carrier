package org.dempsay.agenthub.api.model;

import org.dempsay.aether.api.annotations.AetherRecord;
import org.dempsay.aether.api.annotations.MaxLength;
import org.dempsay.aether.api.annotations.MinLength;
import org.dempsay.aether.api.annotations.Nullable;
import org.dempsay.aether.api.annotations.RegexMatch;

/**
 * Body-only API-key credential. Only a salted hash is stored. Store id and
 * created timestamp live on {@link org.dempsay.aether.api.store.AetherPersisted#metadata()}.
 *
 * @param agentId owning agent metadata id
 * @param keyHash encoded algorithm/parameters/salt/hash; never plaintext
 * @param label operator-facing name for this key
 * @param lastUsedAt optional ISO-8601 UTC instant of last successful authentication
 * @param expiresAt optional ISO-8601 UTC expiry instant
 * @param revokedAt optional ISO-8601 UTC revocation instant
 * @param state {@code active}, {@code expired}, or {@code revoked}
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
@AetherRecord
public record AgentCredentialDto(
        @MinLength(1) @MaxLength(64) String agentId,
        @MinLength(1) @MaxLength(512) String keyHash,
        @MinLength(1) @MaxLength(100) String label,
        @Nullable @MinLength(1) @MaxLength(64) String lastUsedAt,
        @Nullable @MinLength(1) @MaxLength(64) String expiresAt,
        @Nullable @MinLength(1) @MaxLength(64) String revokedAt,
        @MinLength(1)
        @MaxLength(16)
        @RegexMatch(pattern = "active|expired|revoked")
        String state) {
}
