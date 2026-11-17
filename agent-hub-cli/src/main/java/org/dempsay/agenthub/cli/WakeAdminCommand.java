package org.dempsay.agenthub.cli;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Sets and inspects wake pings on a running board.
 *
 * <p>The wake secret is write-only. This command never prints it, including when the
 * board's answer unexpectedly contains it.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
final class WakeAdminCommand {
    private final PrintStream out;
    private final PrintStream err;

    WakeAdminCommand(final PrintStream out, final PrintStream err) {
        this.out = out;
        this.err = err;
    }

    int run(final String[] args) {
        if (args.length < 2 || isHelp(args[1])) {
            usage(args.length < 2);
            return args.length < 2 ? 2 : 0;
        }
        return switch (args[1]) {
            case "show" -> show(args);
            case "set" -> set(args);
            case "clear" -> clear(args);
            case "delay" -> delay(args);
            case "test" -> test(args);
            default -> {
                err.println("agent-hub: unknown wake command '" + args[1] + "'");
                usage(true);
                yield 2;
            }
        };
    }

    private int show(final String[] args) {
        final var agent = agent(args);
        final var key = adminKey(args);
        if (agent == null || key == null) {
            return 2;
        }
        try {
            printView(new BoardClient(base(args)).showWake(key, agent));
            return 0;
        } catch (final IOException e) {
            return refused(e);
        }
    }

    private int set(final String[] args) {
        final var agent = agent(args);
        final var key = adminKey(args);
        final var url = option(args, "--url");
        if (agent == null || key == null) {
            return 2;
        }
        if (url == null || url.isBlank()) {
            err.println("agent-hub: --url is required");
            return 2;
        }
        try {
            printView(new BoardClient(base(args)).setWake(key, agent, url.trim(), option(args, "--wake-key"),
                    option(args, "--header")));
            return 0;
        } catch (final IOException e) {
            return refused(e);
        }
    }

    private int clear(final String[] args) {
        final var agent = agent(args);
        final var key = adminKey(args);
        if (agent == null || key == null) {
            return 2;
        }
        try {
            printView(new BoardClient(base(args)).clearWake(key, agent));
            return 0;
        } catch (final IOException e) {
            return refused(e);
        }
    }

    private int delay(final String[] args) {
        final var key = adminKey(args);
        if (key == null) {
            return 2;
        }
        final var secondsText = option(args, "--seconds");
        try {
            final var client = new BoardClient(base(args));
            final int seconds = secondsText == null
                    ? client.wakeDelay(key)
                    : client.setWakeDelay(key, Integer.parseInt(secondsText.trim()));
            out.println("wakeDelaySeconds: " + seconds);
            return 0;
        } catch (final NumberFormatException e) {
            err.println("agent-hub: --seconds must be an integer from 0 to 300");
            return 2;
        } catch (final IOException e) {
            return refused(e);
        }
    }

    private int test(final String[] args) {
        final var agent = agent(args);
        final var key = adminKey(args);
        if (agent == null || key == null) {
            return 2;
        }
        try {
            final var result = new BoardClient(base(args)).testWake(key, agent);
            if (result.statusCode() != null) {
                out.println("statusCode: " + result.statusCode());
                return 0;
            }
            err.println("agent-hub: " + (result.error() == null ? "wake ping failed" : result.error()));
            return 1;
        } catch (final IOException e) {
            return refused(e);
        }
    }

    private void printView(final BoardClient.WakeView view) {
        out.println("wakeConfigured: " + view.configured());
        if (view.host() != null) {
            out.println("wakeUrlHost: " + view.host());
        }
        if (view.headerName() != null) {
            out.println("wakeHeaderName: " + view.headerName());
        }
    }

    private int refused(final IOException error) {
        err.println("agent-hub: " + error.getMessage());
        return 1;
    }

    private String agent(final String[] args) {
        final var agent = option(args, "--agent");
        if (agent == null || agent.isBlank()) {
            err.println("agent-hub: --agent is required");
            return null;
        }
        return agent.trim();
    }

    private String adminKey(final String[] args) {
        final var key = firstNonBlank(option(args, "--key"), System.getenv(AgentHubCli.KEY_ENV));
        if (key == null) {
            err.println("agent-hub: an administrator key is required");
            err.println("agent-hub: pass --key or set " + AgentHubCli.KEY_ENV);
            return null;
        }
        return key;
    }

    private String base(final String[] args) {
        return firstNonBlank(option(args, "--base"), System.getenv(AgentHubCli.BASE_ENV), AgentHubCli.DEFAULT_BASE_URL);
    }

    private void usage(final boolean error) {
        final var stream = error ? err : out;
        stream.println("Usage: agent-hub admin wake <show|set|clear|delay|test> [options]");
        stream.println();
        stream.println("Configures the empty POST the board sends to wake an agent. The wake");
        stream.println("secret is write-only: it is not printed and not returned by the board.");
        stream.println();
        stream.println("  show  --agent ID");
        stream.println("  set   --agent ID --url URL [--wake-key SECRET] [--header NAME]");
        stream.println("  clear --agent ID");
        stream.println("  delay [--seconds N]     N is 0..300; 0 sends on the next turn; default 30");
        stream.println("  test  --agent ID        POST once now and print the status code");
        stream.println();
        stream.println("  --base URL        board base URL (env " + AgentHubCli.BASE_ENV + ")");
        stream.println("  --key KEY         administrator key (env " + AgentHubCli.KEY_ENV + ")");
    }

    private static boolean isHelp(final String token) {
        return "help".equals(token) || "--help".equals(token) || "-h".equals(token);
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
}
