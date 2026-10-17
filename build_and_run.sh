#!/bin/bash
# SpotShift 빌드/실행 디스패처 (실기기 = A90 전용)
# usage: ./build_and_run.sh debug android [SERIAL]
set -e

MODE=${1:-debug}
PLATFORM=${2:-android}
SERIAL=${3:-${ADB_SERIAL:-}}
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

# A90(SM-A908N) 우선 선택 → 없으면 연결된 단일 기기 (2대 연결 시 -s 필수)
pick_device() {
  if [ -n "$SERIAL" ]; then echo "$SERIAL"; return; fi
  local d=""
  for s in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
    if [ "$(adb -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r')" = "SM-A908N" ]; then
      d="$s"; break
    fi
  done
  [ -z "$d" ] && d=$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')
  echo "$d"
}

case "$PLATFORM" in
  android)
    D=$(pick_device)
    if [ -z "$D" ]; then
      echo "[ERR] A90(SM-A908N)이 연결돼 있지 않습니다. adb devices 확인 (무선: adb connect 192.168.86.30:5555)"
      exit 1
    fi
    echo "[INFO] 대상 기기: $D ($(adb -s "$D" shell getprop ro.product.model 2>/dev/null | tr -d '\r'))"
    cd android
    ./gradlew assembleDebug --no-daemon
    adb -s "$D" install -r app/build/outputs/apk/debug/app-debug.apk
    adb -s "$D" shell am start -n com.borasarang.spotshift/.MainActivity
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
