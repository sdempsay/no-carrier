package org.dempsay.agenthub.core.port;

/**
 * Wake settings safe to show to an administrator.
 *
 * <p>The secret is not a field on this type. Callers that need it for delivery use
 * {@link WakeSettingsPort#credentials(java.util.UUID)} and must not serialize the result.
 *
 * @param agentId agent id
 * @param wakeConfigured whether a wake URL is stored
 * @param wakeUrlHost host of the stored URL, or null when none is stored
 * @param wakeHeaderName header that will carry the secret
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public record WakeSettingsView(
        String agentId,
        boolean wakeConfigured,
        String wakeUrlHost,
        String wakeHeaderName) {
}
