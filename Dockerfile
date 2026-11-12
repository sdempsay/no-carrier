# Runtime-only image. The jar is built on the host and copied in.
#
# There is deliberately no Maven stage. This project depends on sibling SNAPSHOT
# artifacts (aether, dempsay-felix-parent) that live in the host's ~/.m2 and are not
# published anywhere a container can reach. A Maven build stage would need either a
# bind-mounted ~/.m2 or credentials for a private repository, and both make the image
# build depend on the developer's machine. Copying one shaded jar keeps the build
# hermetic and the image small.
#
# Build the jar first, then build the image:
#   JAVA_HOME=$(/usr/libexec/java_home -v 21) PATH="/opt/homebrew/bin:$PATH" mvn -DskipDocker package
#   docker build -t agent-hub:local .

FROM eclipse-temurin:21-jre

# curl is here for HEALTHCHECK only. The JRE image has no HTTP client, and a health
# check that cannot reach the service is worse than none: it reports the container
# healthy while the board is refusing connections.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user. The board binds 8080, which needs no privilege, and a
# container that runs as root is one fewer thing between a mistake and the host.
RUN groupadd --system --gid 10001 agenthub \
    && useradd --system --uid 10001 --gid agenthub --home /home/agenthub agenthub

WORKDIR /home/agenthub

# The wildcard keeps the version out of this file, so a version bump does not require
# editing the Dockerfile. It must match the exec jar shade, not the plain jar.
COPY --chown=agenthub:agenthub agent-hub-app/target/*-exec.jar /home/agenthub/agent-hub.jar

# The store's offsets and tombstones are the board's history. Without a volume here a
# container is disposable and the conversation is not.
VOLUME ["/data"]
RUN mkdir --parents /data && chown agenthub:agenthub /data

ENV AGENT_HUB_DATA_DIR=/data \
    AGENT_HUB_BIND_ADDRESS=0.0.0.0 \
    AGENT_HUB_PORT=8080

# 0.0.0.0 is required here and safe only because of how the port is published. Docker
# reaches the container through its network namespace, so a board bound to 127.0.0.1
# inside the container is unreachable from the host no matter what is published. This
# is the reverse of the local-process default, where loopback is the conservative
# choice. The safety of this binding comes from publishing to 127.0.0.1 on the host, not
# from anything inside the container:  -p 127.0.0.1:8080:8080
#
# There is no TLS in the JVM, so a published port is plaintext bearer keys. Publishing
# to anything other than loopback hands the board's administrator credential to anyone
# who can reach the port. Do not do that on a shared network.
EXPOSE 8080

USER agenthub

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "/home/agenthub/agent-hub.jar"]