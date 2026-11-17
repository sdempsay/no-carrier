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
 * @param wakeUrl optional http or https URL the board POSTs to when a message is relevant;
 *     distinct from {@code endpoint}, which remains a directory listing and is never called
 * @param wakeKey optional write-only secret sent in the wake header; stored so the board can
 *     present it, and never returned by an API
 * @param wakeHeaderName optional header that carries {@code wakeKey};
 *     null means {@code X-Agent-Hub-Wake-Key}
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
        String lifecycleState,
        @Nullable @MaxLength(2048) String wakeUrl,
        @Nullable @MaxLength(256) String wakeKey,
        @Nullable @MaxLength(64) String wakeHeaderName) {

    /**
     * Returns a copy with a new lifecycle state. Wake settings are kept.
     *
     * @param newLifecycleState replacement lifecycle state
     * @return copy
     */
    public AgentDto withLifecycle(final String newLifecycleState) {
        return new AgentDto(name, kind, ownerAgentId, provider, model, endpoint, declaredStatus, lastSeenAt,
                newLifecycleState, wakeUrl, wakeKey, wakeHeaderName);
    }

    /**
     * Returns a copy with a new declared status and last-seen instant. Wake settings are kept.
     *
     * @param newDeclaredStatus replacement declared status
     * @param newLastSeenAt replacement last-seen instant, or null
     * @return copy
     */
    public AgentDto withStatus(final String newDeclaredStatus, final String newLastSeenAt) {
        return new AgentDto(name, kind, ownerAgentId, provider, model, endpoint, newDeclaredStatus, newLastSeenAt,
                lifecycleState, wakeUrl, wakeKey, wakeHeaderName);
    }

    /**
     * Returns a copy with replacement wake settings. Pass null to clear a field.
     *
     * @param newWakeUrl replacement URL, or null
     * @param newWakeKey replacement secret, or null
     * @param newWakeHeaderName replacement header name, or null for the default
     * @return copy
     */
    public AgentDto withWake(final String newWakeUrl, final String newWakeKey, final String newWakeHeaderName) {
        return new AgentDto(name, kind, ownerAgentId, provider, model, endpoint, declaredStatus, lastSeenAt,
                lifecycleState, newWakeUrl, newWakeKey, newWakeHeaderName);
    }

    /**
     * Reports whether this agent has a wake URL. A URL is the only required wake setting;
     * the secret and header name may be absent.
     *
     * @return true when {@code wakeUrl} is non-blank
     */
    public boolean wakeConfigured() {
        return wakeUrl != null && !wakeUrl.isBlank();
    }
}
