package org.dempsay.agenthub.cli;

import java.io.PrintStream;
import java.util.Objects;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Sets and inspects wake pings on a running board.
 *
 * <p>The wake secret is write-only. This command never prints it, including when the
 * board's answer unexpectedly contains it.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
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
        if (Objects.isNull(agent) || Objects.isNull(key)) {
            return 2;
        }
        return finishView(new BoardClient(base(args)).showWake(key, agent));
    }

    private int set(final String[] args) {
        final var agent = agent(args);
        final var key = adminKey(args);
        final var url = option(args, "--url");
        if (Objects.isNull(agent) || Objects.isNull(key)) {
            return 2;
        }
        if (Objects.isNull(url) || url.isBlank()) {
            err.println("agent-hub: --url is required");
            return 2;
        }
        return finishView(new BoardClient(base(args)).setWake(key, agent, url.trim(), option(args, "--wake-key"),
                option(args, "--header")));
    }

    private int clear(final String[] args) {
        final var agent = agent(args);
        final var key = adminKey(args);
        if (Objects.isNull(agent) || Objects.isNull(key)) {
            return 2;
        }
        return finishView(new BoardClient(base(args)).clearWake(key, agent));
    }

    private int delay(final String[] args) {
        final var key = adminKey(args);
        if (Objects.isNull(key)) {
            return 2;
        }
        final var client = new BoardClient(base(args));
        final ExceptionalResponse<BoardClient.HttpJson> answer;
        if (Objects.isNull(option(args, "--seconds"))) {
            answer = client.wakeDelay(key);
        } else {
            final var secondsText = option(args, "--seconds").trim();
            final var parsed = ExceptionalSupplier.of(() -> Integer.valueOf(secondsText)).execute();
            if (parsed.wasError()) {
                err.println("agent-hub: --seconds must be an integer from 0 to 300");
                return 2;
            }
            answer = client.setWakeDelay(key, parsed.response().intValue());
        }
        if (answer.wasError()) {
            err.println("agent-hub: could not reach the board");
            return 1;
        }
        if (answer.response().status() >= 400) {
            err.println("agent-hub: " + BoardClient.describe(answer.response()));
            return 1;
        }
        out.println("wakeDelaySeconds: " + answer.response().body().get("wakeDelaySeconds").getAsInt());
        return 0;
    }

    private int test(final String[] args) {
        final var agent = agent(args);
        final var key = adminKey(args);
        if (Objects.isNull(agent) || Objects.isNull(key)) {
            return 2;
        }
        final var answer = new BoardClient(base(args)).testWake(key, agent);
        if (answer.wasError()) {
            err.println("agent-hub: could not reach the board");
            return 1;
        }
        if (answer.response().status() >= 400) {
            err.println("agent-hub: " + BoardClient.describe(answer.response()));
            return 1;
        }
        final var body = answer.response().body();
        if (body.has("statusCode") && !body.get("statusCode").isJsonNull()) {
            out.println("statusCode: " + body.get("statusCode").getAsInt());
            return 0;
        }
        final var error = body.has("error") && !body.get("error").isJsonNull()
                ? body.get("error").getAsString()
                : "wake ping failed";
        err.println("agent-hub: " + error);
        return 1;
    }

    private int finishView(final ExceptionalResponse<BoardClient.HttpJson> answer) {
        if (answer.wasError()) {
            err.println("agent-hub: could not reach the board");
            return 1;
        }
        if (answer.response().status() >= 400) {
            err.println("agent-hub: " + BoardClient.describe(answer.response()));
            return 1;
        }
        printView(BoardClient.wakeView(answer.response().body()));
        return 0;
    }

    private void printView(final BoardClient.WakeView view) {
        out.println("wakeConfigured: " + view.configured());
        if (Objects.nonNull(view.host())) {
            out.println("wakeUrlHost: " + view.host());
        }
        if (Objects.nonNull(view.headerName())) {
            out.println("wakeHeaderName: " + view.headerName());
        }
    }

    private String agent(final String[] args) {
        final var agent = option(args, "--agent");
        if (Objects.isNull(agent) || agent.isBlank()) {
            err.println("agent-hub: --agent is required");
            return null;
        }
        return agent.trim();
    }

    private String adminKey(final String[] args) {
        final var key = firstNonBlank(option(args, "--key"), System.getenv(AgentHubCli.KEY_ENV));
        if (Objects.isNull(key)) {
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
