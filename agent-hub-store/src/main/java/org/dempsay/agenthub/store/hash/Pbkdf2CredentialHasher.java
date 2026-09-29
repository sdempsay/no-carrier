package org.dempsay.agenthub.store.hash;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.dempsay.agenthub.core.port.CredentialHashPort;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * JDK PBKDF2-HMAC-SHA-256 hasher. Encoded form is
 * {@code pbkdf2-sha256/<iterations>/<url-safe-salt>/<url-safe-dk>}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class Pbkdf2CredentialHasher implements CredentialHashPort {
    /**
     * Production iteration count (OWASP PBKDF2-HMAC-SHA256 recommendation).
     */
    public static final int DEFAULT_ITERATIONS = 600_000;

    private static final String ALGORITHM_ID = "pbkdf2-sha256";

    private static final String SECRET_KEY_FACTORY = "PBKDF2WithHmacSHA256";

    private static final int SALT_LENGTH_BYTES = 16;

    private static final int DERIVED_KEY_BITS = 256;

    private static final int ENCODED_PARTS = 4;

    private final int iterations;

    private final SecureRandom random;

    /**
     * Creates a hasher using {@link #DEFAULT_ITERATIONS}.
     */
    public Pbkdf2CredentialHasher() {
        this(DEFAULT_ITERATIONS);
    }

    /**
     * Creates a hasher with an injectable iteration count.
     *
     * @param iterations PBKDF2 rounds; must be at least 1
     */
    public Pbkdf2CredentialHasher(final int iterations) {
        this(iterations, new SecureRandom());
    }

    /**
     * Creates a hasher with injectable iterations and salt source.
     *
     * @param iterations PBKDF2 rounds; must be at least 1
     * @param random salt generator
     */
    public Pbkdf2CredentialHasher(final int iterations, final SecureRandom random) {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be at least 1");
        }
        this.iterations = iterations;
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Returns the iteration count used when hashing new keys.
     *
     * @return PBKDF2 rounds
     */
    public int iterations() {
        return iterations;
    }

    @Override
    public ExceptionalResponse<String> hash(final String plaintextKey) {
        if (isBlank(plaintextKey)) {
            return ExceptionalResponse.failure();
        }
        final byte[] salt = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(salt);
        return ExceptionalSupplier.of(() -> {
            final byte[] derived = pbkdf2(plaintextKey, salt, iterations);
            return ALGORITHM_ID + '/' + iterations + '/' + encode(salt) + '/' + encode(derived);
        }).execute();
    }

    @Override
    public ExceptionalResponse<Boolean> matches(final String plaintextKey, final String encodedHash) {
        if (isBlank(plaintextKey) || isBlank(encodedHash)) {
            return ExceptionalResponse.success(Boolean.FALSE);
        }
        final Optional<EncodedParts> parsed = parse(encodedHash);
        if (parsed.isEmpty()) {
            return ExceptionalResponse.success(Boolean.FALSE);
        }
        final EncodedParts parts = parsed.get();
        return ExceptionalSupplier.of(() -> {
            final byte[] actual = pbkdf2(plaintextKey, parts.salt(), parts.iterations());
            return Boolean.valueOf(MessageDigest.isEqual(parts.hash(), actual));
        }).execute();
    }

    private static byte[] pbkdf2(final String plaintextKey, final byte[] salt, final int roundCount)
            throws Exception {
        final char[] chars = plaintextKey.toCharArray();
        final PBEKeySpec spec = new PBEKeySpec(chars, salt, roundCount, DERIVED_KEY_BITS);
        try {
            final SecretKeyFactory factory = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY);
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }

    private static Optional<EncodedParts> parse(final String encodedHash) {
        final String[] parts = encodedHash.split("/", -1);
        if (parts.length != ENCODED_PARTS || !ALGORITHM_ID.equals(parts[0])) {
            return Optional.empty();
        }
        final int roundCount;
        try {
            roundCount = Integer.parseInt(parts[1]);
        } catch (final NumberFormatException ignored) {
            return Optional.empty();
        }
        if (roundCount < 1) {
            return Optional.empty();
        }
        try {
            final byte[] salt = decode(parts[2]);
            final byte[] hash = decode(parts[3]);
            if (salt.length == 0 || hash.length == 0) {
                return Optional.empty();
            }
            return Optional.of(new EncodedParts(roundCount, salt, hash));
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static String encode(final byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decode(final String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }

    private static boolean isBlank(final String value) {
        return Objects.isNull(value) || value.isBlank();
    }

    /**
     * Parsed encoding fields used during {@link #matches}.
     */
    private record EncodedParts(int iterations, byte[] salt, byte[] hash) {
    }
}
