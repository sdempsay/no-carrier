package org.dempsay.agenthub.app.http.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Request to update a channel.
 *
 * <p>Only the display name and the advisory agent instructions can be changed. The
 * slug is the channel's identity and the visibility change is deliberately absent:
 * both deserve their own decision rather than riding along on a typo fix.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public record UpdateChannelRequest(
        @SerializedName("name") String name,
        @SerializedName("agentInstructions") String agentInstructions,
        @SerializedName("clearAgentInstructions") Boolean clearAgentInstructions) {
}