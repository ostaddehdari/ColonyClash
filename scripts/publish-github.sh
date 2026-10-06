#!/usr/bin/env bash
set -euo pipefail
REPO_NAME="${1:-colony-clash}"
if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is not installed. Install it, then run gh auth login."
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "Run: gh auth login"
  exit 1
fi
git init
git add .
git commit -m "build: add CI and reproducible Android APK pipeline" || true
git branch -M main
if git remote get-url origin >/dev/null 2>&1; then
  git push -u origin main
else
  gh repo create "$REPO_NAME" --private --source=. --remote=origin --push
fi
echo "GITHUB_PUBLISH_PASS"
