# PLAN_v0.5_android.md — 스케줄러 WorkManager 이관 (자동 변경 실패 근본 수정)

> 버전: v0.5 (2026-10-06) · 플랫폼: Android · 작성자: BoRaSaRang
> 전제: v0.4.1 (T-25까지). 배터리 예외 등록됨·Shizuku 정상·셀룰러 관문 통과 확인済

## 1. 배경 (S22 실측, 2026-10-06)

- FGS 크래시 6회 (`ForegroundServiceStartNotAllowedException`, dataSync 시간 제한, API 36):
  10-05 23:26 → 10-06 06:59:59/07:00:00(부팅 직후 2연속) → 07:30 → 08:30 → 10:01.
  모두 `IpRotationService.onCreate:53`의 `startForeground()`.
- 원인: `SpotShiftApp.onCreate` + `BootReceiver`가 백그라운드에서 `startForegroundService()` 호출 (Android 12+ 금지).
  + 스케줄러가 프로세스 내 코루틴 `delay` 루프라 프로세스 사망 = 스케줄 소멸. `START_STICKY` 재시작도 백그라운드라 크래시 반복.
- 확정 제외: 배터리(whitelist 등록됨), Shizuku(데몬 정상·`exit=0`), 셀룰러 관문(Wi-Fi OFF라 통과).
- 결정: 주기 하한 15분 허용, `IpRotationService` 완전 이관 (상시 알림 제거).

## 2. 목표

- 프로세스 사망·재부팅 후에도 다음 주기에 자동 변경이 실행된다.
- 백그라운드 FGS 크래시 0건. `logcat AndroidRuntime` 무결.

## 3. 설계

1. **의존성**: `androidx.work:work-runtime-ktx` 추가 (`android/app/build.gradle.kts`).
2. **신규 `scheduler/RotationWorker.kt : CoroutineWorker`**
   - 기존 `RotationScheduler.runSpeedGatedRotation` 로직 이관: 셀룰러 관문 → `RotationEngine.rotate` → 속도 검증(최대 재변경) → 기록.
   - 실행 중 `setForeground()`로 승격 (30~60초 로테이션 보호용, 상시 알림 아님).
   - 상태 알림은 기존 채널(`spotshift_status`)에 직접 발행.
3. **주기 등록** (`PeriodicWorkRequest`, `ExistingPeriodicWorkPolicy.UPDATE`)
   - 간격 = `config.intervalMinutes.coerceAtLeast(15)` (15분 미만 저장값은 강제 올림).
   - 등록 지점: `SpotShiftApp.onCreate` (기존 FGS 시작 대체) + `BootReceiver` (기존 서비스 시작 대체, Shizuku 유도 알림 유지) + 주기 변경 시 재등록.
4. **수동 경로**: 홈 버튼·퀵타일·Mac 원격(`EXTRA_AUTOROTATE`)은 `OneTimeWorkRequest` expedited 1회 실행으로 교체.
5. **삭제/축소**: `IpRotationService` 삭제, `RotationScheduler`는 Worker 내부로 흡수 (순수 헬퍼만 남기거나 삭제).
   `AndroidManifest`: FGS service 선언 + `FOREGROUND_SERVICE_DATA_SYNC` 권한 제거 (미사용 시). `RECEIVE_BOOT_COMPLETED` 유지.
6. **관측성**: Prefs에 `lastScheduledAt`/`nextRunAt` 추가 → 홈 "다음 변경 예상"을 실측 기준으로 표시 (Periodic은 구간 내 실행이라 ±오차 명시).

## 4. 검증 (S22)

```
./gradlew assembleDebug --no-daemon && adb install -r app-debug.apk
adb shell dumpsys jobscheduler | grep -i spotshift   # 주기 작업 등록 확인
adb shell am kill com.borasarang.spotshift           # 프로세스 킬 → 다음 주기 실행·기록 확인
adb shell "logcat -d -s AndroidRuntime:E"             # 크래시 0건
재부팅 테스트는 실기회에서 수동 (테더 세션 보호로 원격 재부팅 불가)
```

## 5. 리스크

- Periodic 실행 시각은 구간 내 유동적 → "예상 시각"은 근사치로 표기 변경.
- Doze에서 지연 가능 (예외 등록済라 정상 범위). Shizuku 바인더는 Worker 컨텍스트에서도 유효 (동일 UID).

## 6. T-30 전 단계 진행 표시 (2026-10-08)

- `RotationPhase.SPEED_CHECKING` 추가 — `measure()` 전 `RotationEngine.notifySpeedChecking()` 호출 (수동 `HomeViewModel`·자동 `RotationWorker` 동일).
- 표시: 홈 `RotationProgress` + Worker 포그라운드 알림 + 속도탭 `rotationText`에 `속도 측정 중`.

## 7. T-31 핫스팟 안내 개선 (2026-10-08, 기기 설치 전)
- 설정 `핫스팟 자동 켜기`→`핫스팟 꺼짐 안내` (API 36 자동 ON 불가, 안내만 수행).
- `isHotspotEnabled(): Boolean?` — 실패시 null, 호출처는 `== false` 확정일 때만 안내·이전 표시 유지.
- `restartHotspot` ERROR 폐기 (TETHER_PRIVILEGED 차단 데드코드 삭제).
- 재시도 `attempt/total` 설정 연동 + 데이터 재연결 문구 통일.

## 8. T-32/T-33 연동 계약 v1 (2026-10-08)

- `docs/PLUGIN_CONTRACT.md` — RelayConsole 플러그인 탭용. 명찰·`autorotate` 호출·`[REMOTE]` 로그·ON/OFF AND·버전 규칙.
- 구현: manifest 명찰 3키 + `PluginProbeReceiver` + `RotationConfig.pluginAllowed` + 설정 `연동 허용` + OFF시 `[REMOTE] 거부됨 (연동 OFF)`.
- S22 실검증: force-stop 상태에서 프로브 응답 `[PLUGIN] version=1 actions=autorotate logTag=SpotShift allowed=true`, 크래시 0.
