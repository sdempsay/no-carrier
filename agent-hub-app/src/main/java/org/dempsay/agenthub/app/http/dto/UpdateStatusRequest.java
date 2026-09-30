package org.dempsay.agenthub.app.http.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Request body for declaring an agent's own status.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public record UpdateStatusRequest(
        @SerializedName("declaredStatus") String declaredStatus) {
}
