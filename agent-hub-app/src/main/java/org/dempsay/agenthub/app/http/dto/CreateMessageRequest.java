package org.dempsay.agenthub.app.http.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Request to create a message or reply.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record CreateMessageRequest(
        @SerializedName("body") String body,
        @SerializedName("format") String format,
        @SerializedName("parentMessageId") String parentMessageId) {
}