package org.dempsay.agenthub.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Boots the application in a separate JVM and drives it over real sockets.
 *
 * <p>Every other HTTP test runs inside the Surefire JVM, and that environment does not
 * surface the shared-state behaviour of the JDK HTTP server. An in-process test therefore
 * cannot prove the shipped server answers more than one request. Only a separate process
 * exercises the runtime the application actually ships in.
 */
class StandaloneServerSmokeTest {
    private static final Pattern LISTENING = Pattern.compile("listening on 127\\.0\\.0\\.1:(\\d+)");
    private static final Pattern BOOTSTRAP_KEY = Pattern.compile("\\b([0-9a-f]{32,64})\\b");
    private static final int SOCKET_TIMEOUT_MILLIS = 5000;
    private static final int STARTUP_SECONDS = 60;

    @Test
    void answersEveryRequestWhenRunAsItsOwnProcess(@TempDir final Path dataDir) throws Exception {
        final var server = launch(dataDir);
        try {
            final var banner = server.banner();
            assertNotNull(banner.port(), "server never reported a bound port");
            assertNotNull(banner.key(), "server never reported a first-start administrator key");

            for (int i = 1; i <= 5; i++) {
                assertEquals("HTTP/1.1 200 OK", healthStatus(banner.port()),
                        "anonymous request " + i + " on a separate connection failed");
            }

            assertEquals("HTTP/1.1 200 OK", authorizedStatus(banner.port(), banner.key()),
                    "first authorized request failed");
            assertEquals("HTTP/1.1 200 OK", authorizedStatus(banner.port(), banner.key()),
                    "second authorized request failed");
        } finally {
            server.destroy();
        }
    }

    private Runner launch(final Path dataDir) throws IOException {
        final var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        final var process = new ProcessBuilder(java,
                "-cp", System.getProperty("java.class.path"),
                AgentHubApp.class.getName(),
                "--data-dir", dataDir.toString(),
                "--port", "0")
                .redirectErrorStream(true)
                .start();
        return new Runner(process);
    }

    private String healthStatus(final int port) throws IOException, InterruptedException {
        return status(port, null);
    }

    private String authorizedStatus(final int port, final String key) throws IOException, InterruptedException {
        return status(port, key);
    }

    private String status(final int port, final String key) throws IOException, InterruptedException {
        final var request = new StringBuilder("GET /health HTTP/1.1\r\nHost: localhost\r\n");
        if (key != null) {
            request.append("Authorization: Bearer ").append(key).append("\r\n");
        }
        request.append("Connection: close\r\n\r\n");

        try (var socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            final var reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            return reader.readLine();
        }
    }

    private record Banner(int port, String key) {
    }

    private static final class Runner {
        private final Process process;
        private final BlockingQueue<String> lines = new ArrayBlockingQueue<>(64);

        private Runner(final Process process) {
            this.process = process;
            final var reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            final var pump = new Thread(() -> pump(reader), "standalone-server-banner");
            pump.setDaemon(true);
            pump.start();
        }

        private void pump(final BufferedReader reader) {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                lines.offer(line);
                }
            } catch (final IOException e) {
                // The process was destroyed, so the banner simply ends here.
            }
        }

        private Banner banner() throws InterruptedException {
            var port = 0;
            String key = null;
            final var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STARTUP_SECONDS);
            while (System.nanoTime() < deadline) {
                final var line = lines.poll(1, TimeUnit.SECONDS);
                if (line == null) {
                    continue;
                }
                final Matcher listening = LISTENING.matcher(line);
                if (listening.find()) {
                    port = Integer.parseInt(listening.group(1));
                }
                final Matcher matcher = BOOTSTRAP_KEY.matcher(line);
                if (matcher.find()) {
                    key = matcher.group(1);
                }
                if (port != 0 && key != null) {
                    return new Banner(port, key);
                }
            }
            throw new IllegalStateException("server did not finish starting");
        }

        private void destroy() throws InterruptedException {
            process.destroy();
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "server did not shut down");
        }
    }
}
