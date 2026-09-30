package org.dempsay.agenthub.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.dempsay.aether.api.access.AetherPrincipal;
import org.dempsay.agenthub.api.model.AgentCredentialDto;
import org.dempsay.agenthub.api.model.AgentDto;
import org.dempsay.agenthub.api.model.ChannelDto;
import org.dempsay.agenthub.api.model.MessageDto;
import org.dempsay.agenthub.core.port.AuthPrincipal;
import org.dempsay.agenthub.core.port.AgentRegistryPort;
import org.dempsay.agenthub.core.port.AuthorizationPort;
import org.dempsay.agenthub.core.port.BearerAuthPort;
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
import org.dempsay.agenthub.store.hash.Pbkdf2CredentialHasher;
import org.dempsay.agenthub.store.memory.MemoryAgentCredentialDtoStore;
import org.dempsay.agenthub.store.memory.MemoryAgentDtoStore;
import org.dempsay.agenthub.store.memory.MemoryChannelDtoStore;
import org.dempsay.agenthub.store.memory.MemoryMessageDtoStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * In-memory tests for the core services.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
class CoreServicesInMemoryTest {
    private static final String KEY = "correct-horse-battery-staple";
    private static final String ROTATED_KEY = "rotated-horse-battery-staple";

    private MemoryAgentDtoStore agentStore;
    private MemoryAgentCredentialDtoStore credentialStore;
    private MemoryChannelDtoStore channelStore;
    private MemoryMessageDtoStore messageStore;
    private AgentRegistryPort agentRegistry;
    private CredentialServicePort credentialService;
    private ChannelPort channels;
    private MessagePort messages;
    private AuthorizationPort authorization;
    private BearerAuthPort auth;
    private AtomicReference<Exception> error;

    @BeforeEach
    void setUp() {
        error = new AtomicReference<>();
        agentStore = new MemoryAgentDtoStore();
        credentialStore = new MemoryAgentCredentialDtoStore();
        channelStore = new MemoryChannelDtoStore();
        messageStore = new MemoryMessageDtoStore();

        final CredentialHashPort hasher = new Pbkdf2CredentialHasher(1000);
        agentRegistry = new AgentRegistryService(agentStore);
        credentialService = new CredentialServiceImpl(credentialStore, hasher);
        channels = new ChannelService(channelStore);
        messages = new MessageService(messageStore, new NoOpNotificationPort());
        authorization = new AuthorizationService();
        auth = new BearerAuthService(credentialStore, agentStore, hasher);
    }

    @Test
    void agentCreateAndGet() {
        final var created = agentRegistry.create(agent("test-bot")).response();
        assertNotNull(created.metadata().id());
        assertEquals("test-bot", created.resource().name());

        final var loaded = agentRegistry.get(UUID.fromString(created.metadata().id()));
        assertTrue(loaded.wasNoError());
        assertEquals(created.metadata().id(), loaded.response().metadata().id());
        assertEquals("test-bot", loaded.response().resource().name());
    }

    @Test
    void agentGetUnknownIdFails() {
        assertTrue(agentRegistry.get(UUID.randomUUID()).wasError());
    }

    @Test
    void agentListReturnsAll() {
        agentRegistry.create(agent("bot-one"));
        agentRegistry.create(agent("bot-two"));

        final var listed = agentRegistry.list();
        assertTrue(listed.wasNoError());
        assertEquals(2, listed.response().size());
    }

    @Test
    void agentLifecycleUpdate() {
        final var id = newAgentId(agent("pending-bot", "pending"));
        final var updated = agentRegistry.updateLifecycle(UUID.fromString(id), "active");
        assertTrue(updated.wasNoError(), () -> String.valueOf(error.get()));
        assertEquals("active", updated.response().resource().lifecycleState());
    }

    @Test
    void agentStatusUpdatePreservesLifecycle() {
        final var id = newAgentId(agent("online-bot", "active"));
        final var updated = agentRegistry.updateStatus(UUID.fromString(id), "busy", "2026-09-29T12:00:00Z");
        assertTrue(updated.wasNoError());
        assertEquals("busy", updated.response().resource().declaredStatus());
        assertEquals("2026-09-29T12:00:00Z", updated.response().resource().lastSeenAt());
        assertEquals("active", updated.response().resource().lifecycleState());
    }

    @Test
    void credentialIsStoredHashedAndAuthenticates() {
        final var agentId = newAgentId(agent("test-bot"));
        credentialService.create(UUID.fromString(agentId), credential("test"), KEY);

        final var persisted = credentialStore.list(error::set, AetherPrincipal.system()).response();
        assertEquals(1, persisted.size());
        assertFalse(persisted.get(0).resource().keyHash().contains(KEY));

        final var principal = auth.authenticate(KEY);
        assertTrue(principal.wasNoError());
        assertEquals(UUID.fromString(agentId), principal.response().agentId());
        assertFalse(principal.response().isAdmin());
    }

