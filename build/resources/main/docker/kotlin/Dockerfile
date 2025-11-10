FROM gradle:8.5-jdk17

WORKDIR /workspace

ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Use the existing gradle user (typically uid/gid 1000)
USER gradle

ENTRYPOINT ["/entrypoint.sh"]
