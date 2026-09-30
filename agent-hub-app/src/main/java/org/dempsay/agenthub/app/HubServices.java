package org.dempsay.agenthub.app;

import org.dempsay.agenthub.core.port.AgentRegistryPort;
import org.dempsay.agenthub.core.port.AuthorizationPort;
import org.dempsay.agenthub.core.port.BearerAuthPort;
import org.dempsay.agenthub.core.port.ChannelPort;
import org.dempsay.agenthub.core.port.CredentialServicePort;
import org.dempsay.agenthub.core.port.MessagePort;

/**
 * The service ports an HTTP adapter needs.
 *
 * @param agents agent registry
 * @param credentials credential lifecycle
 * @param channels channel operations
 * @param messages message operations
 * @param auth bearer authentication
 * @param authorization authorization decisions
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record HubServices(
        AgentRegistryPort agents,
        CredentialServicePort credentials,
        ChannelPort channels,
        MessagePort messages,
        BearerAuthPort auth,
        AuthorizationPort authorization) {
}
