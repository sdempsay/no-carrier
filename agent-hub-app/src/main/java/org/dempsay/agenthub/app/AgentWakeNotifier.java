package org.dempsay.agenthub.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.agenthub.api.model.MessageDto;
import org.dempsay.agenthub.core.port.NotificationPort;
import org.dempsay.agenthub.core.port.PostedMessage;
import org.dempsay.agenthub.core.service.MessageCursor;
import org.dempsay.agenthub.core.service.WakeRelevance;
import org.dempsay.agenthub.store.HubServiceGraph;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Schedules wake pings after a message is stored.
 *
 * <p>Work here is best-effort. A channel id that is not a UUID is ignored so the
 * post and the long-poll wake-up still succeed.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class AgentWakeNotifier implements NotificationPort {
    private static final int PAGE = 100;
    private static final int MAX_PAGES = 50;

    private final WakePinger pinger;
    private volatile HubServiceGraph services;

    public AgentWakeNotifier(final WakePinger pinger) {
        this.pinger = pinger;
    }

    /**
     * Supplies the services used to decide relevance. Called once the graph exists.
     *
     * @param services wired services
     */
    public void attach(final HubServiceGraph services) {
        this.services = services;
    }

    @Override
    public void notifyMessagePosted(final String channelId) {
        // A channel id alone is not enough to decide relevance.
    }

    @Override
    public void notifyMessagePosted(final PostedMessage message) {
        final var graph = services;
        if (Objects.isNull(graph) || Objects.isNull(message) || message.deleted()) {
            return;
        }
        consider(graph, message);
    }

    private void consider(final HubServiceGraph graph, final PostedMessage message) {
        final var parsed = ExceptionalSupplier.of(() -> UUID.fromString(message.channelId())).execute();
        if (parsed.wasError()) {
            return;
        }
        final UUID channelId = parsed.response();
        final var channel = graph.channels().get(channelId);
        if (channel.wasError()) {
            return;
        }
        final var listed = graph.agents().list();
        if (listed.wasError()) {
            return;
        }
        final var candidates = new ArrayList<WakeRelevance.Candidate>();
        listed.response().forEach(agent -> candidates.add(new WakeRelevance.Candidate(
                agent.metadata().id(),
                agent.resource().name(),
                agent.resource().wakeConfigured())));

        final boolean reply = Objects.nonNull(message.parentMessageId()) && !message.parentMessageId().isBlank();
        final var participants = reply
                ? participants(graph, message.channelId(), threadRoot(message))
                : Set.<String>of();
        final var selected = WakeRelevance.select(
                message.deleted(),
                message.authorAgentId(),
                channel.response().resource().slug(),
                reply,
                participants,
                candidates);
        for (final var agentId : selected) {
            pinger.schedule(agentId);
        }
    }

    private static String threadRoot(final PostedMessage message) {
        if (Objects.nonNull(message.threadId()) && !message.threadId().isBlank()) {
            return message.threadId();
        }
        return message.parentMessageId();
    }

    private static java.util.Set<String> participants(final HubServiceGraph graph, final String channelId,
            final String threadRootId) {
        final var ids = new ArrayList<String>();
        final var threadIds = new ArrayList<String>();
        final var authors = new ArrayList<String>();
        String before = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            final var listed = graph.messages().list(channelId, PAGE, before, null);
            if (listed.wasError() || listed.response().isEmpty()) {
                break;
            }
            final List<AetherPersisted<MessageDto>> batch = listed.response();
            for (final var item : batch) {
                ids.add(item.metadata().id());
                threadIds.add(item.resource().threadId());
                authors.add(item.resource().authorAgentId());
            }
            if (batch.size() < PAGE) {
                break;
            }
            final var oldest = batch.get(batch.size() - 1);
            final var next = MessageCursor.of(oldest.metadata().createdAt(), oldest.metadata().id()).encode();
            if (next.equals(before)) {
                break;
            }
            before = next;
        }
        return WakeRelevance.participants(threadRootId, ids, threadIds, authors);
    }
}
