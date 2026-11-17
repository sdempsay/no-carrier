package org.dempsay.agenthub.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import org.dempsay.agenthub.api.model.AgentDto;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;
import org.dempsay.agenthub.core.HubDefaults;
import org.dempsay.agenthub.core.WakeRules;
import org.dempsay.agenthub.core.service.AgentRegistryService;
import org.dempsay.agenthub.core.service.WakeRelevance;
import org.dempsay.agenthub.core.service.WakeRelevance.Candidate;
import org.dempsay.agenthub.core.service.WakeSettingsService;
import org.dempsay.agenthub.store.fs.FsAgentDtoStore;
import org.dempsay.agenthub.store.memory.MemoryAgentDtoStore;
import org.dempsay.agenthub.store.memory.MemoryHubSettingsDtoStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Wake relevance and the settings that must survive without exposing the secret.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 */
class WakeSettingsTest {
    private static final String SECRET = "wk-secret-not-in-view";

    @Test
    void selectsHomeChannelRepliesAndThreadParticipantsButNotTheAuthorOrATombstone() {
        final var peyton = "11111111-1111-1111-1111-111111111111";
        final var nanobot = "22222222-2222-2222-2222-222222222222";
        final var quiet = "33333333-3333-3333-3333-333333333333";
        final var agents = List.of(
                new Candidate(peyton, "Peyton", true),
                new Candidate(nanobot, "nanobot", true),
                new Candidate(quiet, "quiet", false));

        assertEquals(List.of(), WakeRelevance.select(true, nanobot, "peyton", false, Set.of(), agents),
                "a tombstone selects nobody");
        assertEquals(List.of(), WakeRelevance.select(false, peyton, "peyton", false, Set.of(), agents),
                "the author is not pinged about their own message");
        assertEquals(List.of(peyton), WakeRelevance.select(false, nanobot, "peyton", false, Set.of(), agents),
                "a top-level post in the home channel pings that agent");
        assertEquals(List.of(peyton), WakeRelevance.select(false, nanobot, "PEYTON", true, Set.of(), agents),
                "a reply in the home channel matches the slug without regard to case");
        assertEquals(List.of(), WakeRelevance.select(false, nanobot, "general", false, Set.of(peyton), agents),
                "a top-level post outside the home channel does not ping, even if the agent posted before");
        assertEquals(List.of(peyton), WakeRelevance.select(false, nanobot, "general", true, Set.of(peyton), agents),
                "a reply in a thread the agent started or posted in does ping");
        assertEquals(List.of(), WakeRelevance.select(false, nanobot, "general", true, Set.of(nanobot), agents),
                "a reply in a thread the agent has not touched does not ping");
        assertFalse(WakeRelevance.select(false, nanobot, "quiet", false, Set.of(), agents).contains(quiet),
                "an agent with no wake URL is skipped");
    }

    @Test
    void participantsIncludeTheRootAuthorAndLaterReplies() {
        final var root = "root-id";
        final var authors = WakeRelevance.participants(root,
                List.of(root, "reply-id", "other-id"),
                Arrays.asList(null, root, "somewhere-else"),
                List.of("peyton", "nanobot", "quinn"));
        assertEquals(Set.of("peyton", "nanobot"), authors);
    }

    @Test
    void viewAndStatusUpdatesDoNotExposeOrDropTheSecret() {
        final var agents = new AgentRegistryService(new MemoryAgentDtoStore());
        final var settings = new WakeSettingsService(agents, new MemoryHubSettingsDtoStore());
        final var id = UUID.fromString(agents.create(agent("peyton")).response().metadata().id());

        assertEquals(HubDefaults.DEFAULT_WAKE_DELAY_SECONDS, settings.delaySeconds());
        assertTrue(settings.setDelaySeconds(-1).wasError());
        assertTrue(settings.setDelaySeconds(301).wasError());
        assertEquals(HubDefaults.DEFAULT_WAKE_DELAY_SECONDS, settings.delaySeconds());
        assertTrue(settings.setDelaySeconds(0).wasNoError());
        assertEquals(0, settings.delaySeconds());

        assertTrue(settings.replace(id, "ftp://example/wake", SECRET, null).wasError());
        final var stored = settings.replace(id, "http://127.0.0.1:9/wake", SECRET, null);
        assertTrue(stored.wasNoError());
        assertTrue(stored.response().wakeConfigured());
        assertEquals("127.0.0.1", stored.response().wakeUrlHost());
        assertEquals(WakeRules.DEFAULT_HEADER, stored.response().wakeHeaderName());
        assertFalse(stored.response().toString().contains(SECRET));
        assertFalse(stored.response().toString().contains("wakeKey"));

        final var credentials = settings.credentials(id).orElseThrow();
        assertEquals(SECRET, credentials.key());
        assertFalse(credentials.toString().contains(SECRET));
        assertFalse(credentials.toString().contains("127.0.0.1"));

        assertTrue(agents.updateStatus(id, "busy", "2026-11-16T00:00:00Z").wasNoError());
        assertEquals(SECRET, settings.credentials(id).orElseThrow().key());
        assertEquals("busy", agents.get(id).response().resource().declaredStatus());

        final var kept = settings.replace(id, "https://example.test/wake", null, "X-Test-Wake");
        assertTrue(kept.wasNoError());
        assertEquals(SECRET, settings.credentials(id).orElseThrow().key(),
                "omitting the secret on a later update keeps it");
        assertEquals("example.test", kept.response().wakeUrlHost());
        assertFalse(kept.response().toString().contains(SECRET));

        assertTrue(settings.clear(id).wasNoError());
        assertFalse(settings.view(id).response().wakeConfigured());
        assertTrue(settings.credentials(id).isEmpty());
    }

    @Test
    void agentDocumentWrittenBeforeWakeFieldsStillLoads(@TempDir final Path root) {
        final var created = new AgentRegistryService(new FsAgentDtoStore(root)).create(agent("peyton"));
        assertTrue(created.wasNoError());
        final var id = created.response().metadata().id();
        final var file = root.resolve("AgentDto").resolve(id + ".json");
        final var raw = ExceptionalSupplier.of(() -> Files.readString(file)).execute();
        assertTrue(raw.wasNoError());
        final var document = JsonParser.parseString(raw.response()).getAsJsonObject();
        final var resource = document.getAsJsonObject("resource");
        resource.remove("wakeUrl");
        resource.remove("wakeKey");
        resource.remove("wakeHeaderName");
        final var written = ExceptionalSupplier.of(() -> {
            Files.writeString(file, new Gson().toJson(document));
            return Boolean.TRUE;
        }).execute();
        assertTrue(written.wasNoError());

        final var agents = new AgentRegistryService(new FsAgentDtoStore(root));
        final var loaded = agents.get(UUID.fromString(id));
        assertTrue(loaded.wasNoError(), "an older agent document must still load");
        assertEquals("peyton", loaded.response().resource().name());
        assertNull(loaded.response().resource().wakeUrl());
        assertNull(loaded.response().resource().wakeKey());
        assertFalse(loaded.response().resource().wakeConfigured());

        assertTrue(agents.updateStatus(UUID.fromString(id), "away", null).wasNoError());
        assertEquals("away", agents.get(UUID.fromString(id)).response().resource().declaredStatus());
        assertNull(agents.get(UUID.fromString(id)).response().resource().wakeKey());
    }

    private static AgentDto agent(final String name) {
        return new AgentDto(name, "automated", null, null, null, null, "online", null, "active", null, null, null);
    }
}
