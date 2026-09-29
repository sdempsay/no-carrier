package org.dempsay.agenthub.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Builder validation for the first-slice Aether records.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 */
class TestFirstSliceDtos {
    private AtomicReference<Exception> error;

    @BeforeEach
    void setUp() {
        error = new AtomicReference<>();
    }

    @Test
    void agentBuilderAcceptsHumanSysop() {
        final ExceptionalResponse<AgentDto> response = new AgentDtoBuilder()
                .name("sysop")
                .kind("human")
                .declaredStatus("online")
                .lifecycleState("active")
                .build(error::set);
        assertTrue(response.wasNoError());
        assertEquals("human", response.response().kind());
    }

    @Test
    void agentBuilderRejectsUnknownKind() {
        final ExceptionalResponse<AgentDto> response = new AgentDtoBuilder()
                .name("bot")
                .kind("cyborg")
                .declaredStatus("online")
                .lifecycleState("active")
                .build(error::set);
        assertTrue(response.wasError());
    }

    @Test
    void credentialBuilderStoresHashOnlyShape() {
        final ExceptionalResponse<AgentCredentialDto> response = new AgentCredentialDtoBuilder()
                .agentId("agent-1")
                .keyHash("pbkdf2$dummy$salt$hash")
                .label("initial")
                .state("active")
                .build(error::set);
        assertTrue(response.wasNoError());
        assertEquals("active", response.response().state());
    }

    @Test
    void channelBuilderAcceptsPublicSlug() {
        final ExceptionalResponse<ChannelDto> response = new ChannelDtoBuilder()
                .slug("general")
                .name("General")
                .visibility("public")
                .createdByAgentId("agent-1")
                .deleted(false)
                .build(error::set);
        assertTrue(response.wasNoError());
        assertFalse(response.response().deleted());
    }

    @Test
    void channelBuilderRejectsUnsafeSlug() {
        final ExceptionalResponse<ChannelDto> response = new ChannelDtoBuilder()
                .slug("Not A Slug")
                .name("General")
                .visibility("public")
                .createdByAgentId("agent-1")
                .deleted(false)
                .build(error::set);
        assertTrue(response.wasError());
    }

    @Test
    void messageBuilderAcceptsPlainPost() {
        final ExceptionalResponse<MessageDto> response = new MessageDtoBuilder()
                .channelId("channel-1")
                .authorAgentId("agent-1")
                .body("hello board")
                .format("plain")
                .deleted(false)
                .build(error::set);
        assertTrue(response.wasNoError());
        assertEquals("hello board", response.response().body());
    }

    @Test
    void messageBuilderRejectsOversizedBody() {
        final String oversized = "x".repeat(16385);
        final ExceptionalResponse<MessageDto> response = new MessageDtoBuilder()
                .channelId("channel-1")
                .authorAgentId("agent-1")
                .body(oversized)
                .format("plain")
                .deleted(false)
                .build(error::set);
        assertTrue(response.wasError());
    }

    @Test
    void messageBuilderAllowsEmptyTombstoneBody() {
        final ExceptionalResponse<MessageDto> response = new MessageDtoBuilder()
                .channelId("channel-1")
                .authorAgentId("agent-1")
                .body("")
                .format("plain")
                .deleted(true)
                .deletedAt("2026-09-29T00:00:00Z")
                .deletedByAgentId("agent-1")
                .build(error::set);
        assertTrue(response.wasNoError());
        assertTrue(response.response().deleted());
    }
}
