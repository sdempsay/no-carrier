package org.dempsay.agenthub.app.http.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Request to create a channel.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record CreateChannelRequest(
        @SerializedName("slug") String slug,
        @SerializedName("name") String name,
        @SerializedName("visibility") String visibility) {
}