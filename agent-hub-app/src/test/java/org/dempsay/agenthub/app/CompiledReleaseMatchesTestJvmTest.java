package org.dempsay.agenthub.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.dempsay.agenthub.app.http.HttpAdapter;
import org.junit.jupiter.api.Test;

/**
 * Guards against running the suite on a different JDK than the one the artifact targets.
 *
 * <p>{@code maven.compiler.release} fixes the bytecode level, not the JVM that executes the
 * tests. Surefire inherits whatever {@code mvn} is running on, which on a developer machine is
 * decided by {@code PATH}. A newer JDK accepts the bytecode without complaint while differing in
 * runtime behaviour, so the suite can pass on one and the shipped artifact can still misbehave
 * on the other. That is not hypothetical: a response guard built on JDK exchange attributes
 * passed every test on Java 26 while making the Java 21 artifact answer exactly one request.
 *
 * <p>Reading the major version back out of the class file keeps this self-adjusting, so bumping
 * {@code maven.compiler.release} needs no edit here.
 */
class CompiledReleaseMatchesTestJvmTest {
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;
    private static final int FEATURE_OFFSET = 44;

    @Test
    void testsRunOnAJdkMatchingTheCompiledBytecodeLevel() throws IOException {
        final var major = majorClassFileVersion(HttpAdapter.class);
        final var compiled = major - FEATURE_OFFSET;
        final var running = Runtime.version().feature();

        assertEquals(compiled, running, () -> "Tests ran on Java " + running
                + ", but the classes were compiled for Java " + compiled + " (class file major version " + major
                + "). This project ships Java " + compiled + " bytecode, so a newer JVM runs the tests happily while"
                + " behaving differently at runtime. Rerun Maven with JAVA_HOME pointing at a JDK " + compiled
                + " installation.");
    }

    private int majorClassFileVersion(final Class<?> type) throws IOException {
        try (DataInputStream in = new DataInputStream(openClassFile(type))) {
            assertEquals(CLASS_FILE_MAGIC, in.readInt(), "not a class file");
            in.readUnsignedShort();
            return in.readUnsignedShort();
        }
    }

    private InputStream openClassFile(final Class<?> type) throws IOException {
        final var resource = type.getResourceAsStream(simpleName(type) + ".class");
        if (resource == null) {
            throw new IOException("cannot locate " + type.getName() + ".class on the test classpath");
        }
        return resource;
    }

    private String simpleName(final Class<?> type) {
        final var name = type.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
