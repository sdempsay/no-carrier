package org.dempsay.agenthub.api.model;

import org.dempsay.aether.api.annotations.AetherRecord;
import org.dempsay.aether.api.annotations.MaxLength;
import org.dempsay.aether.api.annotations.MinLength;
import org.dempsay.aether.api.annotations.Nullable;
import org.dempsay.aether.api.annotations.RegexMatch;

/**
 * Body-only message (top-level post or nested reply). Store id and created/updated
 * timestamps live on {@link org.dempsay.aether.api.store.AetherPersisted#metadata()}.
 *
 * <p>Deleted messages are tombstones: {@code body} is suppressed, thread references stay.
 *
 * @param channelId channel metadata id
 * @param threadId optional thread root metadata id
 * @param parentMessageId optional parent metadata id for replies
 * @param authorAgentId author metadata id
 * @param body message text; empty on a tombstone
 * @param format {@code plain} or {@code markdown}
 * @param deleted whether this row is a tombstone;
 *     {@link Boolean} because Aether builders currently null-check every component
 * @param deletedAt optional ISO-8601 UTC deletion instant
 * @param deletedByAgentId optional deleting agent metadata id
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
@AetherRecord
public record MessageDto(
        @MinLength(1) @MaxLength(64) String channelId,
        @Nullable @MinLength(1) @MaxLength(64) String threadId,
        @Nullable @MinLength(1) @MaxLength(64) String parentMessageId,
        @MinLength(1) @MaxLength(64) String authorAgentId,
        @MinLength(0) @MaxLength(16384) String body,
        @MinLength(1)
        @MaxLength(16)
        @RegexMatch(pattern = "plain|markdown")
        String format,
        Boolean deleted,
        @Nullable @MinLength(1) @MaxLength(64) String deletedAt,
        @Nullable @MinLength(1) @MaxLength(64) String deletedByAgentId) {
}
