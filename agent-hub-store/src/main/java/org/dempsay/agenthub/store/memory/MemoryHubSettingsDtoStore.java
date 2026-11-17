package org.dempsay.agenthub.store.memory;

import org.dempsay.aether.store.memory.InMemoryAetherSingletonStore;
import org.dempsay.agenthub.api.model.HubSettingsDto;
import org.dempsay.agenthub.api.model.HubSettingsDtoStore;

/**
 * In-memory singleton store for board settings.
 *
 * <p>The store generator emits filesystem singleton adapters and multi-resource
 * memory adapters. It does not emit an in-memory singleton, so this class is written
 * by hand for tests and {@code --in-memory}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public final class MemoryHubSettingsDtoStore extends InMemoryAetherSingletonStore<HubSettingsDto>
        implements HubSettingsDtoStore {

    /**
     * Creates an empty settings store.
     */
    public MemoryHubSettingsDtoStore() {
        super();
    }
}
