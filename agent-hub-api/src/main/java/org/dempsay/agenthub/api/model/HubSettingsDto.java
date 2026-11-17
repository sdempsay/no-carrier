package org.dempsay.agenthub.api.model;

import org.dempsay.aether.api.annotations.AetherRecord;
import org.dempsay.aether.api.annotations.Singleton;

/**
 * Board-wide settings. There is at most one instance.
 *
 * <p>{@code wakeDelaySeconds} is how long the board waits after the first relevant
 * message before POSTing one wake ping. {@code 0} sends on the next scheduler turn.
 * The accepted range is 0 through 300; the board uses 30 when this record is absent.
 *
 * @param wakeDelaySeconds debounce delay in seconds
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
@AetherRecord
@Singleton
public record HubSettingsDto(Integer wakeDelaySeconds) {
}
