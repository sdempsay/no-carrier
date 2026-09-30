package org.dempsay.agenthub.core.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.dempsay.aether.api.access.AetherPrincipal;
import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.aether.api.store.UpdateOptions;
import org.dempsay.agenthub.api.model.ChannelDto;
import org.dempsay.agenthub.api.model.ChannelDtoStore;
import org.dempsay.agenthub.core.port.ChannelPort;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Default channel service using Aether stores.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public class ChannelService implements ChannelPort {
    private final ChannelDtoStore store;

    public ChannelService(final ChannelDtoStore store) {
        this.store = store;
    }

    @Override
    public ExceptionalResponse<AetherPersisted<ChannelDto>> create(final ChannelDto dto) {
        final var response = store.create(e -> { }, AetherPrincipal.system(), dto);
        if (response.wasNoError()) {
            return ExceptionalResponse.success(response.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }

    @Override
    public ExceptionalResponse<AetherPersisted<ChannelDto>> get(final UUID channelId) {
        final var response = store.read(e -> { }, AetherPrincipal.system(), channelId.toString());
        if (response.wasNoError()) {
            return ExceptionalResponse.success(response.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }

    @Override
    public ExceptionalResponse<AetherPersisted<ChannelDto>> getBySlug(final String slug) {
        final var response = store.list(e -> { }, AetherPrincipal.system());
        if (response.wasNoError()) {
            return response.response().stream()
                .filter(c -> slug.equals(c.resource().slug()))
                .filter(c -> !Boolean.TRUE.equals(c.resource().deleted()))
                .findFirst()
                .map(ExceptionalResponse::success)
                .orElse(ExceptionalResponse.failure());
        } else {
            return ExceptionalResponse.failure();
        }
    }

    @Override
    public ExceptionalResponse<List<AetherPersisted<ChannelDto>>> listPublic() {
        final var response = store.list(e -> { }, AetherPrincipal.system());
        if (response.wasError()) {
            return ExceptionalResponse.failure();
        }
        return ExceptionalResponse.success(response.response().stream()
            .filter(c -> "public".equals(c.resource().visibility()))
            .filter(c -> !Boolean.TRUE.equals(c.resource().deleted()))
            .collect(Collectors.toList()));
    }

    @Override
    public ExceptionalResponse<AetherPersisted<ChannelDto>> updateName(final UUID channelId, final String name) {
        final var response = store.read(e -> { }, AetherPrincipal.system(), channelId.toString());
        if (!response.wasNoError()) {
            return ExceptionalResponse.failure();
        }

        var channel = response.response().resource();
        var updated = new ChannelDto(
                channel.slug(),
                name,
                channel.visibility(),
                channel.createdByAgentId(),
                channel.deleted(),
                channel.deletedAt(),
                channel.deletedByAgentId(),
                channel.restoredAt(),
                channel.restoredByAgentId());

        final var updateResponse = store.update(e -> { }, AetherPrincipal.system(), channelId.toString(),
                updated, response.response().metadata().version(), UpdateOptions.defaults());
        if (updateResponse.wasNoError()) {
            return ExceptionalResponse.success(updateResponse.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }

    @Override
    public ExceptionalResponse<AetherPersisted<ChannelDto>> delete(final UUID channelId, final String deletedByAgentId, final String deletedAt) {
        final var response = store.read(e -> { }, AetherPrincipal.system(), channelId.toString());
        if (!response.wasNoError()) {
            return ExceptionalResponse.failure();
        }

        var channel = response.response().resource();
        var updated = new ChannelDto(
                channel.slug(),
                channel.name(),
                channel.visibility(),
                channel.createdByAgentId(),
                true,
                deletedAt,
                deletedByAgentId,
                null,
                null);

        final var updateResponse = store.update(e -> { }, AetherPrincipal.system(), channelId.toString(),
                updated, response.response().metadata().version(), UpdateOptions.defaults());
        if (updateResponse.wasNoError()) {
            return ExceptionalResponse.success(updateResponse.response());
        } else {
            return ExceptionalResponse.failure();
        }
    }
}