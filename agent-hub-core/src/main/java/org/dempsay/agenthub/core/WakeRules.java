package org.dempsay.agenthub.core;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Validation and display helpers for wake settings.
 *
 * <p>The wake URL is called by the board. {@code AgentDto.endpoint} is a different
 * field and is never called.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public final class WakeRules {
    /** Header used when an agent does not set one. */
    public static final String DEFAULT_HEADER = "X-Agent-Hub-Wake-Key";

    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_KEY_LENGTH = 256;
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]{0,63}");

    private WakeRules() {
    }

    /**
     * Returns a problem with {@code wakeUrl}, or null when it is an http(s) URL with a host.
     *
     * @param wakeUrl candidate URL
     * @return problem text, or null when acceptable
     */
    public static String urlProblem(final String wakeUrl) {
        if (wakeUrl == null || wakeUrl.isBlank()) {
            return "wakeUrl is required";
        }
        if (wakeUrl.trim().length() > MAX_URL_LENGTH) {
            return "wakeUrl is too long";
        }
        final URI uri;
        try {
            uri = URI.create(wakeUrl.trim());
        } catch (final IllegalArgumentException e) {
            return "wakeUrl must be an http or https URL";
        }
        final var scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return "wakeUrl must be an http or https URL";
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return "wakeUrl must include a host";
        }
        if (uri.getUserInfo() != null) {
            return "wakeUrl must not include user info";
        }
        return null;
    }

    /**
     * Returns a problem with a wake secret, or null when it can be sent as a header value.
     *
     * <p>Null and blank are acceptable: null means "leave the stored secret alone" and blank
     * means "clear it".
     *
     * @param wakeKey candidate secret
     * @return problem text, or null when acceptable
     */
    public static String keyProblem(final String wakeKey) {
        if (wakeKey == null || wakeKey.isEmpty()) {
            return null;
        }
        if (wakeKey.length() > MAX_KEY_LENGTH) {
            return "wakeKey is too long";
        }
        for (int i = 0; i < wakeKey.length(); i++) {
            final char c = wakeKey.charAt(i);
            if (c < 0x21 || c > 0x7E) {
                return "wakeKey contains a character that cannot be sent in a header";
            }
        }
        return null;
    }

    /**
     * Returns a problem with a header name, or null when it is a single token or blank.
     *
     * <p>Blank means "use the default header name".
     *
     * @param wakeHeaderName candidate header name
     * @return problem text, or null when acceptable
     */
    public static String headerProblem(final String wakeHeaderName) {
        if (wakeHeaderName == null || wakeHeaderName.isBlank()) {
            return null;
        }
        if (!HEADER_NAME.matcher(wakeHeaderName.trim()).matches()) {
            return "wakeHeaderName must be a single HTTP header token";
        }
        return null;
    }

    /**
     * Returns the host of a wake URL, without user info, path, or query.
     *
     * @param wakeUrl stored URL
     * @return host, or null when the URL has none
     */
    public static String host(final String wakeUrl) {
        if (wakeUrl == null || wakeUrl.isBlank()) {
            return null;
        }
        try {
            final var host = URI.create(wakeUrl.trim()).getHost();
            return host == null || host.isBlank() ? null : host;
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns the header that will carry the secret.
     *
     * @param configured stored header name, or null
     * @return the stored name, or {@link #DEFAULT_HEADER}
     */
    public static String effectiveHeader(final String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_HEADER;
        }
        return configured.trim();
    }

    /**
     * Reports whether a delay is inside the accepted range.
     *
     * @param seconds candidate delay
     * @return true for 0 through {@link HubDefaults#MAX_WAKE_DELAY_SECONDS}
     */
    public static boolean delayAllowed(final int seconds) {
        return seconds >= 0 && seconds <= HubDefaults.MAX_WAKE_DELAY_SECONDS;
    }
}
