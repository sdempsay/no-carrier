package org.dempsay.agenthub.core.port;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;

/**
 * Hashes API keys and compares them to stored encodings. Implementations must
 * never log plaintext keys.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public interface CredentialHashPort {
    /**
     * Encodes {@code plaintextKey} for persistence. The result contains no
     * plaintext.
     *
     * @param plaintextKey the API key to hash
     * @return encoded algorithm/parameters/salt/hash, or failure
     */
    ExceptionalResponse<String> hash(String plaintextKey);

    /**
     * Constant-time comparison of {@code plaintextKey} against {@code encodedHash}.
     * Unknown, malformed, or mismatched encodings fail closed (not a match).
     *
     * @param plaintextKey candidate API key
     * @param encodedHash previously persisted encoding from {@link #hash}
     * @return {@code true} on match; {@code false} on mismatch or unusable input
     */
    ExceptionalResponse<Boolean> matches(String plaintextKey, String encodedHash);
}
