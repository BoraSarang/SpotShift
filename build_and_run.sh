#!/bin/bash
# SpotShift 빌드/실행 디스패처
# usage: ./build_and_run.sh debug android
set -e

MODE=${1:-debug}
PLATFORM=${2:-android}
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

case "$PLATFORM" in
  android)
    cd android
    ./gradlew assembleDebug --no-daemon
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    adb shell am start -n com.borasarang.spotshift/.MainActivity
    ;;
  build)
    cd android
    ./gradlew assembleDebug --no-daemon
    ;;
  *)
    echo "지원 플랫폼: android | build"
    exit 1
    ;;
esac

echo "[OK] SpotShift 빌드/설치 완료 ($MODE/$PLATFORM)"
