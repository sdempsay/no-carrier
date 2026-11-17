package org.dempsay.agenthub.core.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Decides which agents should receive a wake ping for one stored message.
 *
 * <p>An agent is selected when it has a wake URL and either the channel slug matches
 * its name (its home channel), or the message is a reply in a thread that agent
 * started or has posted in. The author of the message is never selected. A tombstone
 * selects nobody.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class WakeRelevance {
    private WakeRelevance() {
    }

    /**
     * One agent the selector can consider.
     *
     * @param agentId agent id
     * @param name agent name, compared to the channel slug without regard to case
     * @param wakeConfigured whether the agent has a wake URL
     */
    public record Candidate(String agentId, String name, boolean wakeConfigured) {
    }

    /**
     * Returns the ids of agents that should be pinged, in candidate order, without duplicates.
     *
     * @param deleted whether the message is a tombstone
     * @param authorAgentId author of the message
     * @param channelSlug slug of the channel, compared to each agent's name
     * @param reply whether the message is a reply rather than a new thread
     * @param participantAgentIds authors of the thread root and its replies, including tombstones
     * @param agents agents to consider
     * @return agent ids to ping
     */
    public static List<String> select(
            final boolean deleted,
            final String authorAgentId,
            final String channelSlug,
            final boolean reply,
            final Set<String> participantAgentIds,
            final List<Candidate> agents) {
        if (deleted || agents == null || agents.isEmpty()) {
            return List.of();
        }
        final var selected = new LinkedHashSet<String>();
        for (final var agent : agents) {
            if (Objects.isNull(agent) || Objects.isNull(agent.agentId()) || !agent.wakeConfigured()) {
                continue;
            }
            if (sameAgent(agent.agentId(), authorAgentId)) {
                continue;
            }
            final var home = Objects.nonNull(channelSlug) && Objects.nonNull(agent.name())
                    && channelSlug.equalsIgnoreCase(agent.name().trim());
            final var participated = reply && containsAgent(participantAgentIds, agent.agentId());
            if (home || participated) {
                selected.add(agent.agentId());
            }
        }
        return List.copyOf(selected);
    }

    /**
     * Collects the authors of one thread from a channel's messages.
     *
     * <p>A message belongs to the thread when its id is the root or its thread id is the root.
     * Tombstones still count: the author did post.
     *
     * @param threadRootId root message id
     * @param messageIds parallel ids, same length as {@code threadIds} and {@code authors}
     * @param threadIds thread id of each message, null for a root that does not point at itself
     * @param authors author of each message
     * @return author ids in the thread
     */
    public static Set<String> participants(
            final String threadRootId,
            final List<String> messageIds,
            final List<String> threadIds,
            final List<String> authors) {
        final var found = new LinkedHashSet<String>();
        if (Objects.isNull(threadRootId) || threadRootId.isBlank() || Objects.isNull(messageIds)) {
            return found;
        }
        for (int i = 0; i < messageIds.size(); i++) {
            final var id = messageIds.get(i);
            final var threadId = Objects.isNull(threadIds) || i >= threadIds.size() ? null : threadIds.get(i);
            final var author = Objects.isNull(authors) || i >= authors.size() ? null : authors.get(i);
            if (Objects.isNull(author) || author.isBlank()) {
                continue;
            }
            if (threadRootId.equals(id) || threadRootId.equals(threadId)) {
                found.add(author);
            }
        }
        return found;
    }

    private static boolean containsAgent(final Set<String> ids, final String agentId) {
        if (Objects.isNull(ids) || ids.isEmpty()) {
            return false;
        }
        for (final var id : ids) {
            if (sameAgent(id, agentId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameAgent(final String left, final String right) {
        if (Objects.isNull(left) || Objects.isNull(right)) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        final var parsed = ExceptionalSupplier.of(() ->
                Boolean.valueOf(UUID.fromString(left).equals(UUID.fromString(right)))).execute();
        return parsed.wasNoError() && Boolean.TRUE.equals(parsed.response());
    }
}
