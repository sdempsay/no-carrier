package org.dempsay.agenthub.core.port;

import java.util.Optional;
import java.util.UUID;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Administrator settings for wake pings.
 *
 * <p>The secret never appears on {@link WakeSettingsView}. {@link #credentials(UUID)}
 * exists so the sender can present it, and its result must not be written to a
 * response or a log.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public interface WakeSettingsPort {
    /**
     * Reads the safe view of one agent's wake settings.
     *
     * @param agentId agent id
     * @return the view, or failure when the agent does not exist
     */
    ExceptionalResponse<WakeSettingsView> view(UUID agentId);

    /**
     * Stores a wake URL and, optionally, a secret and header name.
     *
     * <p>A null secret or header leaves the stored value in place. A blank secret
     * clears the secret. A blank header name returns the agent to the default header.
     *
     * @param agentId agent id
     * @param wakeUrl http or https URL
     * @param wakeKey secret, null to keep, blank to clear
     * @param wakeHeaderName header name, null to keep, blank for the default
     * @return the safe view, or failure
     */
    ExceptionalResponse<WakeSettingsView> replace(UUID agentId, String wakeUrl, String wakeKey, String wakeHeaderName);

    /**
     * Removes the URL, secret, and header name.
     *
     * @param agentId agent id
     * @return the safe view, or failure when the agent does not exist
     */
    ExceptionalResponse<WakeSettingsView> clear(UUID agentId);

    /**
     * Returns the stored debounce delay, or the default of 30 when none is stored.
     *
     * @return delay in seconds, 0 through 300
     */
    int delaySeconds();

    /**
     * Stores the debounce delay.
     *
     * @param seconds 0 through 300; 0 sends on the next scheduler turn
     * @return the stored delay, or failure when {@code seconds} is outside 0..300
     */
    ExceptionalResponse<Integer> setDelaySeconds(int seconds);

    /**
     * Returns the URL and secret the sender should present.
     *
     * <p>Empty when the agent is missing or has no wake URL. Do not log or serialize
     * the result; {@link WakeCredentials#toString()} is redacted, and the accessor
     * values are not.
     *
     * @param agentId agent id
     * @return delivery material, or empty
     */
    Optional<WakeCredentials> credentials(UUID agentId);
}
