FROM python:3.11-alpine

WORKDIR /workspace

RUN addgroup -g 1000 executor \
 && adduser -D -u 1000 -G executor executor

RUN apk add --no-cache gcc musl-dev libffi-dev openssl-dev bash shadow

RUN pip install --no-cache-dir --upgrade pip \
 && pip install --no-cache-dir pytest pytest-cov pytest-timeout

USER executor

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
