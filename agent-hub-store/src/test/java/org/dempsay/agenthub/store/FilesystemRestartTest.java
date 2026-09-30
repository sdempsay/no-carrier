package org.dempsay.agenthub.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.dempsay.agenthub.api.model.AgentDto;
import org.dempsay.agenthub.api.model.ChannelDto;
import org.dempsay.agenthub.api.model.MessageDto;
import org.dempsay.agenthub.core.port.AgentRegistryPort;
import org.dempsay.agenthub.core.port.ChannelPort;
import org.dempsay.agenthub.core.port.MessagePort;
import org.dempsay.agenthub.core.service.AgentRegistryService;
import org.dempsay.agenthub.core.service.ChannelService;
import org.dempsay.agenthub.core.service.MessageService;
import org.dempsay.agenthub.core.service.NoOpNotificationPort;
import org.dempsay.agenthub.store.fs.FsAgentDtoStore;
import org.dempsay.agenthub.store.fs.FsChannelDtoStore;
import org.dempsay.agenthub.store.fs.FsMessageDtoStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Filesystem persistence across a simulated restart.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
class FilesystemRestartTest {
    private AtomicReference<Exception> error;

    private AgentRegistryPort agents;
    private ChannelPort channels;
    private MessagePort messages;

    @Test
    void historySurvivesRestart(@TempDir final Path root) {
        error = new AtomicReference<>();
        bind(root);

        final var agentId = agents.create(new AgentDto("sysop", "human", null, null, null, null, "online", null, "active"))
                .response().metadata().id();
        final var channelId = channels.create(new ChannelDto("general", "General", "public", agentId, false, null, null, null, null))
                .response().metadata().id();
        final var rootId = messages.create(new MessageDto(channelId, null, null, agentId, "Welcome to no-carrier", "plain", false, null, null))
                .response().metadata().id();
        messages.reply(new MessageDto(channelId, null, rootId, agentId, "First reply", "plain", false, null, null));
        messages.create(new MessageDto(channelId, null, null, agentId, "Second post", "plain", false, null, null));

        bind(root);

        assertEquals(1, agents.list().response().size());
        assertEquals(channelId, channels.getBySlug("general").response().metadata().id());

        final var reloaded = messages.list(channelId, 50, null, null);
        assertTrue(reloaded.wasNoError(), () -> String.valueOf(error.get()));
        assertEquals(3, reloaded.response().size());
        assertEquals("Second post", reloaded.response().get(0).resource().body());
        assertEquals("Welcome to no-carrier", reloaded.response().get(2).resource().body());

        final var reply = reloaded.response().stream()
                .filter(m -> "First reply".equals(m.resource().body()))
                .findFirst();
        assertTrue(reply.isPresent());
        assertEquals(rootId, reply.get().resource().threadId());
        assertEquals(rootId, reply.get().resource().parentMessageId());
    }

    @Test
    void tombstoneSurvivesRestart(@TempDir final Path root) {
        error = new AtomicReference<>();
        bind(root);

        final var agentId = agents.create(new AgentDto("sysop", "human", null, null, null, null, "online", null, "active"))
                .response().metadata().id();
        final var channelId = channels.create(new ChannelDto("general", "General", "public", agentId, false, null, null, null, null))
                .response().metadata().id();
        final var messageId = messages.create(new MessageDto(channelId, null, null, agentId, "Oops", "plain", false, null, null))
                .response().metadata().id();

        assertTrue(messages.delete(UUID.fromString(messageId), agentId, "2026-09-29T12:00:00Z").wasNoError());

        bind(root);

        final var reloaded = messages.get(UUID.fromString(messageId));
        assertTrue(reloaded.wasNoError(), () -> String.valueOf(error.get()));
        assertTrue(reloaded.response().resource().deleted());
        assertEquals("", reloaded.response().resource().body());
        assertTrue(channels.getBySlug("general").wasNoError());
    }

    private void bind(final Path root) {
        agents = new AgentRegistryService(new FsAgentDtoStore(root));
        channels = new ChannelService(new FsChannelDtoStore(root));
        messages = new MessageService(new FsMessageDtoStore(root), new NoOpNotificationPort());
    }
}
