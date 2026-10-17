#!/bin/bash
# T-43 — SpotShift 테더링 머신(A90) 원격 상태 조회 (읽기 전용)
# usage: ./scripts/spotshift_status.sh [SERIAL]
#   SERIAL 미지정 시 $ADB_SERIAL → A90(SM-A908N) 자동 탐색 → 연결된 단일 기기
set -u
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

D="${1:-${ADB_SERIAL:-}}"
PKG="com.borasarang.spotshift"

# A90 우선 (USB RFCM9059YBB / 무선 192.168.86.30:5555 어느 쪽이든 잡힌다)
if [ -z "$D" ]; then
  for s in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do
    if [ "$(adb -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r')" = "SM-A908N" ]; then
      D="$s"; break
    fi
  done
fi
[ -z "$D" ] && D=$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')

if [ -z "$D" ] || ! adb -s "$D" shell echo ok >/dev/null 2>&1; then
  echo "[ERR] 기기 연결 실패: ${D:-없음} (adb devices 확인 · 무선: adb connect 192.168.86.30:5555)"
  exit 1
fi
MODEL=$(adb -s "$D" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
echo "== SpotShift 상태 ($D · $MODEL) =="
if [ "$MODEL" != "SM-A908N" ]; then
  echo "[WARN] A90(SM-A908N)이 아닙니다 — 검증 대상 외 기기"
fi

echo "--- 전원/온도 ---"
adb -s "$D" shell dumpsys battery 2>/dev/null | grep -E "level:|temperature:|status:|USB powered:" | sed 's/^ *//'

echo "--- 신호 ---"
adb -s "$D" shell dumpsys telephony.registry 2>/dev/null | grep -o -m1 "rsrp=-[0-9]*.*level=[0-9]" | head -1

echo "--- 핫스팟 ---"
adb -s "$D" shell 'dumpsys wifi 2>/dev/null | grep -m1 "mRole:"' | sed 's/^ *//'

echo "--- 공인 IP (최근 로그) ---"
adb -s "$D" shell "logcat -d -s SpotShift:*" 2>/dev/null | grep -o "공인 IP[^0-9]*[0-9.]*" | tail -1
adb -s "$D" shell "logcat -d -s SpotShift:*" 2>/dev/null | grep -E "IP 변경 성공" | tail -1

echo "--- DUN ---"
adb -s "$D" shell 'content query --uri content://telephony/carriers --projection _id:name:type --where "current=1"' 2>/dev/null | head -6

echo "--- 스케줄 ---"
adb -s "$D" shell 'am broadcast -a "androidx.work.diagnostics.REQUEST_DIAGNOSTICS" -p '"$PKG"' >/dev/null 2>&1; sleep 2; logcat -d 2>/dev/null' | grep "DiagnosticsWrkr" | grep -o -E "(RotationWorker|WatchdogWorker)[[:space:]]+[0-9]+[[:space:]]+[A-Z]+[[:space:]]+[a-z_]+" | head -4

echo "--- Shizuku ---"
if adb -s "$D" shell 'ps -A 2>/dev/null | grep -q shizuku_server'; then echo "server 실행 중"; else echo "server 미실행!"; fi

echo "--- 앱 ERROR ---"
ERRS=$(adb -s "$D" shell "logcat -d -s SpotShift:*" 2>/dev/null | grep -c " E SpotShift")
echo "SpotShift ERROR ${ERRS}건"
echo "[OK] 조회 완료"
