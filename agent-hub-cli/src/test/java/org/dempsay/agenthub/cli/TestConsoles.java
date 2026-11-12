package org.dempsay.agenthub.cli;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Test consoles.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
final class TestConsoles {
    private TestConsoles() {
    }

    /**
     * A console with no terminal attached, so prompting returns nothing.
     *
     * @return the console
     */
    static CliConsole nonInteractive() {
        return new FixedConsole(false, new ArrayDeque<>());
    }

    /**
     * A console that answers every secret prompt with the same value, as if the
     * operator typed it twice.
     *
     * @param secret value returned for each prompt
     * @return the console
     */
    static CliConsole answering(final String secret) {
        final Deque<String> answers = new ArrayDeque<>();
        answers.add(secret);
        answers.add(secret);
        return new FixedConsole(true, answers);
    }

    private static final class FixedConsole implements CliConsole {
        private final boolean interactive;
        private final Deque<String> answers;

        private FixedConsole(final boolean interactive, final Deque<String> answers) {
            this.interactive = interactive;
            this.answers = answers;
        }

        @Override
        public boolean interactive() {
            return interactive;
        }

        @Override
        public Optional<String> promptSecret(final String prompt) {
            return Optional.ofNullable(answers.poll());
        }

        @Override
        public Optional<String> promptSecretTwice(final String prompt, final String confirmPrompt) {
            final var first = promptSecret(prompt);
            final var second = promptSecret(confirmPrompt);
            if (first.isEmpty() || second.isEmpty()) {
                return Optional.empty();
            }
            return first.get().equals(second.get()) ? first : Optional.empty();
        }
    }
}