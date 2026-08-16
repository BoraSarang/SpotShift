# PLAN_v1.0_android.md — SpotShift (핫스팟 IP 변환기)

> 버전: v1.0 (2026-08-16) · 플랫폼: Android · 작성자: BoRaSaRang
> 공통 규칙: `~/.config/opencode/AGENTS.md` (v2.1.0-common) + `AGENTS.android.md` 참조

## 1. 개요

- **앱 이름**: SpotShift (핫스팟 IP 변환기)
- **패키지**: `com.borasarang.spotshift`
- **목적**: Android 기기를 핫스팟으로 사용하면서 일정 주기마다 IP를 자동 변경하고, 모바일 네트워크·핫스팟을 자동 복원하는 네트워크 제어 앱
- **차별화**: "핫스팟 연결 유지 + IP 변경 + 자동 복원"을 한 번에 수행 (경쟁사는 각각의 기능만 제공)
  - IP 변경 후 SSID/비밀번호 완벽 복원
  - 연결 기기 자동 재연결 유도 UX
  - 스마트 스케줄링 (배터리 잔량 / 시간대 / 네트워크 품질 조건부 실행)

## 2. 결정 사항 (확정)

| 항목 | 결정 | 근거 |
|---|---|---|
| 권한 방식 | **Shizuku 기반** (무루트) | 리서치: 루트 없이 핫스팟 제어 불가. Delta 앱이 Shizuku로 해결 |
| IP 변경 전략 | **모바일 데이터 재연결 우선 + 에어플레인 폴백** | S22 실검증: `svc data disable/enable`로 IP 변경 성공(175.223.x→110.70.x) + 핫스팟 TetheredState 유지. 에어플레인은 핫스팟이 꺼지고 API 36에서 자동 복원 불가(TETHER_PRIVILEGED)하므로 폴백으로 격하 |
| IP 검증 | ipify.org 공인 IP 조회 (IPv4) + 변경 여부 비교 | 변경 성공/실패 판정 필수 |
| 테스트 기기 | **삼성 갤럭시 S22** (USB 디버깅) | 실기기에서 핫스팟/IP 실검증 가능 |
| UI | Material 3 + Dark OLED 기본 (VPN 도구 패턴) | ui-ux-pro-max 리서치: VPN & Privacy Tool 카테고리 |
| 스택 | Kotlin + Jetpack Compose, AGP 8.5.2, Gradle 8.9, minSdk 26, targetSdk 35 | SmartSeller 패턴 재사용 |

> **API 36 제약 (S22 실검증, 2026-08-16)**:
> - `am broadcast AIRPLANE_MODE` 차단 (exit=255) → `cmd connectivity airplane-mode`로 대체 (성공)
> - TetheringManager 리플렉션으로 핫스팟 재시작 불가 — 생성자 변경 + `TETHER_PRIVILEGED` 권한 필수 (Error 14), `cmd wifi start-softap`도 shell(uid 2000) 거부
> - **해결**: `svc data disable/enable`로 모바일 업스트림만 재연결 → 핫스팟 무중단 IP 변경 성공
>
> **v0.2 요구사항 (2026-08-16 추가)**:
> 0. 셀룰러 모드에서만 동작 (Wi-Fi 모드 무관, 이전 IP 체크도 셀룰러 전용)
> 1. 변경 주기 30분~12시간 (30분 단위), 기본 2시간
> 2. 주기 내 IP 변경 시 자동 변경 스킵 (이동으로 IP가 바뀌면 변경 불필요)
> 3. 기록 초기화 기능
> 4. 알림: IP 변경 시작 / 완료(x→y) 푸시
> 5. 옵션: IP 변경 여부 + 핫스팟(테더링) 자동 켜기
>    - API 36에서 프로그램적 핫스팟 ON 불가 → "자동 켜기" 시도 실패 시 설정 화면 안내 (ACTION_TETHERING_SETTINGS)
>
> **v0.3 요구사항 (2026-08-16 추가 — 홈 화면 디자인 개선)**:
> 1. 홈 "다음 변경 2:00:00" 모호성 해소 — 남은 시간 + **다음 변경 예상 시각 병기** ("다음 변경까지 · 13:38 예정"). lastRotationAt은 Prefs 구독 (재시작/자동 변경에도 유지)
> 2. "IP 변경 시작" 클릭 시 **중간 단계 표시** (RotationPhase 기반: 현재 IP 확인 중 / 모바일 데이터 재연결 중 / IP 변경 확인 중 / 재시도 중 (n/3) / 에어플레인 모드 전환)
> 3. 설정 탭 하단 **문의 섹션**: 제작자 BoRaSaRang / 문의 메일 (mailto) / GitHub (브라우저) / 앱 버전

