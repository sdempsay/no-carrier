package org.dempsay.agenthub.store.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dempsay.agenthub.core.port.CredentialHashPort;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PBKDF2 hash and match behavior for API keys.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 */
class TestPbkdf2CredentialHasher {
    private static final String PLAINTEXT = "test-key-alpha";

    private static final int TEST_ITERATIONS = 1_000;

    private CredentialHashPort hasher;

    @BeforeEach
    void setUp() {
        hasher = new Pbkdf2CredentialHasher(TEST_ITERATIONS);
    }

    @Test
    void hashThenMatchesSameKey() {
        final String encoded = requireHash(PLAINTEXT);
        final ExceptionalResponse<Boolean> matched = hasher.matches(PLAINTEXT, encoded);
        assertTrue(matched.wasNoError());
        assertTrue(matched.response());
    }

    @Test
    void wrongKeyFailsClosed() {
        final String encoded = requireHash(PLAINTEXT);
        final ExceptionalResponse<Boolean> matched = hasher.matches("test-key-beta", encoded);
        assertTrue(matched.wasNoError());
        assertFalse(matched.response());
    }

    @Test
    void encodedFormOmitsPlaintext() {
        final String encoded = requireHash(PLAINTEXT);
        assertFalse(encoded.contains(PLAINTEXT));
        assertTrue(encoded.startsWith("pbkdf2-sha256/" + TEST_ITERATIONS + "/"));
        assertEquals(4, encoded.split("/", -1).length);
    }

    @Test
    void sameKeyProducesDistinctSalts() {
        final String first = requireHash(PLAINTEXT);
        final String second = requireHash(PLAINTEXT);
        assertNotEquals(first, second);
    }

    @Test
    void malformedEncodingFailsClosed() {
        final ExceptionalResponse<Boolean> matched = hasher.matches(PLAINTEXT, "not-a-hash");
        assertTrue(matched.wasNoError());
        assertFalse(matched.response());
    }

    @Test
    void blankKeyDoesNotHash() {
        assertTrue(hasher.hash("").wasError());
        assertTrue(hasher.hash(null).wasError());
    }

    @Test
    void injectedIterationsAreUsedWhenHashing() {
        final Pbkdf2CredentialHasher custom = new Pbkdf2CredentialHasher(2_000);
        final String encoded = requireHash(custom, PLAINTEXT);
        assertTrue(encoded.startsWith("pbkdf2-sha256/2000/"));
        assertTrue(custom.matches(PLAINTEXT, encoded).response());
    }

    private String requireHash(final String plaintext) {
        return requireHash(hasher, plaintext);
    }

    private static String requireHash(final CredentialHashPort port, final String plaintext) {
        final ExceptionalResponse<String> hashed = port.hash(plaintext);
        assertTrue(hashed.wasNoError());
        return hashed.response();
    }
}
