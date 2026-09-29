#!/usr/bin/env bash
# Stops the application started by deploy-local.sh.
set -uo pipefail

PID_FILE="${RUNNER_TEMP:-/tmp}/app.pid"

if [[ -f "${PID_FILE}" ]]; then
  PID="$(cat "${PID_FILE}")"
  if kill -0 "${PID}" 2> /dev/null; then
    kill "${PID}"
    echo "Stopped application (pid ${PID})"
  fi
  rm -f "${PID_FILE}"
fi
exit 0