## 3. 아키텍처

```
android/app/src/main/java/com/borasarang/spotshift/
├── MainActivity.kt          # Compose 진입점
├── DebugLogger.kt           # 표준 로거 (Logcat, 에러코드 포함)
├── ErrorMessages.kt         # error_message_ko.json 매핑
├── core/
│   ├── ShizukuManager.kt    # Shizuku 연결/권한/명령 실행
│   ├── DataController.kt    # 모바일 데이터 재연결 (svc data) — 우선 방법
│   ├── AirplaneController.kt# 에어플레인 토글 (cmd connectivity airplane-mode)
│   ├── HotspotController.kt # 핫스팟 상태 조회 (API 36 재시작 불가)
│   ├── IpVerifier.kt        # ipify 공인 IP 조회/검증/재시도
│   └── RotationEngine.kt    # IP 변경 시나리오 오케스트레이션
├── scheduler/
│   ├── RotationScheduler.kt # 주기/시각 기반 스케줄링
│   └── Conditions.kt        # 배터리/시간대/네트워크 품질 조건
├── service/
│   └── IpRotationService.kt # 포그라운드 서비스 + 알림
├── data/
│   ├── Prefs.kt             # DataStore(SharedPreferences) 설정
│   └── Models.kt            # RotationRecord 등
└── ui/
    ├── theme/               # Color.kt, Type.kt, Theme.kt
    ├── components/          # StatusCard, CountdownGauge, ConditionChip 등
    ├── home/                # HomeScreen (대시보드)
    ├── history/             # 기록 탭
    └── settings/            # 설정 탭
```

## 4. IP 변경 시나리오 (핵심 로직)

```
[주기 도래 또는 수동 실행]
  → [셀룰러 모드 체크] — Wi-Fi 연결 시 스킵 (v0.2)
  → [주기 내 IP 변경 체크] — 마지막 IP 변경 후 주기 미경과 시 스킵 (v0.2)
  → 조건 검사 (배터리/시간대/품질) — 실패 시 스킵 로그
  → 현재 공인 IP 조회 (ipify)
  → [모바일 데이터 재연결 우선] (S22 실검증: 핫스팟 무중단 IP 변경)
      A. svc data disable (모바일 데이터 OFF)
      B. 대기 5s
      C. svc data enable (모바일 데이터 ON)
      D. 네트워크 복구 대기 (최대 20s)
      E. 공인 IP 재조회 → 변경 확인
      F. 성공 시: 핫스팟 상태 확인 (TetheredState 유지 확인) → 완료 로그 + 알림(x→y)
      G. 실패 시: 재시도 (기본 2회, 간격 10s)
  → [폴백: 에어플레인 사이클] (데이터 재연결 2회 실패 시 — 핫스팟이 꺼짐)
      A. cmd connectivity airplane-mode enable
      B. 대기 N초 (기본 5s)
      C. cmd connectivity airplane-mode disable
      D. 모바일 네트워크 복구 대기 (최대 20s)
      E. IP 재조회 → 변경 확인
      F. 핫스팟 꺼짐 감지 시: 옵션(핫스팟 자동 켜기) ON → 설정 화면 안내 (API 36 자동 켜기 불가)
  → 결과 기록 (성공/실패/재시도 횟수/이전IP/새IP/소요시간) + 시작/완료 알림
```

> **제거됨**: 핫스팟 재시작 폴백 — API 36에서 TetheringManager 리플렉션/`cmd wifi start-softap` 모두
> `TETHER_PRIVILEGED`(Error 14)로 차단되어 프로그램적 핫스팟 재시작 불가 (S22 실검증, 2026-08-16)

## 5. 구현 단계 (T-번호)

