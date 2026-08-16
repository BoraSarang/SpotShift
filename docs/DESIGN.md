# DESIGN.md — SpotShift 기술 설계

> 버전: v1.0 (2026-08-16) · 플랫폼: Android · 상세: docs/plans/PLAN_v1.0_android.md

## 1. 기술 스택

| 계층 | 선택 | 이유 |
|---|---|---|
| 언어 | Kotlin 2.0.21 | Android 표준 |
| UI | Jetpack Compose (BOM 2024.09.03) + Material 3 | 모던 대시보드, 다크 테마 용이 |
| 빌드 | AGP 8.5.2 / Gradle 8.9 (wrapper) | SmartSeller 검증 구성 |
| 권한 | Shizuku API (rikka.shizuku) | 무루트 시스템 API 접근 |
| IP 조회 | ipify.org (https://api.ipify.org?format=json) | 무료 공개 API, IPv4 |
| 네트워크 | OkHttp 4.12.0 | 표준 |
| JSON | Gson 2.11.0 | 표준 |
| 저장 | DataStore (또는 SharedPreferences) | 설정 + 기록 (가볍게) |

## 2. 컴포넌트 설계

### 2.1 ShizukuManager
- 역할: Shizuku 연결 상태 확인, 권한 요청, 셸 명령 실행
- 핵심: `Shizuku.pingBinder()`, `Shizuku.requestPermission()`, `Shizuku.newProcess()`
- 명령 실행: `settings put global airplane_mode_on <0|1>` + `am broadcast -a android.intent.action.AIRPLANE_MODE --ez state <true|false>`
- 방어: Shizuku 미연결 시 `E-AND-PERM-0001/0002` 반환

### 2.2 AirplaneController
- 에어플레인 ON → 대기(설정) → OFF → 네트워크 복구 대기(최대 20s)
- 상태 확인: `Settings.Global.AIRPLANE_MODE_ON` 읽기 (앱 권한으로 읽기 가능)

### 2.3 HotspotController (폴백)
- Shizuku + 리플렉션으로 `TetheringManager` 접근
- `startTethering/stopTethering` (리플렉션, 버전별 시그니처 분기)
- SSID/비밀번호: `WifiManager.getWifiApConfiguration()` (리플렉션) 읽기 → 복원 비교
- 실패 시 `E-AND-NET-0003`

### 2.4 IpVerifier
- `GET https://api.ipify.org?format=json` → `{ "ip": "1.2.3.4" }`
- 이전 IP 보관 → 변경 여부 판정 (동일/상이/조회실패)
- 조회 실패 시 `E-AND-NET-0001`

### 2.5 RotationEngine (오케스트레이터)
- 시나리오: 조건 → 현재IP → 에어플레인 시도 → 검증 → 재시도 → 폴백(핫스팟) → 검증 → 기록
- 상태 머신: IDLE → CHECKING → ROTATING → VERIFYING → SUCCESS / RETRYING / FALLBACK / FAILED
- 모든 단계 DebugLogger 로그 + 에러코드

### 2.6 RotationScheduler
- 주기 모드: 분(5/10/15/30/60) / 시각(시간대 구간) / 특정 시각
- 조건 평가: 배터리 잔량(`BatteryManager`), 시간대, 네트워크 품질(`ConnectivityManager` + `WifiManager`)
- 조건 실패 시 `E-AND-SCH-0001` 로그 + 스킵

### 2.7 IpRotationService (포그라운드)
- `startForeground` + 고유 알림 채널 ("SpotShift 실행 중")
- 알림: 상태/다음 변경까지 시간/IP 변경 결과 (맥락 포함 메시지)
- Quick Settings 타일 (TileService) → 시작/정지

## 3. 데이터 모델

```kotlin
data class RotationRecord(
  val id: Long,          // autoincrement
  val timestamp: Long,   // 실행 시각
  val oldIp: String?,    // 이전 IP (조회 실패 시 null)
  val newIp: String?,    // 새 IP
  val changed: Boolean,  // IP 변경 성공 여부
  val method: String,    // AIRPLANE | HOTSPOT_RESTART | NONE
  val retryCount: Int,   // 재시도 횟수
  val durationMs: Long,  // 소요 시간
  val errorCode: String? // 실패 시 에러코드
)

data class RotationConfig(
  val intervalMinutes: Int,     // 0 = 특정 시각 모드
  val scheduleWindowStart: Int?, // 24h 시각 (예: 22)
  val scheduleWindowEnd: Int?,
  val minBatteryPercent: Int,   // 기본 30
  val minSignalDbm: Int,        // 기본 -110
  val retryCount: Int,          // 기본 2
  val airplaneWaitSec: Int,     // 기본 5
  val fallbackEnabled: Boolean, // 기본 true
  val enabled: Boolean          // 마스터 토글
)
```

## 4. 디자인 시스템 (ui-ux-pro-max 리서치 결과)

- **패턴**: VPN & Privacy Tool (NordVPN류) — Connection-focused 대시보드
- **스타일**: Minimalism & Swiss Style + Dark Mode (OLED)
- **컬러 토큰** (Material 3):
  - 배경 #0F172A / 카드 #1B2336 / 텍스트 #F8FAFC / 보조 #94A3B8
  - 성공 #22C55E / 실패 #EF4444 / 대기 #F59E0B / 악센트 #38BDF8
- **타이포**: Inter 계열 (300~700) — Compose 기본 SansSerif + weight 체계
- **홈**: 상태 카드 + 원형 카운트다운 게이지 + 대형 시작/정지 버튼 + 조건 칩
- **아이콘**: 원형 + Wi-Fi 파장 + 순환 화살표 (앱 리소스로 제작)

## 5. 에러 코드 매핑

| 코드 | 화면 표시 |
|---|---|
| E-AND-NET-0001 | "공인 IP 조회에 실패했습니다. 네트워크를 확인해 주세요." |
| E-AND-NET-0002 | "IP 변경에 실패했습니다. 재시도 횟수를 초과했습니다." |
| E-AND-NET-0003 | "핫스팟 재시작에 실패했습니다. 핫스팟 상태를 확인해 주세요." |
| E-AND-PERM-0001 | "Shizuku 권한이 필요합니다. 설정 가이드를 확인해 주세요." |
| E-AND-PERM-0002 | "Shizuku가 실행 중이 아닙니다. Shizuku를 시작해 주세요." |
| E-AND-SCH-0001 | "스케줄 조건을 충족하지 못해 이번 회차를 건너뜁니다." |
| E-AND-SRV-0001 | "백그라운드 서비스 시작에 실패했습니다." |
| E-AND-STOR-0001 | "기록 저장에 실패했습니다." |

## 6. 권한 정의

| 권한 | 용도 |
|---|---|
| INTERNET | ipify 조회 |
| ACCESS_NETWORK_STATE | 네트워크 상태/품질 |
| ACCESS_WIFI_STATE | Wi-Fi 상태 (핫스팟 감지) |
| POST_NOTIFICATIONS | 알림 (Android 13+) |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC | 상시 서비스 |
| WRITE_SETTINGS | 에어플레인 상태 읽기 (토글은 Shizuku) |
| (Shizuku) | settings put global + TetheringManager |
