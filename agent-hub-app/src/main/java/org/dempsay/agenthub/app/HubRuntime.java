package org.dempsay.agenthub.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.UUID;

import org.dempsay.agenthub.api.model.AgentCredentialDtoStore;
import org.dempsay.agenthub.api.model.AgentDtoStore;
import org.dempsay.agenthub.api.model.ChannelDtoStore;
import org.dempsay.agenthub.api.model.MessageDtoStore;
import org.dempsay.agenthub.app.config.AppConfig;
import org.dempsay.agenthub.app.http.ApiRoutes;
import org.dempsay.agenthub.app.http.HttpAdapter;
import org.dempsay.agenthub.core.port.AgentRegistryPort;
import org.dempsay.agenthub.core.port.AuthorizationPort;
import org.dempsay.agenthub.core.port.BearerAuthPort;
import org.dempsay.agenthub.core.port.BootstrapCredentialPort;
import org.dempsay.agenthub.core.port.ChannelPort;
import org.dempsay.agenthub.core.port.CredentialHashPort;
import org.dempsay.agenthub.core.port.CredentialServicePort;
import org.dempsay.agenthub.core.port.MessagePort;
import org.dempsay.agenthub.core.service.AgentRegistryService;
import org.dempsay.agenthub.core.service.AuthorizationService;
import org.dempsay.agenthub.core.service.BearerAuthService;
import org.dempsay.agenthub.core.service.ChannelService;
import org.dempsay.agenthub.core.service.CredentialServiceImpl;
import org.dempsay.agenthub.core.service.MessageService;
import org.dempsay.agenthub.core.service.NoOpNotificationPort;
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
 * Wires the full hub and serves HTTP. Used by {@link AgentHubApp} and by tests
 * that need a real server on an ephemeral port.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class HubRuntime implements AutoCloseable {
    private final AppConfig config;
    private final HttpAdapter http;
    private final String bootstrapAdminKey;

    private HubRuntime(final AppConfig config, final HttpAdapter http, final String bootstrapAdminKey) {
        this.config = config;
        this.http = http;
        this.bootstrapAdminKey = bootstrapAdminKey;
    }

    /**
     * Starts the hub, bootstrapping an administrator on an empty store.
     *
     * @param config runtime configuration
     * @return a started runtime
     * @throws IOException if the socket cannot be bound
     */
    public static HubRuntime start(final AppConfig config) throws IOException {
        if (config.persistent()) {
            try {
                Files.createDirectories(config.dataDir());
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to create data directory " + config.dataDir(), e);
            }
        }

        final AgentDtoStore agentStore = config.persistent()
                ? new FsAgentDtoStore(config.dataDir())
                : new MemoryAgentDtoStore();
        final AgentCredentialDtoStore credentialStore = config.persistent()
                ? new FsAgentCredentialDtoStore(config.dataDir())
                : new MemoryAgentCredentialDtoStore();
        final ChannelDtoStore channelStore = config.persistent()
                ? new FsChannelDtoStore(config.dataDir())
                : new MemoryChannelDtoStore();
        final MessageDtoStore messageStore = config.persistent()
                ? new FsMessageDtoStore(config.dataDir())
                : new MemoryMessageDtoStore();

        final CredentialHashPort hasher = new Pbkdf2CredentialHasher(config.pbkdf2Iterations());
        final AgentRegistryPort agents = new AgentRegistryService(agentStore);
        final CredentialServicePort credentials = new CredentialServiceImpl(credentialStore, hasher);
        final ChannelPort channels = new ChannelService(channelStore);
        final MessagePort messages = new MessageService(messageStore, new NoOpNotificationPort());
        final BearerAuthPort auth = new BearerAuthService(credentialStore, agentStore, hasher);
        final AuthorizationPort authorization = new AuthorizationService();
        final BootstrapCredentialPort bootstrap = new BootstrapCredentialService(agents, credentials);

        String adminKey = null;
        if (!bootstrap.adminExists()) {
            adminKey = UUID.randomUUID().toString().replace("-", "");
            if (bootstrap.initializeAdmin(BootstrapCredentialService.ADMIN_NAME, adminKey).wasError()) {
                throw new IllegalStateException("Unable to bootstrap the administrator credential");
            }
        }

        final HttpAdapter http = new HttpAdapter(config.port(), config.bindAddress());
        final var services = new HubServices(agents, credentials, channels, messages, auth, authorization);
        new ApiRoutes(http, services, config.maxMessageSize()).register();
        http.start();

        return new HubRuntime(config, http, adminKey);
    }

    public int port() {
        return http.port();
    }

    /**
     * The one-time administrator key, or {@code null} when the store was not empty.
     *
     * @return the bootstrap key
     */
    public String bootstrapAdminKey() {
        return bootstrapAdminKey;
    }

    public AppConfig config() {
        return config;
    }

    @Override
    public void close() {
        http.close();
    }
}