| T | 작업 | 상태 |
|---|---|---|
| T-1 | 문서 골격 (PLAN/TODO/AGENTS.android/DESIGN/PRD/error_message_ko) | 진행중 |
| T-2 | Gradle 프로젝트 + Compose + DebugLogger + 테마 | 대기 |
| T-3 | Shizuku 연결 + 에어플레인 토글 (S22 검증) | 대기 |
| T-4 | IP 검증 + 재시도 + 핫스팟 재시작 폴백 | 대기 |
| T-5 | 스케줄러 (주기 + 조건부 실행) | 대기 |
| T-6 | 포그라운드 서비스 + 알림 + 퀵 세팅 타일 | 대기 |
| T-7 | UI 3탭 + 아이콘 (원형+Wi-Fi+순환 화살표) | 대기 |
| T-8 | S22 실검증 + DoD + CHANGELOG + 세션 로그 | 대기 |

## 6. 테스트 계획 (TC-번호)

| TC | 시나리오 | 기기 |
|---|---|---|
| TC-01 | 모바일 데이터 재연결 → 공인 IP 변경 + 핫스팟 유지 확인 (성공 케이스) | S22 ✅ |
| TC-02 | 통신사 동일 IP 재할당 → 재시도 로직 동작 | S22 |
| TC-03 | 데이터 재연결 실패 → 에어플레인 폴백 → IP 변경 + 핫스팟 복원 안내 | S22 |
| TC-04 | 배터리 20% 이하 조건 → 스킵 로그 확인 | S22 |
| TC-05 | 포그라운드 서비스 유지 + 알림 표시 (앱 종료 후) | S22 |
| TC-06 | Quick Settings 타일 → 시작/정지 | S22 |
| TC-07 | 기록 탭: 성공/실패/재시도 이력 저장 + 초기화 (v0.2) | S22 |
| TC-08 | 클라이언트 기기 자동 재연결 안내 카드 표시 | S22 |
| TC-09 | Wi-Fi 모드에서 자동 실행 스킵 (v0.2) | S22 |
| TC-10 | 주기 내 IP 변경 → 스킵 로그 (v0.2) | S22 |
| TC-11 | 알림: IP 변경 시작/완료(x→y) 푸시 (v0.2) | S22 |

## 7. 에러코드 (E-AND-*)

| 코드 | 메시지 (error_message_ko.json) |
|---|---|
| E-AND-NET-0001 | 공인 IP 조회에 실패했습니다. 네트워크를 확인해 주세요. |
| E-AND-NET-0002 | IP 변경에 실패했습니다. 재시도 횟수를 초과했습니다. |
| E-AND-NET-0003 | 핫스팟 상태 확인에 실패했습니다. 핫스팟 상태를 확인해 주세요. |
| E-AND-PERM-0001 | Shizuku 권한이 필요합니다. 설정 가이드를 확인해 주세요. |
| E-AND-PERM-0002 | Shizuku가 실행 중이 아닙니다. Shizuku를 시작해 주세요. |
| E-AND-SCH-0001 | 스케줄 조건을 충족하지 못해 이번 회차를 건너뜁니다. |
| E-AND-SRV-0001 | 백그라운드 서비스 시작에 실패했습니다. |
| E-AND-STOR-0001 | 기록 저장에 실패했습니다. |

## 8. 롤백 계획

- git revert + `./gradlew clean assembleDebug` 재빌드
- S22에서 `adb uninstall com.borasarang.spotshift`
- 설정 데이터: DataStore 삭제 → 앱 재설치로 초기화
- Shizuku 영향 없음 (시스템 변경 없음, 에어플레인 토글만 수행)

## 9. 성능 예산

| 지표 | 목표 |
|---|---|
| 콜드 스타트 | ≤2.0s |
| IP 변경 사이클 (성공 시) | ≤30s (데이터 재연결 5s + 복구 20s + 조회) |
| 메모리 (포그라운드 서비스) | ≤80MB |
| 알림 반영 시간 | ≤500ms |

## 10. DoD 체크리스트 (v2.1 축약)

- [ ] AGENTS.android.md 작성 + 환경 기록
- [ ] build: `./gradlew assembleDebug` 성공
- [ ] S22 설치 + DebugLogger 로그 ERROR 0개
- [ ] TC-01~08 실행 결과 기록
- [ ] error_message_ko.json + CHANGELOG.md 업데이트
- [ ] 세션 로그 8줄 요약 저장
