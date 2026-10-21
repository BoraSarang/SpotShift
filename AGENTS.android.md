# AGENTS.android.md — SpotShift Android 가이드

> 공통 규칙: `~/.config/opencode/AGENTS.md` (v2.1.0-common) + `AGENTS.md`(프로젝트 확장) 참조

## 1. 환경 (검증 완료)

- **테스트 기기: 삼성 갤럭시 A90 (SM-A908N, Android 12 / API 31) 단독** — 무선 ADB `192.168.86.30:5555`. 2026-10-11부터 **S22는 검증 대상 제외** (과거 기록만 참고)
- JDK: `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` (brew openjdk@17)
- Gradle: wrapper 8.11.1 / AGP 8.9.2 / Kotlin 2.1.21 (반드시 `./gradlew` 사용)
- Android SDK: `~/Library/Android/sdk` (`android/local.properties`에 sdk.dir 기록)
- (보조) AVD 시스템 이미지: android-37.1 / google_apis_ps16k / arm64-v8a — 필요 시 생성 `spotshift_test`
- **기기 2대가 동시에 연결될 수 있음** (A90 86.30 / S22 86.49) → 모든 adb 명령에 `-s 192.168.86.30:5555`를 명시한다 (생략 시 `more than one device` 오류)

## 2. 빌드/실행 명령

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
cd android && ./gradlew assembleDebug --no-daemon
A=192.168.86.30:5555                            # A90 단독 (기기 2대 연결 시 -s 필수)
adb -s $A install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $A shell am start -n com.borasarang.spotshift/.MainActivity
adb -s $A shell "logcat -d -s SpotShift:*"          # 앱 로그 (DebugLogger)
adb -s $A shell "logcat -d -s AndroidRuntime:E"     # 크래시 확인
adb -s $A exec-out screencap -p > /tmp/spotshift.png # 스크린샷
```

## 3. 권한 구조 (Shizuku 기반)

- **Shizuku** (무루트): ADB 권한으로 `settings put global airplane_mode_on` + `AIRPLANE_MODE` 브로드캐스트 실행, TetheringManager 리플렉션 호출
- Shizuku 시작: A90 연결 상태에서 `adb`로 시작 가능 (무선 디버깅 유지 시 재부팅 후에도 무선). 앱 내 가이드 제공
- 앱 권한: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `WRITE_SETTINGS`(airplane용, 최소화)

## 4. IP 변경 메커니즘

1. 에어플레인 토글 (Shizuku) → 모바일 IP 갱신 시도
2. ipify.org 공인 IP 조회 → 변경 검증
3. 실패 시 재시도 (기본 2회)
4. 최종 폴백: 핫스팟 재시작 (TetheringManager) → SSID/비밀번호 자동 복원
5. 통신사 정책상 동일 IP 재할당 가능 → 성공/실패 로그 기록, 사용자에게 명시

## 5. 주의사항

- **삼성 One UI**: 백그라운드 앱 종료 가능 → 포그라운드 서비스 + 배터리 예외 설정 안내 UI 필요
- **USB 설치 확인 팝업**: 개발자 옵션 → "USB로 설치한 앱 확인" 끄면 자동 설치
- **에어플레인 토글 후 복구 대기**: 모바일 네트워크 재연결 최대 20s 대기
- 에어플레인 토글 중에는 인터넷 단절 → 순차 대기 필수
- 시크릿 없음 (외부 API 없음, ipify만 공개 API)
- Release 빌드: `if (BuildConfig.DEBUG)`로 DebugLogger 컴파일 제거

## 6. 실기기 검증 기록

- 검증 기기: **A90 (SM-A908N, Android 12 / API 31) 단독** (2026-10-11~). S22 기록은 이력.
- v0.6~v0.7 (T-37~T-44): DUN 탭·Shizuku 동선·온도/전원 가드·속도 감시·상태 스크립트 — A90 실측 (`docs/CHANGELOG.md`, `docs/plans/PLAN_v0.7_android.md` TC-12~17)
- v0.5.1 (T-45): 수동 변경의 자동 토글 결합 제거 — **A90 실측 통과 (TC-18, 2026-10-17)**: OFF 상태 유지 + 주기 미등록 + IP 변경/속도 달성 + ERROR 0

## 7. 금지사항

- `print()`/`println()` 직접 호출 금지 → `DebugLogger` 경유
- 일반 앱 권한으로 핫스팟 직접 제어 시도 금지 (시스템 권한, 리플렉션/Shizuku 필수)
- 사용자 기기의 Wi-Fi/핫스팟 설정 영구 변경 금지 (SSID/비번은 원복만)
