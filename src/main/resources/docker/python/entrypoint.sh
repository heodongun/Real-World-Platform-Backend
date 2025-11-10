#!/bin/sh
set -e

timeout="${EXECUTION_TIMEOUT:-30}"

if command -v timeout >/dev/null 2>&1; then
  timeout "$timeout" "$@"
else
  "$@"
fi
