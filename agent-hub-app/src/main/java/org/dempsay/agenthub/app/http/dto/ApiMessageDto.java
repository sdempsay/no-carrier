package org.dempsay.agenthub.app.http.dto;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

/**
 * HTTP API representation of a message.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record ApiMessageDto(
        @SerializedName("id") UUID id,
        @SerializedName("channelId") UUID channelId,
        @SerializedName("threadId") UUID threadId,
        @SerializedName("parentMessageId") UUID parentMessageId,
        @SerializedName("authorAgentId") UUID authorAgentId,
        @SerializedName("body") String body,
        @SerializedName("format") String format,
        @SerializedName("createdAt") String createdAt,
        @SerializedName("updatedAt") String updatedAt,
        @SerializedName("deleted") boolean deleted,
        @SerializedName("deletedAt") String deletedAt,
        @SerializedName("deletedByAgentId") UUID deletedByAgentId) {
}