    @Test
    void credentialRotationRevokesOldKey() {
        final var agentId = newAgentId(agent("test-bot"));
        final var original = credentialService.create(UUID.fromString(agentId), credential("first"), KEY).response();
        final var rotated = credentialService.rotate(UUID.fromString(original.metadata().id()), credential("second"), ROTATED_KEY);
        assertTrue(rotated.wasNoError());
        assertEquals("second", rotated.response().resource().label());
        assertNotEquals(original.metadata().id(), rotated.response().metadata().id());

        assertFalse(auth.authenticate(KEY).wasNoError());
        assertTrue(auth.authenticate(ROTATED_KEY).wasNoError());
    }

    @Test
    void credentialRevokeBlocksAuthentication() {
        final var agentId = newAgentId(agent("test-bot"));
        final var created = credentialService.create(UUID.fromString(agentId), credential("doomed"), KEY).response();

        final var revoked = credentialService.revoke(UUID.fromString(created.metadata().id()), "2026-09-29T12:00:00Z");
        assertTrue(revoked.wasNoError());
        assertEquals("revoked", revoked.response().resource().state());
        assertEquals("2026-09-29T12:00:00Z", revoked.response().resource().revokedAt());

        assertFalse(auth.authenticate(KEY).wasNoError());
    }

    @Test
    void bearerAuthRejectsBlankWrongAndSuspended() {
        final var agentId = newAgentId(agent("test-bot"));
        credentialService.create(UUID.fromString(agentId), credential("test"), KEY);

        assertFalse(auth.authenticate(null).wasNoError());
        assertFalse(auth.authenticate("  ").wasNoError());
        assertFalse(auth.authenticate("wrong-key").wasNoError());

        assertTrue(agentRegistry.updateLifecycle(UUID.fromString(agentId), "suspended").wasNoError());
        assertFalse(auth.authenticate(KEY).wasNoError());
    }

    @Test
    void bearerAuthTreatsAdministratorAsAdmin() {
        final var agentId = newAgentId(agent("admin"));
        credentialService.create(UUID.fromString(agentId), credential("bootstrap"), KEY);

        assertTrue(auth.authenticate(KEY).response().isAdmin());
    }

    @Test
    void channelResolvesBySlugAndId() {
        final var created = channels.create(channel("general", "General", "public")).response();
        assertNotNull(created.metadata().id());

        final var bySlug = channels.getBySlug("general");
        assertTrue(bySlug.wasNoError());
        assertEquals(created.metadata().id(), bySlug.response().metadata().id());

        final var byId = channels.get(UUID.fromString(created.metadata().id()));
        assertTrue(byId.wasNoError());
        assertEquals("general", byId.response().resource().slug());

        assertTrue(channels.getBySlug("missing").wasError());
    }

    @Test
    void channelSlugIsUnique() {
        assertTrue(channels.create(channel("general", "General", "public")).wasNoError());
        assertTrue(channels.create(channel("general", "Duplicate", "public")).wasError());
    }

    @Test
    void channelListPublicExcludesPrivate() {
        channels.create(channel("general", "General", "public"));
        channels.create(channel("random", "Random", "public"));
        channels.create(channel("secret", "Secret", "private"));

        final var listed = channels.listPublic();
        assertTrue(listed.wasNoError());
        assertEquals(2, listed.response().size());
    }

    @Test
    void channelDeleteTombstones() {
        final var created = channels.create(channel("general", "General", "public")).response();
        final var id = UUID.fromString(created.metadata().id());

        final var deleted = channels.delete(id, "agent-1", "2026-09-29T12:00:00Z");
        assertTrue(deleted.wasNoError());
        assertTrue(deleted.response().resource().deleted());
        assertEquals("2026-09-29T12:00:00Z", deleted.response().resource().deletedAt());

        assertTrue(channels.getBySlug("general").wasError());
        assertTrue(channels.listPublic().response().isEmpty());
    }

    @Test
    void channelUpdateNameKeepsSlug() {
        final var created = channels.create(channel("general", "General", "public")).response();
        final var id = UUID.fromString(created.metadata().id());

        final var renamed = channels.updateName(id, "Lobby");
        assertTrue(renamed.wasNoError());
        assertEquals("Lobby", renamed.response().resource().name());
        assertEquals("general", renamed.response().resource().slug());
    }

    @Test
    void messageListIsNewestFirstAndCursorPaginated() {
        final var channelId = newChannelId("general");
        final var authorId = newAgentId(agent("bot"));
        for (int i = 1; i <= 5; i++) {
            final var created = messages.create(message(channelId, authorId, "Message " + i));
            assertTrue(created.wasNoError());
        }

        final var firstPage = messages.list(channelId, 2, null, null);
        assertTrue(firstPage.wasNoError());
        assertEquals(2, firstPage.response().size());
        assertEquals("Message 5", firstPage.response().get(0).resource().body());
        assertEquals("Message 4", firstPage.response().get(1).resource().body());

        final var afterOldest = messages.list(channelId, 10, null, firstPage.response().get(1).metadata().createdAt().toString());
        assertEquals(1, afterOldest.response().size());
        assertEquals("Message 5", afterOldest.response().get(0).resource().body());

        final var beforeNewest = messages.list(channelId, 10, firstPage.response().get(0).metadata().createdAt().toString(), null);
        assertEquals(4, beforeNewest.response().size());
        assertEquals("Message 4", beforeNewest.response().get(0).resource().body());
    }

