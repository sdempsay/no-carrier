package org.dempsay.agenthub.app;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Ephemeral per-channel wake-up signals backing long polling.
 *
 * <p>Callers observe a monotonic per-channel epoch rather than a consumable flag, so a signal
 * delivered while several requests are parked wakes all of them instead of only the first.
 * Nothing here is persisted: this is a hint that a reader should re-read the store, which keeps
 * the "no durable delivery state" decision intact.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public final class ChannelWakeups {
    private final Map<String, Signal> channels = new ConcurrentHashMap<>();

    /**
     * Returns the current epoch for a channel, creating its signal on first use.
     *
     * @param channelId channel metadata id
     * @return current epoch
     */
    public long epoch(final String channelId) {
        return channel(channelId).epoch();
    }

    /**
     * Signals every waiter on a channel that the store may have changed.
     *
     * @param channelId channel metadata id
     */
    public void signal(final String channelId) {
        channel(channelId).signal();
    }

    /**
     * Waits for the channel epoch to move away from {@code seen}.
     *
     * @param channelId channel metadata id
     * @param seen epoch observed by the caller before its previous read
     * @param timeout how long to wait
     * @return true when the epoch changed before the timeout
     * @throws InterruptedException when the calling thread is interrupted
     */
    public boolean awaitChange(final String channelId, final long seen, final Duration timeout)
            throws InterruptedException {
        if (timeout.isZero() || timeout.isNegative()) {
            return false;
        }
        return channel(channelId).awaitChange(seen, timeout);
    }

    private Signal channel(final String channelId) {
        return channels.computeIfAbsent(channelId, key -> new Signal());
    }

    /** Monitor guarding one channel's epoch. */
    private static final class Signal {
        private long epoch;

        synchronized long epoch() {
            return epoch;
        }

        synchronized void signal() {
            epoch++;
            notifyAll();
        }

        synchronized boolean awaitChange(final long seen, final Duration timeout) throws InterruptedException {
            if (epoch != seen) {
                return true;
            }
            final long deadline = System.nanoTime() + timeout.toNanos();
            long remaining = timeout.toNanos();
            while (remaining > 0 && epoch == seen) {
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
                remaining = deadline - System.nanoTime();
            }
            return epoch != seen;
        }
    }
}
