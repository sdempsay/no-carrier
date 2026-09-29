package org.dempsay.agenthub.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.dempsay.aether.api.access.AetherPrincipal;
import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.agenthub.api.model.AgentDto;
import org.dempsay.agenthub.api.model.AgentDtoBuilder;
import org.dempsay.agenthub.api.model.AgentDtoStore;
import org.dempsay.agenthub.store.fs.FsAgentDtoStore;
import org.dempsay.agenthub.store.memory.MemoryAgentDtoStore;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trip persistence for first-slice generated store adapters.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 */
class TestFirstSliceStores {
    private AtomicReference<Exception> error;

    @BeforeEach
    void setUp() {
        error = new AtomicReference<>();
    }

    @Test
    void memoryAgentRoundTrip() {
        final AgentDtoStore store = new MemoryAgentDtoStore();
        final AetherPersisted<AgentDto> created = createSysop(store);
        final ExceptionalResponse<AetherPersisted<AgentDto>> loaded =
                store.read(error::set, AetherPrincipal.system(), created.metadata().id());
        assertTrue(loaded.wasNoError(), () -> String.valueOf(error.get()));
        assertEquals("sysop", loaded.response().resource().name());
        assertEquals(created.metadata().id(), loaded.response().metadata().id());
    }

    @Test
    void filesystemAgentRoundTrip(@TempDir final Path root) {
        final AgentDtoStore store = new FsAgentDtoStore(root);
        final AetherPersisted<AgentDto> created = createSysop(store);
        final ExceptionalResponse<AetherPersisted<AgentDto>> loaded =
                store.read(error::set, AetherPrincipal.system(), created.metadata().id());
        assertTrue(loaded.wasNoError(), () -> String.valueOf(error.get()));
        assertEquals("sysop", loaded.response().resource().name());
    }

    @Test
    void filesystemAgentSurvivesNewStoreInstance(@TempDir final Path root) {
        final AetherPersisted<AgentDto> created = createSysop(new FsAgentDtoStore(root));
        final AgentDtoStore reopened = new FsAgentDtoStore(root);
        final ExceptionalResponse<AetherPersisted<AgentDto>> loaded =
                reopened.read(error::set, AetherPrincipal.system(), created.metadata().id());
        assertTrue(loaded.wasNoError(), () -> String.valueOf(error.get()));
        assertEquals("human", loaded.response().resource().kind());
    }

    private AetherPersisted<AgentDto> createSysop(final AgentDtoStore store) {
        final ExceptionalResponse<AgentDto> built = new AgentDtoBuilder()
                .name("sysop")
                .kind("human")
                .declaredStatus("online")
                .lifecycleState("active")
                .build(error::set);
        assertTrue(built.wasNoError(), () -> String.valueOf(error.get()));
        final ExceptionalResponse<AetherPersisted<AgentDto>> created =
                store.create(error::set, AetherPrincipal.system(), built.response());
        assertTrue(created.wasNoError(), () -> String.valueOf(error.get()));
        return created.response();
    }
}
