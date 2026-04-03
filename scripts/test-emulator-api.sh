#!/usr/bin/env bash

set -euo pipefail

PACKAGE_NAME="com.example.applauncher"
MAIN_ACTIVITY=".MainActivity"
API_PORT="3001"
HEALTH_URL="http://localhost:${API_PORT}/health"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

SKIP_INSTALL=false
if [[ "${1:-}" == "--skip-install" ]]; then
  SKIP_INSTALL=true
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: required command not found: $1" >&2
    exit 1
  fi
}

require_cmd adb
require_cmd curl

if [[ ! -x "${PROJECT_DIR}/gradlew" ]]; then
  echo "Error: gradlew not found or not executable in ${PROJECT_DIR}" >&2
  exit 1
fi

DEVICE_COUNT="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | wc -l | tr -d ' ')"
if [[ "${DEVICE_COUNT}" -eq 0 ]]; then
  echo "No Android device/emulator detected."
  echo "Start emulator first, for example:"
  echo "  emulator -avd pixel-emulator -no-snapshot-load"
  exit 1
fi

echo "[1/4] Install latest debug APK"
if [[ "${SKIP_INSTALL}" == true ]]; then
  echo "Skipping install (--skip-install)"
else
  (
    cd "${PROJECT_DIR}"
    ./gradlew installDebug
  )
fi

echo "[2/4] Restart app process"
echo "Waiting for emulator/device to be ready..."
adb wait-for-device
BOOT_COMPLETE="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
for _ in {1..30}; do
  if [[ "${BOOT_COMPLETE}" == "1" ]]; then
    break
  fi
  sleep 2
  BOOT_COMPLETE="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
done

adb shell am force-stop "${PACKAGE_NAME}" || true
adb shell am start -n "${PACKAGE_NAME}/${MAIN_ACTIVITY}" >/dev/null

echo "[3/4] Reset and apply adb port forward (${API_PORT})"
adb forward --remove "tcp:${API_PORT}" >/dev/null 2>&1 || true
adb forward "tcp:${API_PORT}" "tcp:${API_PORT}" >/dev/null

echo "[4/4] Check health endpoint"
# Slow emulators can take a while to initialize networking and app process.
MAX_ATTEMPTS=20
SLEEP_SECONDS=3

for attempt in $(seq 1 "${MAX_ATTEMPTS}"); do
  if curl --silent --show-error --fail --max-time 10 "${HEALTH_URL}"; then
    echo
    echo "Success: API is reachable at ${HEALTH_URL}"
    exit 0
  fi

  echo "Health not ready yet (attempt ${attempt}/${MAX_ATTEMPTS}), retrying in ${SLEEP_SECONDS}s..."
  sleep "${SLEEP_SECONDS}"
done

echo
echo "Health check failed after ${MAX_ATTEMPTS} attempts. Recent relevant logs:"
adb logcat -d | grep -E "MainActivity|ApiService|AndroidRuntime|FATAL|ANR" | tail -80 || true
exit 1
