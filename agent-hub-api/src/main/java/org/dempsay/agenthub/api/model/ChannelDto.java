package org.dempsay.agenthub.api.model;

import org.dempsay.aether.api.annotations.AetherRecord;
import org.dempsay.aether.api.annotations.MaxLength;
import org.dempsay.aether.api.annotations.MinLength;
import org.dempsay.aether.api.annotations.Nullable;
import org.dempsay.aether.api.annotations.RegexMatch;
import org.dempsay.aether.api.annotations.Unique;

/**
 * Body-only channel. Store id and created timestamp live on
 * {@link org.dempsay.aether.api.store.AetherPersisted#metadata()}.
 *
 * <p>Slugs are URL-safe, globally unique, and immutable. Display names may change.
 *
 * @param slug immutable URL-safe identifier
 * @param name display name
 * @param visibility {@code public} or {@code private}
 * @param createdByAgentId creating agent metadata id
 * @param deleted whether the channel is frozen/hidden from normal discovery;
 *     {@link Boolean} because Aether builders currently null-check every component
 * @param deletedAt optional ISO-8601 UTC deletion instant
 * @param deletedByAgentId optional deleting agent metadata id
 * @param restoredAt optional ISO-8601 UTC restoration instant
 * @param restoredByAgentId optional restoring agent metadata id
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
@AetherRecord
public record ChannelDto(
        @MinLength(1)
        @MaxLength(64)
        @RegexMatch(pattern = "[a-z0-9]+(?:-[a-z0-9]+)*")
        @Unique
        String slug,
        @MinLength(1) @MaxLength(100) String name,
        @MinLength(1)
        @MaxLength(16)
        @RegexMatch(pattern = "public|private")
        String visibility,
        @MinLength(1) @MaxLength(64) String createdByAgentId,
        Boolean deleted,
        @Nullable @MinLength(1) @MaxLength(64) String deletedAt,
        @Nullable @MinLength(1) @MaxLength(64) String deletedByAgentId,
        @Nullable @MinLength(1) @MaxLength(64) String restoredAt,
        @Nullable @MinLength(1) @MaxLength(64) String restoredByAgentId) {
}
