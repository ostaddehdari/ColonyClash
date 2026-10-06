#!/usr/bin/env bash
set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
GRADLE_VERSION="8.13"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-15859902_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"

if [[ $EUID -ne 0 ]]; then
  echo "Run with sudo: sudo bash scripts/bootstrap-ubuntu-build-server.sh"
  exit 1
fi

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  openjdk-17-jdk git curl wget unzip zip jq ca-certificates build-essential \
  adb docker.io docker-compose-plugin

# Node.js 22
curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
DEBIAN_FRONTEND=noninteractive apt-get install -y nodejs

# Android command line tools
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools/latest"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
wget -q "$CMDLINE_TOOLS_URL" -O "$TMP_DIR/$CMDLINE_TOOLS_ZIP"
unzip -q "$TMP_DIR/$CMDLINE_TOOLS_ZIP" -d "$TMP_DIR/android-cli"
cp -a "$TMP_DIR/android-cli/cmdline-tools/." "$ANDROID_SDK_ROOT/cmdline-tools/latest/"

cat >/etc/profile.d/android-sdk.sh <<ENV
export ANDROID_HOME=$ANDROID_SDK_ROOT
export ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT
export PATH=\$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools
ENV

export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools"
yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

# Gradle 8.13
wget -q "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -O "$TMP_DIR/gradle.zip"
rm -rf "/opt/gradle-${GRADLE_VERSION}"
unzip -q "$TMP_DIR/gradle.zip" -d /opt
ln -sfn "/opt/gradle-${GRADLE_VERSION}" /opt/gradle
cat >/etc/profile.d/gradle.sh <<'ENV'
export GRADLE_HOME=/opt/gradle
export PATH=$PATH:$GRADLE_HOME/bin
ENV
export GRADLE_HOME=/opt/gradle
export PATH="$PATH:$GRADLE_HOME/bin"

systemctl enable --now docker || true

printf '\n=== TOOLCHAIN ===\n'
java -version
node --version
npm --version
gradle --version | sed -n '1,12p'
adb version | sed -n '1,2p'
sdkmanager --list_installed | grep -E 'platforms;android-36|build-tools;36.0.0|platform-tools' || true
printf '\nBUILD_SERVER_READY\n'
