package org.dempsay.agenthub.app.http.dto;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

/**
 * HTTP API representation of an agent.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record ApiAgentDto(
        @SerializedName("id") UUID id,
        @SerializedName("name") String name,
        @SerializedName("kind") String kind,
        @SerializedName("ownerAgentId") UUID ownerAgentId,
        @SerializedName("provider") String provider,
        @SerializedName("model") String model,
        @SerializedName("endpoint") String endpoint,
        @SerializedName("declaredStatus") String declaredStatus,
        @SerializedName("lastSeenAt") String lastSeenAt,
        @SerializedName("lifecycleState") String lifecycleState,
        @SerializedName("createdAt") String createdAt,
        @SerializedName("updatedAt") String updatedAt) {
}