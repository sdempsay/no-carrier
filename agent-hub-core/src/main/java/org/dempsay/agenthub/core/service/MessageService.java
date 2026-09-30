package org.dempsay.agenthub.core.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.dempsay.aether.api.access.AetherPrincipal;
import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.aether.api.store.UpdateOptions;
import org.dempsay.agenthub.api.model.MessageDto;
import org.dempsay.agenthub.api.model.MessageDtoStore;
import org.dempsay.agenthub.core.port.MessagePort;
import org.dempsay.agenthub.core.port.NotificationPort;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Default message service using Aether stores.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public class MessageService implements MessagePort {
    private final MessageDtoStore store;
    private final NotificationPort notifications;

    public MessageService(final MessageDtoStore store, final NotificationPort notifications) {
        this.store = store;
        this.notifications = notifications;
    }

    @Override
    public ExceptionalResponse<AetherPersisted<MessageDto>> create(final MessageDto dto) {
        final var response = store.create(e -> { }, AetherPrincipal.system(), dto);
        if (response.wasNoError()) {
            notifications.notifyMessagePosted(dto.channelId());
            return ExceptionalResponse.success(response.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }

    @Override
    public ExceptionalResponse<AetherPersisted<MessageDto>> reply(final MessageDto dto) {
        if (dto.parentMessageId() == null || dto.parentMessageId().isBlank()) {
            return ExceptionalResponse.failure();
        }

        final var parent = store.read(e -> { }, AetherPrincipal.system(), dto.parentMessageId());
        if (parent.wasError()) {
            return ExceptionalResponse.failure();
        }

        final var parentMessage = parent.response().resource();
        final String threadId;
        if (parentMessage.threadId() == null || parentMessage.threadId().isBlank()) {
            threadId = dto.parentMessageId();
        } else {
            threadId = parentMessage.threadId();
        }

        final var replyDto = new MessageDto(
                dto.channelId(),
                threadId,
                dto.parentMessageId(),
                dto.authorAgentId(),
                dto.body(),
                dto.format(),
                dto.deleted(),
                dto.deletedAt(),
                dto.deletedByAgentId());

        final var response = store.create(e -> { }, AetherPrincipal.system(), replyDto);
        if (response.wasNoError()) {
            notifications.notifyMessagePosted(dto.channelId());
            return ExceptionalResponse.success(response.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }

    @Override
    public ExceptionalResponse<AetherPersisted<MessageDto>> get(final UUID messageId) {
        final var response = store.read(e -> { }, AetherPrincipal.system(), messageId.toString());
        if (response.wasNoError()) {
            return ExceptionalResponse.success(response.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }

    @Override
    public ExceptionalResponse<List<AetherPersisted<MessageDto>>> list(final String channelId, final int limit, final String before, final String after) {
        final var response = store.list(e -> { }, AetherPrincipal.system());
        if (!response.wasNoError()) {
            return ExceptionalResponse.failure();
        }

        List<AetherPersisted<MessageDto>> messages = new ArrayList<>();
        response.response().forEach(m -> {
            if (channelId.equals(m.resource().channelId())) {
                messages.add(m);
            }
        });

        // Sort by (createdAt, id) descending - newest first
        messages.sort(Comparator
            .comparing((AetherPersisted<MessageDto> m) -> m.metadata().createdAt())
            .thenComparing((AetherPersisted<MessageDto> m) -> m.metadata().id())
            .reversed());

        // Apply cursor filtering
        if (before != null && !before.isBlank()) {
            final Instant beforeInstant = Instant.parse(before);
            messages.removeIf((AetherPersisted<MessageDto> m) -> !m.metadata().createdAt().isBefore(beforeInstant));
        }
        if (after != null && !after.isBlank()) {
            final Instant afterInstant = Instant.parse(after);
            messages.removeIf((AetherPersisted<MessageDto> m) -> !m.metadata().createdAt().isAfter(afterInstant));
        }

        // Apply limit
        final int effectiveLimit = Math.min(limit > 0 ? limit : 50, 100);
        return ExceptionalResponse.success(messages.stream()
            .limit(effectiveLimit)
            .collect(Collectors.toList()));
    }

    @Override
    public ExceptionalResponse<AetherPersisted<MessageDto>> delete(final UUID messageId, final String deletedByAgentId, final String deletedAt) {
        final var response = store.read(e -> { }, AetherPrincipal.system(), messageId.toString());
        if (!response.wasNoError()) {
            return ExceptionalResponse.failure();
        }

        var message = response.response().resource();
        var updated = new MessageDto(
                message.channelId(),
                message.threadId(),
                message.parentMessageId(),
                message.authorAgentId(),
                "",
                message.format(),
                true,
                deletedAt,
                deletedByAgentId);

        final var updateResponse = store.update(e -> { }, AetherPrincipal.system(), messageId.toString(),
                updated, response.response().metadata().version(), UpdateOptions.defaults());
        if (updateResponse.wasNoError()) {
            return ExceptionalResponse.success(updateResponse.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }
}