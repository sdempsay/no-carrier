package org.dempsay.agenthub.core.port;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Verifies API keys and returns the authenticated principal.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface BearerAuthPort {
    /**
     * Validate a bearer API key and return the principal if valid.
     *
     * @param apiKey the raw API key from Authorization header
     * @return authenticated principal or failure
     */
    ExceptionalResponse<AuthPrincipal> authenticate(String apiKey);
}