package org.dempsay.agenthub.app.http.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Administrator request to store an agent's wake settings.
 *
 * <p>{@code wakeKey} is accepted and not echoed. Null leaves the stored secret
 * in place. A blank string clears it.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record UpdateWakeRequest(
        @SerializedName("wakeUrl") String wakeUrl,
        @SerializedName("wakeKey") String wakeKey,
        @SerializedName("wakeHeaderName") String wakeHeaderName) {
}
