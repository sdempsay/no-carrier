package org.dempsay.agenthub.core.service;

import java.util.Optional;
import java.util.UUID;

import org.dempsay.aether.api.access.AetherPrincipal;
import org.dempsay.aether.api.store.AetherPersisted;
import org.dempsay.aether.api.store.UpdateOptions;
import org.dempsay.agenthub.api.model.AgentDto;
import org.dempsay.agenthub.api.model.HubSettingsDto;
import org.dempsay.agenthub.api.model.HubSettingsDtoStore;
import org.dempsay.agenthub.core.HubDefaults;
import org.dempsay.agenthub.core.WakeRules;
import org.dempsay.agenthub.core.port.AgentRegistryPort;
import org.dempsay.agenthub.core.port.WakeCredentials;
import org.dempsay.agenthub.core.port.WakeSettingsPort;
import org.dempsay.agenthub.core.port.WakeSettingsView;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Stores per-agent wake settings on the agent record and the debounce delay on the
 * settings singleton.
 *
 * <p>There is no migration framework in this project. The wake fields on
 * {@link AgentDto} are nullable, and the filesystem store leaves them null when an
 * older document does not contain them.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public final class WakeSettingsService implements WakeSettingsPort {
    private final AgentRegistryPort agents;
    private final HubSettingsDtoStore settings;

    public WakeSettingsService(final AgentRegistryPort agents, final HubSettingsDtoStore settings) {
        this.agents = agents;
        this.settings = settings;
    }

    @Override
    public ExceptionalResponse<WakeSettingsView> view(final UUID agentId) {
        final var found = agents.get(agentId);
        if (found.wasError()) {
            return ExceptionalResponse.failure();
        }
        return ExceptionalResponse.success(toView(found.response()));
    }

    @Override
    public ExceptionalResponse<WakeSettingsView> replace(final UUID agentId, final String wakeUrl, final String wakeKey,
            final String wakeHeaderName) {
        if (WakeRules.urlProblem(wakeUrl) != null
                || WakeRules.keyProblem(wakeKey) != null
                || WakeRules.headerProblem(wakeHeaderName) != null) {
            return ExceptionalResponse.failure();
        }
        final var found = agents.get(agentId);
        if (found.wasError()) {
            return ExceptionalResponse.failure();
        }
        final var agent = found.response().resource();
        final var newKey = wakeKey == null ? agent.wakeKey() : blankToNull(wakeKey);
        final var newHeader = wakeHeaderName == null ? agent.wakeHeaderName() : blankToNull(wakeHeaderName);
        final var updated = agents.updateWake(agentId, wakeUrl.trim(), newKey, newHeader == null ? null : newHeader.trim());
        if (updated.wasError()) {
            return ExceptionalResponse.failure();
        }
        return ExceptionalResponse.success(toView(updated.response()));
    }

    @Override
    public ExceptionalResponse<WakeSettingsView> clear(final UUID agentId) {
        final var updated = agents.updateWake(agentId, null, null, null);
        if (updated.wasError()) {
            return ExceptionalResponse.failure();
        }
        return ExceptionalResponse.success(toView(updated.response()));
    }

    @Override
    public int delaySeconds() {
        final var read = settings.read(e -> { }, AetherPrincipal.system());
        if (read.wasError() || read.response().resource().wakeDelaySeconds() == null) {
            return HubDefaults.DEFAULT_WAKE_DELAY_SECONDS;
        }
        final int value = read.response().resource().wakeDelaySeconds();
        if (!WakeRules.delayAllowed(value)) {
            return HubDefaults.DEFAULT_WAKE_DELAY_SECONDS;
        }
        return value;
    }

    @Override
    public ExceptionalResponse<Integer> setDelaySeconds(final int seconds) {
        if (!WakeRules.delayAllowed(seconds)) {
            return ExceptionalResponse.failure();
        }
        final var body = new HubSettingsDto(seconds);
        final var read = settings.read(e -> { }, AetherPrincipal.system());
        if (read.wasError()) {
            final var created = settings.create(e -> { }, AetherPrincipal.system(), body);
            if (created.wasNoError()) {
                return ExceptionalResponse.success(seconds);
            }
            final var again = settings.read(e -> { }, AetherPrincipal.system());
            if (again.wasError()) {
                return ExceptionalResponse.failure();
            }
            return updateDelay(body, again.response().metadata().version(), seconds);
        }
        return updateDelay(body, read.response().metadata().version(), seconds);
    }

    @Override
    public Optional<WakeCredentials> credentials(final UUID agentId) {
        final var found = agents.get(agentId);
        if (found.wasError()) {
            return Optional.empty();
        }
        final var agent = found.response().resource();
        if (!agent.wakeConfigured()) {
            return Optional.empty();
        }
        return Optional.of(new WakeCredentials(
                agent.wakeUrl().trim(),
                WakeRules.effectiveHeader(agent.wakeHeaderName()),
                blankToNull(agent.wakeKey())));
    }

    private ExceptionalResponse<Integer> updateDelay(final HubSettingsDto body, final String version, final int seconds) {
        final var updated = settings.update(e -> { }, AetherPrincipal.system(), body, version, UpdateOptions.defaults());
        if (updated.wasError()) {
            return ExceptionalResponse.failure();
        }
        return ExceptionalResponse.success(seconds);
    }

    private static WakeSettingsView toView(final AetherPersisted<AgentDto> persisted) {
        final var agent = persisted.resource();
        final var configured = agent.wakeConfigured();
        return new WakeSettingsView(
                persisted.metadata().id(),
                configured,
                configured ? WakeRules.host(agent.wakeUrl()) : null,
                WakeRules.effectiveHeader(agent.wakeHeaderName()));
    }

    private static String blankToNull(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
