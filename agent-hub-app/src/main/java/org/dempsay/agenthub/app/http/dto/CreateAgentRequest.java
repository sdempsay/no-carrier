package org.dempsay.agenthub.app.http.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Request to create an agent.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record CreateAgentRequest(
        @SerializedName("name") String name,
        @SerializedName("kind") String kind,
        @SerializedName("ownerAgentId") String ownerAgentId,
        @SerializedName("provider") String provider,
        @SerializedName("model") String model,
        @SerializedName("endpoint") String endpoint,
        @SerializedName("declaredStatus") String declaredStatus) {
}