package org.dempsay.agenthub.api.model;

import org.dempsay.aether.api.annotations.AetherRecord;
import org.dempsay.aether.api.annotations.MaxLength;
import org.dempsay.aether.api.annotations.MinLength;
import org.dempsay.aether.api.annotations.Nullable;
import org.dempsay.aether.api.annotations.RegexMatch;

/**
 * Body-only agent identity. Store id and created/updated timestamps live on
 * {@link org.dempsay.aether.api.store.AetherPersisted#metadata()}.
 *
 * @param name display name
 * @param kind {@code human} or {@code automated}
 * @param ownerAgentId optional owning agent metadata id
 * @param provider optional model provider name
 * @param model optional model identifier
 * @param endpoint optional discovery URL; the hub does not invoke it
 * @param declaredStatus {@code online}, {@code busy}, {@code away}, or {@code offline}
 * @param lastSeenAt optional ISO-8601 UTC instant of last authenticated activity
 * @param lifecycleState {@code pending}, {@code active}, {@code suspended}, or {@code revoked}
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
@AetherRecord
public record AgentDto(
        @MinLength(1) @MaxLength(100) String name,
        @MinLength(1)
        @MaxLength(16)
        @RegexMatch(pattern = "human|automated")
        String kind,
        @Nullable @MinLength(1) @MaxLength(64) String ownerAgentId,
        @Nullable @MinLength(1) @MaxLength(100) String provider,
        @Nullable @MinLength(1) @MaxLength(100) String model,
        @Nullable @MinLength(1) @MaxLength(2048) String endpoint,
        @MinLength(1)
        @MaxLength(16)
        @RegexMatch(pattern = "online|busy|away|offline")
        String declaredStatus,
        @Nullable @MinLength(1) @MaxLength(64) String lastSeenAt,
        @MinLength(1)
        @MaxLength(16)
        @RegexMatch(pattern = "pending|active|suspended|revoked")
        String lifecycleState) {
}
