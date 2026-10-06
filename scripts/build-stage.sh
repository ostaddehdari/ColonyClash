#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_BASE_URL="${API_BASE_URL:-https://api.example.com}"
mkdir -p "$ROOT/dist"
rm -f "$ROOT/dist"/*.apk "$ROOT/dist"/*.aab "$ROOT/dist"/SHA256SUMS.txt 2>/dev/null || true

printf '\n=== BACKEND ===\n'
cd "$ROOT/backend"
npm install --no-audit --no-fund
npm run build
npm test

printf '\n=== ANDROID ===\n'
cd "$ROOT/android"
gradle --no-daemon -PAPI_BASE_URL="$API_BASE_URL" \
  assemblePlayDebug assembleIranDebug assembleChinaDebug

cp app/build/outputs/apk/play/debug/app-play-debug.apk "$ROOT/dist/ColonyClash-play-debug.apk"
cp app/build/outputs/apk/iran/debug/app-iran-debug.apk "$ROOT/dist/ColonyClash-iran-debug.apk"
cp app/build/outputs/apk/china/debug/app-china-debug.apk "$ROOT/dist/ColonyClash-china-debug.apk"

cd "$ROOT"
sha256sum dist/*.apk > dist/SHA256SUMS.txt
printf '\n=== OUTPUT ===\n'
ls -lh dist/*.apk dist/SHA256SUMS.txt
cat dist/SHA256SUMS.txt
printf '\nBUILD_STAGE_PASS\n'
