#!/usr/bin/env bash
# Simulated deployment for the demo pipeline: starts the jar on the runner and
# waits until the readiness probe is UP. Swap this for a real deploy step
# (cloud CLI, Helm, etc.) in a customer pipeline.
#
# Usage: deploy-local.sh <jar-path> <port> <environment>
set -euo pipefail

JAR_PATH="${1:?jar path required}"
PORT="${2:-8080}"
TARGET_ENV="${3:-dev}"
PID_FILE="${RUNNER_TEMP:-/tmp}/app.pid"
LOG_FILE="${RUNNER_TEMP:-/tmp}/app.log"
READY_URL="http://localhost:${PORT}/actuator/health/readiness"
MAX_WAIT_SECONDS=90

echo "Deploying ${JAR_PATH} to '${TARGET_ENV}' on port ${PORT}"

PORT="${PORT}" SPRING_PROFILES_ACTIVE="${TARGET_ENV}" \
  nohup java -jar "${JAR_PATH}" > "${LOG_FILE}" 2>&1 &
echo $! > "${PID_FILE}"

for ((i = 1; i <= MAX_WAIT_SECONDS; i++)); do
  if curl -fsS "${READY_URL}" > /dev/null 2>&1; then
    echo "Application is ready after ${i}s"
    exit 0
  fi
  if ! kill -0 "$(cat "${PID_FILE}")" 2> /dev/null; then
    echo "::error::Application process exited during startup"
    cat "${LOG_FILE}"
    exit 1
  fi
  sleep 1
done

echo "::error::Application did not become ready within ${MAX_WAIT_SECONDS}s"
cat "${LOG_FILE}"
exit 1
