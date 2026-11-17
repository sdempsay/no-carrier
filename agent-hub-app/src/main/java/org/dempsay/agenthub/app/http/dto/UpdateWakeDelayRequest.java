package org.dempsay.agenthub.app.http.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Administrator request to store the board-wide wake delay.
 *
 * @param wakeDelaySeconds seconds to wait, 0 through 300; 0 sends on the next scheduler turn
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record UpdateWakeDelayRequest(@SerializedName("wakeDelaySeconds") Integer wakeDelaySeconds) {
}