    @Test
    void messageListHonoursLimitCeiling() {
        final var channelId = newChannelId("general");
        final var authorId = newAgentId(agent("bot"));
        for (int i = 0; i < 3; i++) {
            messages.create(message(channelId, authorId, "Message " + i));
        }

        assertEquals(3, messages.list(channelId, 5000, null, null).response().size());
        assertEquals(2, messages.list(channelId, 2, null, null).response().size());
    }

    @Test
    void messageListIsScopedToChannel() {
        final var general = newChannelId("general");
        final var random = newChannelId("random");
        final var authorId = newAgentId(agent("bot"));
        messages.create(message(general, authorId, "general message"));
        messages.create(message(random, authorId, "random message"));

        final var listed = messages.list(general, 50, null, null);
        assertEquals(1, listed.response().size());
        assertEquals("general message", listed.response().get(0).resource().body());
    }

    @Test
    void messageReplyInheritsThreadRoot() {
        final var channelId = newChannelId("general");
        final var authorId = newAgentId(agent("bot"));
        final var rootId = messages.create(message(channelId, authorId, "Root")).response().metadata().id();

        final var childId = messages.reply(new MessageDto(channelId, null, rootId, authorId, "Child", "plain", false, null, null))
                .response().metadata().id();
        final var grandChild = messages.reply(new MessageDto(channelId, childId, childId, authorId, "Grandchild", "plain", false, null, null))
                .response().resource();

        assertEquals(rootId, grandChild.threadId());
        assertEquals(childId, grandChild.parentMessageId());
    }

    @Test
    void messageDeleteSuppressesBodyButKeepsThread() {
        final var channelId = newChannelId("general");
        final var authorId = newAgentId(agent("bot"));
        final var rootId = messages.create(message(channelId, authorId, "Root")).response().metadata().id();
        final var replyId = messages.reply(new MessageDto(channelId, rootId, rootId, authorId, "Reply", "plain", false, null, null))
                .response().metadata().id();

        final var deleted = messages.delete(UUID.fromString(replyId), authorId, "2026-09-29T12:00:00Z");
        assertTrue(deleted.wasNoError());
        assertTrue(deleted.response().resource().deleted());
        assertEquals("", deleted.response().resource().body());
        assertEquals(rootId, deleted.response().resource().threadId());
        assertEquals("2026-09-29T12:00:00Z", deleted.response().resource().deletedAt());

        final var loaded = messages.get(UUID.fromString(replyId));
        assertTrue(loaded.wasNoError());
        assertTrue(loaded.response().resource().deleted());
    }

    @Test
    void authorizationSeparatesAdminFromAgent() {
        final var admin = new AuthPrincipal(UUID.randomUUID(), true);
        final var agent = new AuthPrincipal(UUID.randomUUID(), false);

        assertTrue(authorization.canManageAgents(admin));
        assertTrue(authorization.canManageCredentials(admin));
        assertTrue(authorization.canManageChannels(admin));
        assertTrue(authorization.canVerifyCapabilities(admin));

        assertFalse(authorization.canManageAgents(agent));
        assertFalse(authorization.canManageCredentials(agent));
        assertFalse(authorization.canManageChannels(agent));
        assertFalse(authorization.canVerifyCapabilities(agent));

        assertTrue(authorization.canReadPublicChannel(agent, "channel-1"));
        assertTrue(authorization.canPostToPublicChannel(agent, "channel-1"));
        assertTrue(authorization.canUpdateOwnStatus(agent, agent.agentId()));
        assertFalse(authorization.canUpdateOwnStatus(agent, admin.agentId()));
    }

    private String newAgentId(final AgentDto dto) {
        final var created = agentRegistry.create(dto);
        assertTrue(created.wasNoError());
        return created.response().metadata().id();
    }

    private String newChannelId(final String slug) {
        final var created = channels.create(channel(slug, slug, "public"));
        assertTrue(created.wasNoError(), () -> String.valueOf(error.get()));
        return created.response().metadata().id();
    }

    private AgentDto agent(final String name) {
        return agent(name, "active");
    }

    private AgentDto agent(final String name, final String lifecycleState) {
        return new AgentDto(name, "automated", null, null, null, null, "online", null,
                lifecycleState);
    }

    private AgentCredentialDto credential(final String label) {
        return new AgentCredentialDto("placeholder", "placeholder", label, null, null, null, "active");
    }

    private ChannelDto channel(final String slug, final String name, final String visibility) {
        return new ChannelDto(slug, name, visibility, "agent-1", false, null, null, null, null);
    }

    private MessageDto message(final String channelId, final String authorId, final String body) {
        return new MessageDto(channelId, null, null, authorId, body, "plain", false, null, null);
    }
}
