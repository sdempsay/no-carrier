package org.dempsay.agenthub.cli;

import java.io.Console;
import java.util.Arrays;
import java.util.Optional;

/**
 * Interactive input for the command-line adapter.
 *
 * <p>Prompts that read an API key never echo it. When no terminal is attached the
 * implementation reports itself as non-interactive so a command can refuse rather
 * than silently block on a stdin that will never supply a line.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public interface CliConsole {
    /**
     * Whether a terminal is attached and prompting is possible.
     *
     * @return true when the operator can be prompted
     */
    boolean interactive();

    /**
     * Reads a secret without echoing it.
     *
     * @param prompt text shown before the input
     * @return the entered secret, or empty when non-interactive
     */
    Optional<String> promptSecret(String prompt);

    /**
     * Reads a secret twice and requires the two to match.
     *
     * @param prompt text shown before each input
     * @param confirmPrompt text shown before the repeat
     * @return the entered secret, or empty when non-interactive or the two differ
     */
    Optional<String> promptSecretTwice(String prompt, String confirmPrompt);

    /**
     * Console backed by the real terminal, degrading to non-interactive when stdin is
     * redirected.
     */
    static CliConsole system() {
        return new SystemCliConsole();
    }

    /**
     * Terminal-backed console.
     */
    final class SystemCliConsole implements CliConsole {
        private final Console console = System.console();

        @Override
        public boolean interactive() {
            return console != null;
        }

        @Override
        public Optional<String> promptSecret(final String prompt) {
            if (console == null) {
                return Optional.empty();
            }
            final var entered = console.readPassword(prompt);
            if (entered == null) {
                return Optional.empty();
            }
            final var value = new String(entered);
            Arrays.fill(entered, '\0');
            return value.isBlank() ? Optional.empty() : Optional.of(value);
        }

        @Override
        public Optional<String> promptSecretTwice(final String prompt, final String confirmPrompt) {
            final var first = promptSecret(prompt);
            if (first.isEmpty()) {
                return Optional.empty();
            }
            final var second = promptSecret(confirmPrompt);
            if (second.isEmpty()) {
                return Optional.empty();
            }
            return first.get().equals(second.get()) ? first : Optional.empty();
        }
    }
}