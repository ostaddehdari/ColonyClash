#!/usr/bin/env bash
set -euo pipefail

REPO_HTTPS="https://github.com/ostaddehdari/ColonyClash.git"
REPO_SSH="git@github.com:ostaddehdari/ColonyClash.git"
BRANCH="main"
VERSION="v0.8.1"

if ! command -v git >/dev/null 2>&1; then
  echo "ERROR: git is not installed" >&2
  exit 1
fi

if git ls-remote "$REPO_SSH" >/dev/null 2>&1; then
  REPO_URL="$REPO_SSH"
else
  REPO_URL="$REPO_HTTPS"
fi

# refuse accidental secrets commonly generated locally
if find . -maxdepth 3 -type f \( -name '.env' -o -name '*.jks' -o -name '*.keystore' -o -name 'google-services.json' \) | grep -q .; then
  echo "ERROR: sensitive local file detected; remove it before pushing:" >&2
  find . -maxdepth 3 -type f \( -name '.env' -o -name '*.jks' -o -name '*.keystore' -o -name 'google-services.json' \) >&2
  exit 1
fi

if [ ! -d .git ]; then
  git init
fi

git config user.name "ostaddehdari" >/dev/null 2>&1 || true
# Preserve an existing server Git identity/email if configured.
if ! git config user.email >/dev/null 2>&1; then
  git config user.email "ostaddehdari@users.noreply.github.com"
fi

git branch -M "$BRANCH"

if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REPO_URL"
else
  git remote add origin "$REPO_URL"
fi

git add -A
if git diff --cached --quiet; then
  echo "No new changes to commit."
else
  git commit -m "feat: ColonyClash ${VERSION} build-ready baseline"
fi

# The target repository is currently intended as the project origin.
git push -u origin "$BRANCH"

# Tag the baseline if the tag is not already present locally/remotely.
if ! git rev-parse "$VERSION" >/dev/null 2>&1; then
  git tag -a "$VERSION" -m "ColonyClash ${VERSION} build-ready baseline"
fi
git push origin "$VERSION" || true

echo "GITHUB_PUSH_OK"
echo "REPO=$REPO_URL"
echo "BRANCH=$BRANCH"
echo "VERSION=$VERSION"
