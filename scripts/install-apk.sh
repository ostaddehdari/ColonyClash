#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MARKET="${1:-play}"
APK="$ROOT/dist/ColonyClash-${MARKET}-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK"
  echo "Run: API_BASE_URL=https://YOUR-DOMAIN bash scripts/build-stage.sh"
  exit 1
fi
adb devices
adb install -r "$APK"
echo "INSTALLED: $APK"
