package org.dempsay.agenthub.app.http;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Structured error response for HTTP API.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record ErrorResponse(
        @SerializedName("code") String code,
        @SerializedName("message") String message,
        @SerializedName("requestId") String requestId,
        @SerializedName("violations") List<Violation> violations) {

    public static ErrorResponse of(final String code, final String message, final String requestId) {
        return new ErrorResponse(code, message, requestId, List.of());
    }

    public static ErrorResponse of(final String code, final String message, final String requestId, final List<Violation> violations) {
        return new ErrorResponse(code, message, requestId, violations);
    }

    public record Violation(
            @SerializedName("field") String field,
            @SerializedName("message") String message) {
    }
}