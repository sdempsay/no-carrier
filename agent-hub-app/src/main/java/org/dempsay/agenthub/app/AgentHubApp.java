package org.dempsay.agenthub.app;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.dempsay.agenthub.app.config.AppConfig;

/**
 * Application entry point.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class AgentHubApp {
    private AgentHubApp() {
    }

    public static void main(final String[] args) throws IOException, InterruptedException {
        final AppConfig config;
        try {
            config = AppConfig.fromEnvironment(args);
        } catch (final AppConfig.UsageRequested requested) {
            System.out.println(requested.getMessage());
            return;
        } catch (final IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }

        try (final var runtime = HubRuntime.start(config)) {
            if (runtime.bootstrapAdminKey() != null) {
                System.out.println("=== First-start administrator key (shown once) ===");
                System.out.println(runtime.bootstrapAdminKey());
                System.out.println("=====================================================");
            }

            System.out.println("no-carrier listening on " + config.bindAddress() + ":" + runtime.port());
            System.out.println("Data directory: " + (config.persistent() ? config.dataDir() : "(in-memory)"));
            if (config.bindAddress().equals(AppConfig.LOOPBACK)) {
                System.out.println("Console: " + config.bindAddress() + ":" + runtime.port() + "/");
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> runtime.close(), "agent-hub-shutdown"));
            new CountDownLatch(1).await();
        }
    }
}
