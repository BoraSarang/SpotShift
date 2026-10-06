# AGENTS.md — SpotShift (핫스팟 IP 자동 변경기)

Android 단독 네이티브 앱 (Kotlin + Compose, 무루트/Shizuku, 서버 없음). minSdk 26 / targetSdk 35.
상세 환경·실기기 절차는 `AGENTS.android.md`, 작업 상태는 `docs/TODO.md` + `docs/CHANGELOG.md`가 진실원천 (이 파일에 상태 복제 금지).

## 명령 (Gradle 루트는 `android/`)

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
cd android && ./gradlew assembleDebug --no-daemon    # 디버그 빌드 (wrapper 8.9 필수, system gradle 금지)
cd android && ./gradlew assembleRelease --no-daemon  # 릴리즈 (android/local.properties의 keystore.* + keystore/release.jks 필요, 둘 다 gitignore)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.borasarang.spotshift/.MainActivity
adb shell "logcat -d -s SpotShift:*"       # 앱 로그 (DebugLogger, TAG=SpotShift)
adb shell "logcat -d -s AndroidRuntime:E"   # 크래시 확인
```

- 루트 `./build_and_run.sh`는 위 절차의 디스패처. `scripts/`는 비어 있음.
- 테스트·CI·lint 없음. 검증 = `assembleDebug` 성공 + S22 설치 + logcat ERROR 0.

## 구조 (`android/app/src/main/java/com/borasarang/spotshift/`)

- 진입: `SpotShiftApp.kt` (토글 ON이면 주기 작업 등록) → `MainActivity.kt` → 수동 변경은 `HomeViewModel.manualRotate` (인프로세스)
- 핵심: `scheduler/RotationWorker.kt` (주기 틱→expedited 1회 실행→`core/RotationEngine.kt` 오케스트레이션) + `scheduler/RotationSchedule.kt` (등록/취소 단일 진입점) + `scheduler/Conditions.kt` (셀룰러만 실행 관문, 나머지는 기록용)
- 수단 우선순위 (S22 실측 확정): `core/DataController.kt` (`svc data` 재연결) → `core/AirplaneController.kt` (`cmd connectivity airplane-mode`) 폴백. 검증은 `core/IpVerifier.kt` (ipify) + `core/SpeedChecker.kt` (변경 후 측정, 기준 미달 시 최대 3회 재변경)
- 상태: `data/Prefs.kt` (DataStore, 키별 `update*` — 통째 save 금지, 콜드스타트 fresh read) / `receiver/BootReceiver.kt` (재부팅 복원) / `ui/` (Compose Material3 4탭: home/history/speed/settings)
- 루트 `index.html`은 GitHub Pages 랜딩, 앱 코드 아님.

## S22 / API 36 제약 (재발 방지)

- `am broadcast AIRPLANE_MODE` (exit=255), TetheringManager 리플렉션·`cmd wifi start-softap` (`TETHER_PRIVILEGED`) 전부 차단 → 위 우회 명령만 사용.
- 핫스팟 SSID/비밀번호 영구 변경 금지 (복원만). 일반 권한으로 핫스팟 직접 제어 시도 금지.
- 삼성 One UI는 백그라운드 킬 가능 → 포그라운드 서비스 + 배터리 예외 안내 유지. 상태알림 채널은 `setShowBadge(false)` (NotificationChannel 전용 API).

## 규칙

- 응답·문서 본문은 한국어. 코드 식별자·로그·커밋 type은 영어 허용.
- 코딩 전 `docs/plans/PLAN_v*.md` + `docs/TODO.md`(T-번호) 확인, 수정 후 둘 다 갱신. 세션 로그는 `.agent/session-*.md` (gitignore, 커밋 금지).
- 로그는 `DebugLogger` 경유만 (`println`/`print` 금지). 에러는 `E-AND-{NET|PERM|SCH|SRV|STOR}-NNNN` 형식 + `error_message_ko.json`(루트)에 문구 추가, `DebugLogger.e(..., errorCode)`로 기록.
- 릴리즈 키·`local.properties`·`*.jks`·`.env` 커밋 금지. 외부 API 없음 (ipify 공개 API 제외).
- 브랜치 `feat/{name}`·`fix/bd-{id}`, 커밋 `type(android): subject`.
