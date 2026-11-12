package org.dempsay.agenthub.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * Registers an agent on a running board and hands back its credential.
 *
 * <p>This is the step that otherwise forces a human to read a key off the server's
 * first-start output and paste it into another agent's environment. The key is shown
 * once here, exactly as the board shows it once, and can optionally be written
 * straight to a file the agent can read.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.1.0
 */
public final class RegisterAgentCommand {
    private final PrintStream out;
    private final PrintStream err;

    RegisterAgentCommand(final PrintStream out, final PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Runs the command.
     *
     * @param nameText agent name
     * @param kind agent kind, human or automated
     * @param baseUrl board base URL
     * @param adminKey administrator key used to authorize the request
     * @param ownerText optional owner agent id, or null
     * @param outText optional file to write the credential to, or null
     * @return process exit code
     */
    int run(final String nameText, final String kind, final String baseUrl, final String adminKey,
            final String ownerText, final String outText) {
        if (nameText == null || nameText.isBlank()) {
            err.println("agent-hub: --name is required");
            return 2;
        }
        if (!"human".equals(kind) && !"automated".equals(kind)) {
            err.println("agent-hub: --kind must be human or automated");
            return 2;
        }
        if (adminKey == null || adminKey.isBlank()) {
            err.println("agent-hub: an administrator key is required");
            err.println("agent-hub: pass --key or set NO_CARRIER_KEY");
            return 2;
        }

        final BoardClient.CreatedAgent created;
        try {
            created = new BoardClient(baseUrl).createAgent(adminKey, nameText.trim(), kind, ownerText);
        } catch (final IOException e) {
            err.println("agent-hub: " + e.getMessage());
            return 1;
        }

        if (outText != null && !outText.isBlank()) {
            try {
                writeCredential(Path.of(outText.trim()), created);
            } catch (final IOException e) {
                err.println("agent-hub: the agent was created, but the credential file failed: " + e.getMessage());
                err.println("agent-hub: the key is shown below and must be stored now");
                printKey(nameText.trim(), created);
                return 1;
            }
            out.println("Created agent '" + nameText.trim() + "' (" + kind + ")");
            out.println("Wrote its credential to " + Path.of(outText.trim()).toAbsolutePath());
            out.println("It is mode 600; give the agent that file, not this terminal.");
            return 0;
        }

        out.println("Created agent '" + nameText.trim() + "' (" + kind + ")");
        printKey(nameText.trim(), created);
        return 0;
    }

    private void printKey(final String name, final BoardClient.CreatedAgent created) {
        out.println();
        out.println("  Agent id: " + created.agentId());
        out.println("  API key:  " + created.apiKey());
        out.println();
        out.println("This key is shown once. Only a hash is stored, so it cannot be");
        out.println("recovered later. Give it to the agent as NO_CARRIER_KEY.");
    }

    private void writeCredential(final Path path, final BoardClient.CreatedAgent created) throws IOException {
        final var parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        final var contents = "NO_CARRIER_KEY=" + created.apiKey() + System.lineSeparator()
                + "NO_CARRIER_AGENT_ID=" + created.agentId() + System.lineSeparator();
        Files.writeString(path, contents, StandardCharsets.UTF_8);
        restrictToOwner(path);
    }

    private void restrictToOwner(final Path path) throws IOException {
        try {
            final Set<PosixFilePermission> ownerOnly = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, ownerOnly);
        } catch (final UnsupportedOperationException e) {
            err.println("agent-hub: warning: this filesystem cannot mark the credential file owner-only");
        }
    }
}