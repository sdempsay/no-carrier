package org.dempsay.agenthub.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.dempsay.agenthub.api.model.AgentCredentialDtoStore;
import org.dempsay.agenthub.api.model.AgentDtoStore;
import org.dempsay.agenthub.api.model.ChannelDtoStore;
import org.dempsay.agenthub.api.model.MessageDtoStore;
import org.dempsay.agenthub.core.port.CredentialHashPort;
import org.dempsay.agenthub.core.service.AgentRegistryService;
import org.dempsay.agenthub.core.service.AuthorizationService;
import org.dempsay.agenthub.core.service.BearerAuthService;
import org.dempsay.agenthub.core.service.BootstrapCredentialService;
import org.dempsay.agenthub.core.service.ChannelService;
import org.dempsay.agenthub.core.service.CredentialServiceImpl;
import org.dempsay.agenthub.core.service.MessageService;
import org.dempsay.agenthub.core.port.NotificationPort;
import org.dempsay.agenthub.store.fs.FsAgentCredentialDtoStore;
import org.dempsay.agenthub.store.fs.FsAgentDtoStore;
import org.dempsay.agenthub.store.fs.FsChannelDtoStore;
import org.dempsay.agenthub.store.fs.FsMessageDtoStore;
import org.dempsay.agenthub.store.hash.Pbkdf2CredentialHasher;
import org.dempsay.agenthub.store.memory.MemoryAgentCredentialDtoStore;
import org.dempsay.agenthub.store.memory.MemoryAgentDtoStore;
import org.dempsay.agenthub.store.memory.MemoryChannelDtoStore;
import org.dempsay.agenthub.store.memory.MemoryMessageDtoStore;

/**
 * Builds the service graph, choosing memory or filesystem stores.
 *
 * <p>The server and the command-line adapter share this factory rather than each
 * wiring stores by hand. A second copy would be free to drift from the first, and a
 * drift there is silent: the board would hash a credential one way on one path and
 * another way on the other.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public final class HubServiceFactory {
    private HubServiceFactory() {
    }

    /**
     * Wires every service against the requested store.
     *
     * @param persistent when true, bind filesystem stores under {@code dataDir};
     *                   when false, bind in-memory stores and ignore {@code dataDir}
     * @param dataDir data directory for the filesystem stores
     * @param pbkdf2Iterations credential hashing cost
     * @param notifications wake-up sink for the message service
     * @return the wired service graph
     */
    public static HubServiceGraph create(final boolean persistent, final Path dataDir, final int pbkdf2Iterations,
            final NotificationPort notifications) {
        if (persistent) {
            createDirectories(dataDir);
        }

        final AgentDtoStore agentStore = persistent ? new FsAgentDtoStore(dataDir) : new MemoryAgentDtoStore();
        final AgentCredentialDtoStore credentialStore = persistent
                ? new FsAgentCredentialDtoStore(dataDir)
                : new MemoryAgentCredentialDtoStore();
        final ChannelDtoStore channelStore = persistent ? new FsChannelDtoStore(dataDir) : new MemoryChannelDtoStore();
        final MessageDtoStore messageStore = persistent ? new FsMessageDtoStore(dataDir) : new MemoryMessageDtoStore();

        final CredentialHashPort hasher = new Pbkdf2CredentialHasher(pbkdf2Iterations);
        final var agents = new AgentRegistryService(agentStore);
        final var credentials = new CredentialServiceImpl(credentialStore, hasher);

        return new HubServiceGraph(
                agents,
                credentials,
                new ChannelService(channelStore),
                new MessageService(messageStore, notifications),
                new BearerAuthService(credentialStore, agentStore, hasher),
                new AuthorizationService(),
                notifications,
                new BootstrapCredentialService(agents, credentials));
    }

    private static void createDirectories(final Path dataDir) {
        try {
            Files.createDirectories(dataDir);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to create data directory " + dataDir, e);
        }
    }
}