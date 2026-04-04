#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
APK_DIR="${PROJECT_DIR}/app/build/outputs/apk/release"
KEYSTORE_PROPERTIES_FILE="${PROJECT_DIR}/keystore.properties"
DEFAULT_RELEASE_KEYSTORE_REL_PATH="app/release-keystore.jks"

SERIAL=""

usage() {
  cat <<EOF
Usage: $(basename "$0") [--serial <device-id>]

Builds a release APK with Gradle and installs it on a connected Android device.
If release signing is not configured yet, this script bootstraps a local signing key.

Options:
  --serial <device-id>   Install to a specific device serial from 'adb devices'
  -h, --help             Show this help text
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      if [[ $# -lt 2 ]]; then
        echo "Error: --serial requires a value" >&2
        exit 1
      fi
      SERIAL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Error: Unknown argument '$1'" >&2
      usage
      exit 1
      ;;
  esac
done

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: required command not found: $1" >&2
    exit 1
  fi
}

random_secret() {
  local secret
  set +o pipefail
  secret="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"
  set -o pipefail
  printf '%s' "${secret}"
}

bootstrap_release_signing_if_needed() {
  if [[ -f "${KEYSTORE_PROPERTIES_FILE}" ]]; then
    return
  fi

  local keystore_abs_path="${PROJECT_DIR}/${DEFAULT_RELEASE_KEYSTORE_REL_PATH}"
  if [[ -f "${keystore_abs_path}" ]]; then
    echo "Error: ${DEFAULT_RELEASE_KEYSTORE_REL_PATH} exists but keystore.properties is missing." >&2
    echo "Create ${KEYSTORE_PROPERTIES_FILE} with RELEASE_* values, or remove the keystore and retry." >&2
    exit 1
  fi

  require_cmd keytool

  local store_password
  local key_password
  local key_alias="app_launcher_release"
  store_password="$(random_secret)"
  key_password="${store_password}"

  echo "[setup] Release signing not configured. Creating local keystore..."
  keytool -genkeypair \
    -v \
    -storetype PKCS12 \
    -keystore "${keystore_abs_path}" \
    -alias "${key_alias}" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "${store_password}" \
    -keypass "${key_password}" \
    -dname "CN=App Launcher Local Release, OU=Local, O=Local, L=Local, S=Local, C=US" >/dev/null 2>&1

  cat > "${KEYSTORE_PROPERTIES_FILE}" <<EOF
RELEASE_STORE_FILE=${DEFAULT_RELEASE_KEYSTORE_REL_PATH}
RELEASE_STORE_PASSWORD=${store_password}
RELEASE_KEY_ALIAS=${key_alias}
RELEASE_KEY_PASSWORD=${key_password}
EOF

  echo "[setup] Created ${DEFAULT_RELEASE_KEYSTORE_REL_PATH} and keystore.properties"
}

require_cmd adb

bootstrap_release_signing_if_needed

if [[ ! -x "${PROJECT_DIR}/gradlew" ]]; then
  echo "Error: gradlew not found or not executable in ${PROJECT_DIR}" >&2
  exit 1
fi

mapfile -t DEVICES < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')

if [[ -n "${SERIAL}" ]]; then
  FOUND=false
  for d in "${DEVICES[@]:-}"; do
    if [[ "$d" == "${SERIAL}" ]]; then
      FOUND=true
      break
    fi
  done
  if [[ "${FOUND}" != true ]]; then
    echo "Error: device '${SERIAL}' is not connected and ready." >&2
    echo "Connected devices:" >&2
    adb devices >&2
    exit 1
  fi
  ADB_TARGET=("-s" "${SERIAL}")
else
  if [[ ${#DEVICES[@]} -eq 0 ]]; then
    echo "Error: no connected Android devices found." >&2
    echo "Check cable, USB debugging, and 'adb devices'." >&2
    exit 1
  fi
  if [[ ${#DEVICES[@]} -gt 1 ]]; then
    echo "Error: multiple devices connected. Use --serial <device-id>." >&2
    adb devices >&2
    exit 1
  fi
  ADB_TARGET=("-s" "${DEVICES[0]}")
fi

echo "[1/3] Building release APK (assembleRelease)"
(
  cd "${PROJECT_DIR}"
  ./gradlew assembleRelease
)

APK_PATH=""
if [[ -f "${APK_DIR}/app-release.apk" ]]; then
  APK_PATH="${APK_DIR}/app-release.apk"
elif [[ -f "${APK_DIR}/app-release-unsigned.apk" ]]; then
  APK_PATH="${APK_DIR}/app-release-unsigned.apk"
else
  APK_PATH="$(find "${APK_DIR}" -maxdepth 1 -type f -name "*.apk" | head -n 1 || true)"
fi

if [[ -z "${APK_PATH}" || ! -f "${APK_PATH}" ]]; then
  echo "Error: could not find a release APK in ${APK_DIR}" >&2
  exit 1
fi

if [[ "${APK_PATH}" == *"-unsigned.apk" ]]; then
  echo "Error: built APK is unsigned: ${APK_PATH}" >&2
  echo "Configure release signing in app/build.gradle.kts before installing release builds." >&2
  exit 1
fi

echo "[2/3] Installing ${APK_PATH}"
INSTALL_OUTPUT="$(adb "${ADB_TARGET[@]}" install -r "${APK_PATH}" 2>&1 || true)"
echo "${INSTALL_OUTPUT}"

if [[ "${INSTALL_OUTPUT}" != *"Success"* ]]; then
  echo "Error: adb install did not report success." >&2
  exit 1
fi

echo "[3/3] Done"
echo "Installed release APK on device: ${ADB_TARGET[1]}"
