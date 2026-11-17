package org.dempsay.agenthub.app;

import java.io.IOException;
import java.util.UUID;

import org.dempsay.agenthub.app.config.AppConfig;
import org.dempsay.agenthub.app.http.ApiRoutes;
import org.dempsay.agenthub.app.http.HttpAdapter;
import org.dempsay.agenthub.core.service.BootstrapCredentialService;
import org.dempsay.agenthub.store.HubServiceFactory;

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
    private final WakePinger wakePinger;

    private HubRuntime(final AppConfig config, final HttpAdapter http, final String bootstrapAdminKey,
            final WakePinger wakePinger) {
        this.config = config;
        this.http = http;
        this.bootstrapAdminKey = bootstrapAdminKey;
        this.wakePinger = wakePinger;
    }

    /**
     * Starts the hub, bootstrapping an administrator on an empty store.
     *
     * @param config runtime configuration
     * @return a started runtime
     * @throws IOException if the socket cannot be bound
     */
    public static HubRuntime start(final AppConfig config) throws IOException {
        final HttpAdapter http = new HttpAdapter(config.port(), config.bindAddress());
        final var wakeups = new ChannelWakeups();
        final var pinger = new WakePinger();
        final var notifier = new AgentWakeNotifier(pinger);
        final var graph = HubServiceFactory.create(config.persistent(), config.dataDir(),
                config.pbkdf2Iterations(),
                new CompositeNotificationPort(new WakeupNotificationPort(wakeups), notifier));
        notifier.attach(graph);
        pinger.attach(graph.wakeSettings());

        String adminKey = null;
        if (!graph.bootstrap().adminExists()) {
            adminKey = UUID.randomUUID().toString().replace("-", "");
            if (graph.bootstrap().initializeAdmin(BootstrapCredentialService.ADMIN_NAME, adminKey).wasError()) {
                pinger.close();
                http.close();
                throw new IllegalStateException("Unable to bootstrap the administrator credential");
            }
        }

        new ApiRoutes(http, graph, config.maxMessageSize(), wakeups, pinger).register();
        http.start();
        return new HubRuntime(config, http, adminKey, pinger);
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
        wakePinger.close();
    }
}
