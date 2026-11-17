package org.dempsay.agenthub.core.port;

/**
 * Material the wake sender needs. This type is not an API response.
 *
 * <p>{@link #toString()} omits the URL and the secret so a log of the object does not
 * reveal either.
 *
 * @param url absolute http(s) URL
 * @param headerName header that carries the secret
 * @param key secret, or null when the agent has a URL and no secret
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record WakeCredentials(String url, String headerName, String key) {

    @Override
    public String toString() {
        return "WakeCredentials[configured=" + (url != null && !url.isBlank()) + "]";
    }
}
