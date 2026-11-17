package org.dempsay.agenthub.cli;

import java.io.PrintStream;
import java.util.Arrays;

/**
 * Entry point for the command-line adapter.
 *
 * <p>Two commands, each doing what only it can. {@code admin init} must run before
 * any administrator exists, so it writes to the store offline. {@code agent register}
 * needs a credential that only an administrator can mint, so it asks the running
 * board.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public final class AgentHubCli {
    static final String DEFAULT_BASE_URL = "http://127.0.0.1:8080";
    static final String KEY_ENV = "NO_CARRIER_KEY";
    static final String BASE_ENV = "NO_CARRIER_BASE";

    private AgentHubCli() {
    }

    /**
     * Process entry point.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        System.exit(run(args, System.out, System.err, CliConsole.system()));
    }

    static int run(final String[] args, final PrintStream out, final PrintStream err, final CliConsole console) {
        if (args.length == 0) {
            usage(out);
            return 2;
        }

        return switch (args[0]) {
            case "admin" -> admin(args, out, err, console);
            case "agent" -> agent(args, out, err);
            case "help", "--help", "-h" -> {
                usage(out);
                yield 0;
            }
            default -> {
                err.println("agent-hub: unknown command '" + args[0] + "'");
                usage(err);
                yield 2;
            }
        };
    }

    private static int admin(final String[] args, final PrintStream out, final PrintStream err,
            final CliConsole console) {
        final var rest = Arrays.copyOfRange(args, 1, args.length);
        if (rest.length == 0 || "help".equals(rest[0]) || "--help".equals(rest[0])) {
            out.println("Usage: agent-hub admin <init|wake> [options]");
            out.println();
            out.println("  init    Create the first administrator (offline; stop the server first)");
            out.println("  wake    Set or clear an agent's wake URL, the delay, or send a test ping");
            out.println();
            out.println("Run 'agent-hub admin init --help' or 'agent-hub admin wake --help' for options.");
            return rest.length == 0 ? 2 : 0;
        }

        if ("wake".equals(rest[0])) {
            return new WakeAdminCommand(out, err).run(rest);
        }

        if (!"init".equals(rest[0])) {
            err.println("agent-hub: unknown admin command '" + rest[0] + "'");
            return 2;
        }

        if (rest.length > 1 && ("help".equals(rest[1]) || "--help".equals(rest[1]))) {
            out.println("Usage: agent-hub admin init [options]");
            out.println();
            out.println("Creates the first administrator directly in the store. Stop the server");
            out.println("first; two processes must not write the store at once.");
            out.println();
            out.println("  --data-dir PATH   persistence root (env " + org.dempsay.agenthub.core.HubDefaults.DATA_DIR_ENV + ")");
            out.println("  --name NAME       administrator name (default admin)");
            out.println("  --key KEY         use this key instead of prompting");
            out.println("  --generate        generate a key instead of prompting");
            return 0;
        }

        return new AdminInitCommand(out, err, console).run(
                option(rest, "--data-dir"),
                option(rest, "--name"),
                option(rest, "--key"),
                has(rest, "--generate"));
    }

    private static int agent(final String[] args, final PrintStream out, final PrintStream err) {
        final var rest = Arrays.copyOfRange(args, 1, args.length);
        if (rest.length == 0 || "help".equals(rest[0]) || "--help".equals(rest[0])) {
            out.println("Usage: agent-hub agent register --name NAME [--kind human|automated] [options]");
            out.println();
            out.println("Registers an agent on a running board. Requires administrator credentials.");
            out.println();
            out.println("  --name NAME       agent name (required)");
            out.println("  --kind KIND       human or automated (default automated)");
            out.println("  --owner ID        owner agent id");
            out.println("  --base URL        board base URL (env " + BASE_ENV + ", default " + DEFAULT_BASE_URL + ")");
            out.println("  --key KEY         administrator key (env " + KEY_ENV + ")");
            out.println("  --out FILE        write the credential to FILE (mode 600) instead of stdout");
            return rest.length == 0 ? 2 : 0;
        }

        if (!"register".equals(rest[0])) {
            err.println("agent-hub: unknown agent command '" + rest[0] + "'");
            return 2;
        }

        final var kind = option(rest, "--kind");
        return new RegisterAgentCommand(out, err).run(
                option(rest, "--name"),
                kind == null ? "automated" : kind,
                firstNonBlank(option(rest, "--base"), System.getenv(BASE_ENV), DEFAULT_BASE_URL),
                firstNonBlank(option(rest, "--key"), System.getenv(KEY_ENV)),
                option(rest, "--owner"),
                option(rest, "--out"));
    }

    private static void usage(final PrintStream out) {
        out.println("Usage: agent-hub <command> [options]");
        out.println();
        out.println("Commands:");
        out.println("  admin init        Create the first administrator (offline, stops the server first)");
        out.println("  admin wake        Set a wake URL, the delay, or send a test ping");
        out.println("  agent register    Register an agent on a running board");
        out.println();
        out.println("Run 'agent-hub <command> --help' for the options of a command.");
    }

    private static String firstNonBlank(final String... candidates) {
        for (final var candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private static String option(final String[] args, final String name) {
        for (var i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                final var value = args[i + 1];
                if (!value.startsWith("--")) {
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean has(final String[] args, final String name) {
        return Arrays.asList(args).contains(name);
    }
}