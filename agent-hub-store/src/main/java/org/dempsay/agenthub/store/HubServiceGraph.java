package org.dempsay.agenthub.store;

import org.dempsay.agenthub.core.port.AgentRegistryPort;
import org.dempsay.agenthub.core.port.AuthorizationPort;
import org.dempsay.agenthub.core.port.BearerAuthPort;
import org.dempsay.agenthub.core.port.BootstrapCredentialPort;
import org.dempsay.agenthub.core.port.ChannelPort;
import org.dempsay.agenthub.core.port.CredentialServicePort;
import org.dempsay.agenthub.core.port.MessagePort;
import org.dempsay.agenthub.core.port.NotificationPort;
import org.dempsay.agenthub.core.port.WakeSettingsPort;

/**
 * The core service ports wired to a concrete store.
 *
 * <p>This is the composition root's result. Both the server and the command-line
 * adapter obtain their ports here, so the two cannot disagree about which store backs
 * a service or how credentials get hashed.
 *
 * @param agents agent registry
 * @param credentials credential lifecycle
 * @param channels channel operations
 * @param messages message operations
 * @param auth bearer authentication
 * @param authorization authorization decisions
 * @param notifications wake-up sink handed to the message service
 * @param bootstrap first-start administrator bootstrap
 * @param wakeSettings per-agent wake URLs and the board-wide debounce delay
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public record HubServiceGraph(
        AgentRegistryPort agents,
        CredentialServicePort credentials,
        ChannelPort channels,
        MessagePort messages,
        BearerAuthPort auth,
        AuthorizationPort authorization,
        NotificationPort notifications,
        BootstrapCredentialPort bootstrap,
        WakeSettingsPort wakeSettings) {
}