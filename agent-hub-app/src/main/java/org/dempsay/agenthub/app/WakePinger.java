package org.dempsay.agenthub.app;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.dempsay.agenthub.core.port.WakeCredentials;
import org.dempsay.agenthub.core.port.WakeSettingsPort;

/**
 * Sends one HTTP POST per agent, debounced in memory.
 *
 * <p>The first relevant message schedules a ping after the current
 * {@code wakeDelaySeconds}. Further relevant messages for that agent before the
 * timer fires do not add pings. There is no retry. A delay of 0 still runs on
 * the scheduler thread, so the message post never waits on the network.
 *
 * <p>Logs name the agent and the status code or a redacted error. The secret and
 * the URL are removed from anything written here.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public final class WakePinger implements AutoCloseable {
    static final Logger LOG = Logger.getLogger(WakePinger.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final var thread = new Thread(runnable, "agent-wake");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    private volatile WakeSettingsPort settings;

    /**
     * Supplies settings. Called once the service graph exists.
     *
     * @param settings wake settings
     */
    public void attach(final WakeSettingsPort settings) {
        this.settings = settings;
    }

    /**
     * Schedules one ping for the agent if one is not already waiting.
     *
     * <p>This method does not perform network I/O and does not throw.
     *
     * @param agentId agent id
     */
    public void schedule(final String agentId) {
        final var current = settings;
        if (current == null || agentId == null || agentId.isBlank()) {
            return;
        }
        final int delay = Math.max(0, current.delaySeconds());
        try {
            pending.compute(agentId, (id, existing) -> {
                if (existing != null && !existing.isDone()) {
                    return existing;
                }
                final ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];
                holder[0] = scheduler.schedule(() -> fire(id, holder[0]), delay, TimeUnit.SECONDS);
                return holder[0];
            });
        } catch (final RejectedExecutionException e) {
            LOG.warning("wake ping agent=" + agentId + " error=" + e.getClass().getSimpleName());
        }
    }

    /**
     * Sends one ping now and returns the outcome. Used by the administrator test route.
     *
     * <p>May block for up to about five seconds. Does not log the secret.
     *
     * @param agentId agent id
     * @return status code, a redacted error, or not-configured
     */
    public WakePingResult sendNow(final String agentId) {
        final var current = settings;
        if (current == null) {
            return WakePingResult.failed("wake sender is not ready");
        }
        final UUID id;
        try {
            id = UUID.fromString(agentId);
        } catch (final IllegalArgumentException e) {
            return WakePingResult.unconfigured();
        }
        final var credentials = current.credentials(id);
        if (credentials.isEmpty()) {
            return WakePingResult.unconfigured();
        }
        return post(credentials.get());
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        http.close();
    }

    private void fire(final String agentId, final ScheduledFuture<?> scheduled) {
        pending.remove(agentId, scheduled);
        final var result = sendNow(agentId);
        if (result.statusCode() != null) {
            LOG.info("wake ping agent=" + agentId + " status=" + result.statusCode());
        } else if (!result.notConfigured()) {
            LOG.warning("wake ping agent=" + agentId + " error=" + result.error());
        }
    }

    private WakePingResult post(final WakeCredentials credentials) {
        try {
            final var builder = HttpRequest.newBuilder(URI.create(credentials.url()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"));
            if (credentials.key() != null && !credentials.key().isBlank()) {
                builder.header(credentials.headerName(), credentials.key());
            }
            final var response = http.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            return WakePingResult.status(response.statusCode());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return WakePingResult.failed(redact(e, credentials));
        } catch (final IllegalArgumentException | IOException e) {
            return WakePingResult.failed(redact(e, credentials));
        }
    }

    /**
     * Removes the secret and the URL from an error message before it is logged or returned.
     *
     * @param error failure from the HTTP client
     * @param credentials material that must not appear in the text
     * @return a short description
     */
    static String redact(final Throwable error, final WakeCredentials credentials) {
        var detail = error.getClass().getSimpleName();
        final var message = error.getMessage();
        if (message != null && !message.isBlank()) {
            var cleaned = message;
            if (credentials != null && credentials.key() != null && !credentials.key().isBlank()) {
                cleaned = cleaned.replace(credentials.key(), "[redacted]");
            }
            if (credentials != null && credentials.url() != null && !credentials.url().isBlank()) {
                cleaned = cleaned.replace(credentials.url(), "[url]");
            }
            detail = detail + ": " + cleaned;
        }
        return detail;
    }

    /**
     * Outcome of one wake POST.
     *
     * @param statusCode HTTP status from the wake URL, or null when the call did not complete
     * @param error redacted error, or null when a status was received
     * @param notConfigured true when the agent has no wake URL
     */
    public record WakePingResult(Integer statusCode, String error, boolean notConfigured) {
        static WakePingResult status(final int statusCode) {
            return new WakePingResult(statusCode, null, false);
        }

        static WakePingResult failed(final String error) {
            return new WakePingResult(null, error, false);
        }

        static WakePingResult unconfigured() {
            return new WakePingResult(null, null, true);
        }
    }
}
