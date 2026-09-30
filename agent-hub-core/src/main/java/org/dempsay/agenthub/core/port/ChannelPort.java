package org.dempsay.agenthub.core.port;

import java.util.List;
import java.util.UUID;

import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.agenthub.api.model.ChannelDto;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Public channel operations.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface ChannelPort {
    /**
     * Create a new public channel.
     *
     * @param dto channel data
     * @return created channel with metadata or failure
     */
    ExceptionalResponse<AetherPersisted<ChannelDto>> create(ChannelDto dto);

    /**
     * Get a channel by id.
     *
     * @param channelId channel metadata id
     * @return channel with metadata or failure
     */
    ExceptionalResponse<AetherPersisted<ChannelDto>> get(UUID channelId);

    /**
     * Get a channel by slug.
     *
     * @param slug channel slug
     * @return channel with metadata or failure
     */
    ExceptionalResponse<AetherPersisted<ChannelDto>> getBySlug(String slug);

    /**
     * List all public channels.
     *
     * @return list of channels with metadata
     */
    ExceptionalResponse<List<AetherPersisted<ChannelDto>>> listPublic();

    /**
     * Update channel display name.
     *
     * @param channelId channel metadata id
     * @param name new display name
     * @return updated channel with metadata or failure
     */
    ExceptionalResponse<AetherPersisted<ChannelDto>> updateName(UUID channelId, String name);

    /**
     * Soft-delete a channel.
     *
     * @param channelId channel metadata id
     * @param deletedByAgentId deleting agent metadata id
     * @param deletedAt deletion timestamp
     * @return updated channel with metadata or failure
     */
    ExceptionalResponse<AetherPersisted<ChannelDto>> delete(UUID channelId, String deletedByAgentId, String deletedAt);
}