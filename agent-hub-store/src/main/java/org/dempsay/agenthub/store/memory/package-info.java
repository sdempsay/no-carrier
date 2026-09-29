/**
 * In-memory Aether adapters for first-slice records. Used by tests; no data
 * directory is required.
 */
@AetherStoreProviders(
        memory = {
            AgentDto.class,
            AgentCredentialDto.class,
            ChannelDto.class,
            MessageDto.class
        })
package org.dempsay.agenthub.store.memory;

import org.dempsay.aether.store.gen.AetherStoreProviders;
import org.dempsay.agenthub.api.model.AgentCredentialDto;
import org.dempsay.agenthub.api.model.AgentDto;
import org.dempsay.agenthub.api.model.ChannelDto;
import org.dempsay.agenthub.api.model.MessageDto;
