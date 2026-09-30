package org.dempsay.agenthub.core.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque pagination position for channel messages.
 *
 * <p>A cursor identifies a message by the pair {@code (createdAt, id)} rather than by
 * timestamp alone. Timestamps are not unique: concurrent writers can produce messages
 * sharing a single {@code createdAt}, and a timestamp-only cursor then skips or repeats
 * the whole tied group. Carrying the id makes every position unambiguous.
 *
 * <p>The encoded form is base64url over {@code epochSecond:nano:id} and is treated as an
 * opaque token by clients.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public final class MessageCursor implements Comparable<MessageCursor> {
    private static final String SEPARATOR = ":";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final long epochSecond;
    private final int nano;
    private final String id;

    /**
     * Creates a cursor for an explicit position.
     *
     * @param createdAt message creation instant
     * @param id message identifier
     */
    public MessageCursor(final Instant createdAt, final String id) {
        this(createdAt.getEpochSecond(), createdAt.getNano(), id);
    }

    private MessageCursor(final long epochSecond, final int nano, final String id) {
        this.epochSecond = epochSecond;
        this.nano = nano;
        this.id = id;
    }

    /**
     * Creates a cursor positioned at an already-persisted message.
     *
     * @param createdAt message creation instant
     * @param id message identifier
     * @return cursor for that position
     */
    public static MessageCursor of(final Instant createdAt, final String id) {
        return new MessageCursor(createdAt, id);
    }

    /**
     * Encodes this cursor as an opaque token.
     *
     * @return opaque cursor string
     */
    public String encode() {
        return ENCODER.encodeToString(
                (epochSecond + SEPARATOR + nano + SEPARATOR + id).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes an opaque cursor token.
     *
     * @param token previously encoded cursor
     * @return decoded cursor
     * @throws IllegalArgumentException when the token is not a valid cursor
     */
    public static MessageCursor decode(final String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Cursor must not be blank");
        }

        final String raw;
        try {
            raw = new String(DECODER.decode(token.trim()), StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed cursor: " + token, e);
        }

        final var parts = raw.split(SEPARATOR, 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed cursor: " + token);
        }
        try {
            final var epochSecond = Long.parseLong(parts[0]);
            final var nano = Integer.parseInt(parts[1]);
            final var id = parts[2];
            if (id.isBlank()) {
                throw new IllegalArgumentException("Malformed cursor: " + token);
            }
            UUID.fromString(id);
            return new MessageCursor(epochSecond, nano, id);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed cursor: " + token, e);
        }
    }

    /**
     * Decodes an optional cursor, returning empty for null or blank input.
     *
     * @param token possibly-absent cursor token
     * @return decoded cursor, or {@link java.util.Optional#empty()}
     * @throws IllegalArgumentException when the token is not a valid cursor
     */
    public static java.util.Optional<MessageCursor> decodeOptional(final String token) {
        if (token == null || token.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(decode(token));
    }

    /**
     * Returns the creation instant carried by this cursor.
     *
     * @return creation instant
     */
    public Instant createdAt() {
        return Instant.ofEpochSecond(epochSecond, nano);
    }

    /**
     * Returns the message identifier carried by this cursor.
     *
     * @return message identifier
     */
    public String id() {
        return id;
    }

    /**
     * Tests whether this position is strictly older than another, breaking ties by id.
     *
     * @param other comparison target
     * @return true when this cursor sorts before {@code other}
     */
    public boolean isOlderThan(final MessageCursor other) {
        return compareTo(other) < 0;
    }

    /**
     * Tests whether this position is strictly newer than another, breaking ties by id.
     *
     * @param other comparison target
     * @return true when this cursor sorts after {@code other}
     */
    public boolean isNewerThan(final MessageCursor other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(final MessageCursor other) {
        final var bySecond = Long.compare(epochSecond, other.epochSecond);
        if (bySecond != 0) {
            return bySecond;
        }
        final var byNano = Integer.compare(nano, other.nano);
        if (byNano != 0) {
            return byNano;
        }
        return id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return "MessageCursor[" + createdAt() + ", " + id + "]";
    }
}
