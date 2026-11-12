package org.dempsay.agenthub.cli;

import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import org.dempsay.agenthub.core.HubDefaults;
import org.dempsay.agenthub.core.service.BootstrapCredentialService;
import org.dempsay.agenthub.core.service.NoOpNotificationPort;
import org.dempsay.agenthub.store.HubServiceFactory;

/**
 * Creates the first administrator credential directly in the store.
 *
 * <p>This runs offline, against the same data directory the server will read. It has
 * to: an administrator that already exists is the only thing that can mint the first
 * ordinary agent, so this step cannot be expressed as an authenticated call to a
 * board that has no administrator yet. Stop the server first, so two processes never
 * write the store at once.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public final class AdminInitCommand {
    /** Shortest accepted API key. Anything shorter is trivially guessable. */
    static final int MIN_KEY_LENGTH = 16;

    private final PrintStream out;
    private final PrintStream err;
    private final CliConsole console;
    private final SecureRandom random = new SecureRandom();

    AdminInitCommand(final PrintStream out, final PrintStream err, final CliConsole console) {
        this.out = out;
        this.err = err;
        this.console = console;
    }

    /**
     * Runs the command.
     *
     * @param dataDirText explicit data directory, or null to resolve from the
     *        environment
     * @param nameText explicit administrator name, or null for the default
     * @param keyText explicit API key, or null to prompt or generate
     * @param generate true to skip prompting and generate a key
     * @return process exit code
     */
    int run(final String dataDirText, final String nameText, final String keyText, final boolean generate) {
        final var name = nameText == null || nameText.isBlank()
                ? BootstrapCredentialService.ADMIN_NAME
                : nameText.trim();

        final Path dataDir;
        try {
            dataDir = HubDefaults.resolveDataDir(dataDirText);
        } catch (final InvalidPathException e) {
            err.println("agent-hub: unusable data directory: " + e.getMessage());
            return 1;
        }

        final var graph = HubServiceFactory.create(true, dataDir, HubDefaults.DEFAULT_PBKDF2_ITERATIONS,
                new NoOpNotificationPort());

        if (graph.bootstrap().adminExists()) {
            err.println("agent-hub: an administrator already exists in " + dataDir);
            err.println("agent-hub: nothing was changed; existing keys are not recoverable");
            return 1;
        }

        final var key = resolveKey(keyText, generate);
        if (key.isEmpty()) {
            err.println("agent-hub: no API key was supplied");
            err.println("agent-hub: pass --key, pass --generate, or run from a terminal to be prompted");
            return 2;
        }

        final var keyValue = key.get();
        if (keyValue.length() < MIN_KEY_LENGTH) {
            err.println("agent-hub: the API key must be at least " + MIN_KEY_LENGTH + " characters");
            return 2;
        }

        final var created = graph.bootstrap().initializeAdmin(name, keyValue);
        if (created.wasError()) {
            err.println("agent-hub: unable to create the administrator");
            return 1;
        }

        out.println("Created administrator '" + name + "' in " + dataDir);
        if (keyText == null) {
            out.println();
            out.println("  API key: " + keyValue);
            out.println();
            out.println("This key is shown once. Only a hash is stored, so it cannot be");
            out.println("recovered later. Give it to the agent as NO_CARRIER_KEY.");
        }
        return 0;
    }

    private Optional<String> resolveKey(final String keyText, final boolean generate) {
        if (keyText != null && !keyText.isBlank()) {
            return Optional.of(keyText.trim());
        }
        if (generate) {
            return Optional.of(generateKey());
        }
        return console.promptSecretTwice("API key (at least " + MIN_KEY_LENGTH + " characters): ",
                "Repeat API key: ");
    }

    private String generateKey() {
        final var bytes = new byte[24];
        random.nextBytes(bytes);
        return "ah_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}