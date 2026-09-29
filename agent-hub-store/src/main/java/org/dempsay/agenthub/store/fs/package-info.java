/**
 * Filesystem Aether adapters for first-slice records. Layout under the supplied
 * root is owned by Aether ({@code {root}/{type}/{id}.json}).
 */
@AetherStoreProviders(
        filesystem = {
            AgentDto.class,
            AgentCredentialDto.class,
            ChannelDto.class,
            MessageDto.class
        })
package org.dempsay.agenthub.store.fs;

import org.dempsay.aether.store.gen.AetherStoreProviders;
import org.dempsay.agenthub.api.model.AgentCredentialDto;
import org.dempsay.agenthub.api.model.AgentDto;
import org.dempsay.agenthub.api.model.ChannelDto;
import org.dempsay.agenthub.api.model.MessageDto;
