#!/usr/bin/env bash

set -euo pipefail

AVD_NAME="${1:-pixel-emulator}"

if ! command -v emulator >/dev/null 2>&1; then
  echo "Error: emulator command not found in PATH"
  exit 1
fi

echo "Starting AVD '${AVD_NAME}' with low-overhead flags..."
exec emulator \
  -avd "${AVD_NAME}" \
  -no-snapshot-load \
  -no-boot-anim \
  -gpu swiftshader_indirect \
  -memory 2048
