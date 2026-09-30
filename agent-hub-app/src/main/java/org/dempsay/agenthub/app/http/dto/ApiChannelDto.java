package org.dempsay.agenthub.app.http.dto;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

/**
 * HTTP API representation of a channel.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record ApiChannelDto(
        @SerializedName("id") UUID id,
        @SerializedName("slug") String slug,
        @SerializedName("name") String name,
        @SerializedName("visibility") String visibility,
        @SerializedName("createdByAgentId") UUID createdByAgentId,
        @SerializedName("createdAt") String createdAt,
        @SerializedName("updatedAt") String updatedAt,
        @SerializedName("deleted") boolean deleted,
        @SerializedName("deletedAt") String deletedAt,
        @SerializedName("deletedByAgentId") UUID deletedByAgentId) {
}