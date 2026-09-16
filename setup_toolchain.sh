#!/usr/bin/env bash
# 一键安装 Android 命令行工具链（WSL/Linux）: JDK已就绪, 只需 SDK + Gradle
set -euo pipefail

SDK_ROOT="$HOME/android-sdk"
TOOLS_DIR="$SDK_ROOT/cmdline-tools"
GRADLE_VER="8.9"
LOG() { echo "[$(date +%H:%M:%S)] $*"; }

mkdir -p "$SDK_ROOT" "$TOOLS_DIR"

if ! command -v unzip >/dev/null; then
  LOG "installing unzip..."
  apt-get update -qq && apt-get install -y -qq unzip >/dev/null
fi

# ---------- 1. cmdline-tools ----------
if [ ! -x "$TOOLS_DIR/latest/bin/sdkmanager" ]; then
  LOG "resolving latest commandlinetools-linux ..."
  ZIP_NAME=$(curl -s https://dl.google.com/android/repository/repository2-3.xml \
    | grep -o 'commandlinetools-linux-[0-9]*_latest\.zip' | head -1 || true)
  ZIP_NAME=${ZIP_NAME:-commandlinetools-linux-11076708_latest.zip}
  LOG "downloading $ZIP_NAME"
  curl -# -L -o /tmp/cmdline-tools.zip \
    "https://dl.google.com/android/repository/$ZIP_NAME"
  rm -rf /tmp/clt && mkdir -p /tmp/clt
  unzip -q /tmp/cmdline-tools.zip -d /tmp/clt
  rm -rf "$TOOLS_DIR/latest"
  mv /tmp/clt/cmdline-tools "$TOOLS_DIR/latest"
  chmod +x "$TOOLS_DIR/latest/bin/"*
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$TOOLS_DIR/latest/bin:$SDK_ROOT/platform-tools:$PATH"

# ---------- 2. licenses + packages ----------
LOG "accepting licenses..."
yes 2>/dev/null | sdkmanager --licenses >/dev/null 2>&1 || true

LOG "installing platform-tools / android-35 / build-tools 35.0.0 ..."
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" 2>&1 | tail -5

# ---------- 3. Gradle ----------
if [ ! -x /opt/gradle/gradle-$GRADLE_VER/bin/gradle ]; then
  LOG "downloading Gradle $GRADLE_VER ..."
  curl -# -L -o /tmp/gradle.zip \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip"
  mkdir -p /opt/gradle
  unzip -q -o /tmp/gradle.zip -d /opt/gradle
fi

LOG "=== VERSIONS ==="
/opt/gradle/gradle-$GRADLE_VER/bin/gradle --version 2>&1 | head -8
sdkmanager --list_installed 2>/dev/null | head -20
LOG "TOOLCHAIN READY"
