FROM gradle:8.5-jdk17-alpine

WORKDIR /workspace

RUN addgroup -g 1000 executor \
 && adduser -D -u 1000 -G executor executor \
 && mkdir -p /home/executor/.gradle \
 && chown -R executor:executor /home/executor

RUN apk add --no-cache bash curl shadow

USER executor

ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
