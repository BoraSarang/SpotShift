# REDESIGN_v2.md — SpotShift UI 리디자인 (MD3 Expressive, 다크/라이트)

> 기준: `material-3` 스킬 (Material Design 3 / Compose-first) · 플랫폼: Android
> 목업: `docs/design/mockup_v2.html` (5탭 × 라이트/다크 = 10프레임, 실제 앱 데이터)
> 상태: **T-46 목업 확정 · T-47 토큰/테마 반영 완료 (A90 TC-19~23 통과, 2026-10-21)** — T-48~T-51 승인 대기
> 원칙: 회전 코어(`RotationEngine`/`DataController`/`AirplaneController`)·Shizuku·Worker 로직은 **불변**, UI 계층만 교체

## 1. 문제 정의 (현재 UI)

| 항목 | 현재 | 문제 |
|---|---|---|
| 테마 | `isSystemInDarkTheme()` 고정 2종, 정적 hex (`Color.kt`) | 시맨틱 role 미사용 → 컴포넌트가 토큰 밖 색을 직접 참조, 동적 색상 불가 |
| 색 | `surface`/`surfaceVariant`(deprecated 계열) + `SurfaceMuted` 직접 사용 (`CountdownGauge`) | 다크/라이트 대비 검증 불가, 계층(tone) 표현 없음 |
| 타이포 | 7개 스타일 임의 정의, letterSpacing 산발 | MD3 type scale 미준수, 숫자 정렬(tabular) 없음 |
| 형태 | 카드 20dp/16dp 혼용, 버튼 `RoundedCornerShape(24.dp)` 추정 | shape 토큰 아님, 화면마다 다름 |
| 레이아웃 | `Scaffold` 기본 inset, 24dp 고정 패딩 | edge-to-edge 미적용, 8dp 그리드 아님 |
| 내비 | `NavigationBar` 5탭 (라이트/다크 미검증) | 큰 화면(폴더블/태블릿) 대응 없음 |
| 테마 선택 | 없음 (시스템만) | 사용자가 다크/라이트 고정 불가 |

## 2. 디자인 방향

- **컨셉**: "네트워크 컨트롤 콘솔" — 상태(IP·핫스팟·가드)를 한눈에, 액션은 화면당 1개.
- **시드**: `#0E7BC4` (기존 스카이/네이비 정체성 유지). 라이트 `primary #00639B`, 다크 `#93CCFF`.
- **동적 색상**: API 31+ (`A90 = API 31`) 에서 `dynamicLightColorScheme`/`dynamicDarkColorScheme` 우선, 정적 팔레트는 폴백 + 사용자 토글.
- **표면 계층**: 그림자 대신 tone(`surface-container-low/high`)으로 깊이 표현. 다크는 순흑 대신 `#0F1318`(OLED 번인·대비 균형).
- **상태색**: 성공/경고는 MD3 `error`가 아닌 **커스텀 success/warning role** (성공 ≠ 오류). 색 + 아이콘 + 텍스트 **3중 부호화**.
- **숫자 중심**: 카운트다운·IP·속도는 tabular-nums + mono 계열로 자리수 흔들림 제거.

## 3. 색상 토큰

### 3.1 라이트

| Role | Hex | Role | Hex |
|---|---|---|---|
| primary | `#00639B` | on-primary | `#FFFFFF` |
| primary-container | `#CDE5FF` | on-primary-container | `#001D33` |
| secondary | `#4F616E` | secondary-container | `#D2E5F4` |
| tertiary | `#7A5900` | tertiary-container | `#FFDF9C` |
| success | `#146C2E` | success-container | `#A8F5BD` |
| warning | `#8A5100` | warning-container | `#FFDDB3` |
| error | `#BA1A1A` | error-container | `#FFDAD6` |
| surface | `#F7F9FF` | on-surface | `#181C20` |
| surface-container-lowest | `#FFFFFF` | surface-container-low | `#F1F4FA` |
| surface-container | `#EBEEF4` | surface-container-high | `#E5E8EE` |
| surface-container-highest | `#DFE2E8` | on-surface-variant | `#41474E` |
| outline | `#71787F` | outline-variant | `#C1C7CE` |
| inverse-surface | `#2D3135` | inverse-on-surface | `#EEF1F6` |

### 3.2 다크

| Role | Hex | Role | Hex |
|---|---|---|---|
| primary | `#93CCFF` | on-primary | `#003354` |
| primary-container | `#00497A` | on-primary-container | `#CDE5FF` |
| secondary | `#B6C9D8` | secondary-container | `#374956` |
| tertiary | `#F5BF48` | tertiary-container | `#5C4200` |
| success | `#8BD99F` | success-container | `#00531F` |
| warning | `#FFB95C` | warning-container | `#653D00` |
| error | `#FFB4AB` | error-container | `#93000A` |
| surface | `#0F1318` | on-surface | `#DFE3E8` |
| surface-container-lowest | `#0A0E12` | surface-container-low | `#171C21` |
| surface-container | `#1B2025` | surface-container-high | `#252A30` |
| surface-container-highest | `#30353B` | on-surface-variant | `#BFC6CE` |
| outline | `#899098` | outline-variant | `#3F464D` |
| inverse-surface | `#DFE3E8` | inverse-on-surface | `#2B3036` |

> 대비: 본문 `on-surface`↔`surface` 15:1(라이트)/12:1(다크), 보조 텍스트 4.8:1 이상, `on-primary`↔`primary` 6:1 이상.
> 구현 후 WCAG 대비 계산으로 최종 검증 (본문 4.5:1, UI 경계 3:1).

### 3.3 사용 규칙

- `primary`: 주 액션(IP 변경 시작)·선택 상태·게이지 진행.
- `secondary-container`: 내비 선택 pill, 필터 칩 선택, 보조 버튼(tonal).
- `tertiary-container`: 속도/측정 계열 강조(현재는 배지·비교 카드에만).
- `success/warning/error` + `*-container`: 상태 전용. **텍스트 색은 항상 `on-*`**.
- `outline-variant`: 구분선. `outline`: 입력/칩 경계.
- 금지: `Color(0x...)` 직접 참조, `surfaceVariant`(deprecated), 컴포넌트 내부 하드코딩.

## 4. 타이포그래피

| 역할 | Mockup | Compose | 용도 |
|---|---|---|---|
| Display M | 45/52, 600, tabular | `displayMedium` | 카운트다운 1:47:38 |
| Display S | 36/44, 600 | `displaySmall` | 속도 2.42 |
| Headline M | 26/32, 600 | `headlineMedium` | 화면 제목(SpotShift·설정) |
| Title M | 16/22, 600 | `titleMedium` | 카드 제목·리스트 주 항목 |
| Body M | 14/21, 400 | `bodyMedium` | 본문·보조 설명 |
| Label M | 12/16, 500 +0.35 | `labelMedium` | 캡션·단위 |
| Label S | 11/14, 600 | `labelSmall` | 그룹 라벨 |

- 기본 서체: **Roboto** (Android 기본, MD3 표준). 한글은 시스템 폴백(Noto Sans CJK).
- 숫자: `fontFeatureSettings = "tnum"` 고정 (IP/속도/카운트다운).
- 모든 크기 `sp` — 사용자 글자 크기 설정 대응. 고정 높이 컨테이너 안에 텍스트 넣지 않기.

## 5. 형태 · 간격 · 모션

| 토큰 | 값 | 사용 |
|---|---|---|
| `extraLarge` | 28dp | 히어로 카드(상태·속도) |
| `large` | 20dp | 배너·비교 박스 |
| `medium` | 12dp | 내부 미디어·작은 컨테이너 |
| `full` | 999dp | 버튼·칩·스위치·배지·내비 pill |

- 간격: **8dp 그리드** — 화면 패딩 16, 카드 패딩 16, 카드 간 12, 섹션 간 24~32.
- 터치 영역: 최소 48dp (칩 32dp 높이라도 히트박스 48dp).
- 모션(Expressive): `MotionScheme.expressive()` — 스프링 기반 press/상태 전환. 화면 전환은 emphasized 계열 300~400ms.
- 게이지: `animateFloatAsState` 유지하되 1s 선형 → 스프링/`animateFloat` with `tween(1000, easing = LinearEasing)` 명시 (초침처럼).

## 6. 화면별 변경

| 화면 | 목업 | 핵심 변경 |
|---|---|---|
| 상태 | `?screen=home` | (1) 상태 카드 = 히어로(IP + 3지표: 최근 측정/기준/온도) (2) 오브 192dp + 예상 시각 pill (3) 주 액션 1개(56dp filled) (4) 조건 칩 1줄(배터리·RSRP·가드) (5) 최근 변경 카드 |
| 기록 | `?screen=history` | 필터 세그먼트(전체/성공/실패/속도), 날짜 그룹 카드, 성공·실패 이중 부호화, 시간은 보조행으로 이동 |
| 속도 | `?screen=speed` | 숫자 히어로(다운), 업로드·지연 2열, 변경 전/후 비교 카드, 측정 기록 리스트 |
| DUN | `?screen=dun` | Shizuku 배너(성공 톤), DUN 유지 스위치, APN 라디오 리스트 + DUN 포함 배지, 추가/원복 액션, 원복 가능 안내 |
| 설정 | `?screen=settings` | 그룹 카드(자동 변경/화면/가드/알림/연동·정보), 슬라이더 값 배지, **테마(시스템/라이트/다크) + 동적 색상 토글** |

> 신규: 설정 → 화면 → `테마`(SegmentedButton) + `배경화면 색상 사용`(Switch, API 31+ 미만은 비활성 + 사유 표시).

## 7. Compose 구현 매핑

```kotlin
// 1) 엣지투엣지 (MainActivity.onCreate)
enableEdgeToEdge()

// 2) 테마 (ui/theme/Theme.kt)
enum class ThemeMode { SYSTEM, LIGHT, DARK }          // Prefs.themeMode (키별 update*)
@Composable fun SpotShiftTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialExpressiveTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, motionScheme = MotionScheme.expressive(), content = content)
}
```

| 현재 | 변경 |
|---|---|
| `Color.kt` 정적 hex | `LightScheme`/`DarkScheme` (`lightColorScheme`/`darkColorScheme` 전 역할 지정) + success/warning은 `CompositionLocal` 또는 `SpotColors` 확장 |
| `SurfaceMuted` (게이지 트랙) | `MaterialTheme.colorScheme.surfaceContainerHighest` |
| `RoundedCornerShape(20.dp)` | `MaterialTheme.shapes.extraLarge` |
| `BottomNavigation` 고정 | `NavigationBar` + `NavigationRail`(≥600dp, `WindowSizeClass`) — A90은 compact만 실검증 |
| `Scaffold` 기본 inset | `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` + `Modifier.windowInsetsPadding` |
| 진행 표시 | `LinearProgressIndicator`/`CircularProgressIndicator` → Expressive `LoadingIndicator`(가능 시), 속도 측정 중 상태 |
| 필터/테마 선택 | `SegmentedButton` (`SingleChoiceSegmentedButtonRow`) |
| 다이얼로그 | `AlertDialog` 유지(User+ 작업) — 모양만 토큰화 |

> **버전 주의**: 현재 BOM `2024.09.03`. Expressive API(`MaterialExpressiveTheme`/`MotionScheme`)는 Material3 1.4+ 필요 → 구현 첫 단계에서 실제 사용 가능한 최신 BOM을 Gradle 메타데이터로 **확인 후** 고정한다 (추측 금지). 미지원이면 `MaterialTheme` + 정적 토큰으로 동일 목업 구현.

> **실측 결과 (T-47, 2026-10-21)**: BOM `2024.09.03` = **Material3 1.3.0** → `MaterialExpressiveTheme`·`MotionScheme`·`LoadingIndicator` **미제공**(컴파일 불가). 따라서 `MaterialTheme(colorScheme, typography, shapes)` + 정적 토큰(`Color.kt`/`Shapes.kt`/`Type.kt`)으로 구현했고, 모션은 Compose 기본 `animateFloatAsState`/`tween`을 유지한다. Expressive 전환은 BOM 상향(1.4+/Material3 1.4) 시 T-47 후속 과제로 재검토 — 상향 시 `material3` API 변경(alpha)과 AGP/Kotlin 정합 확인 필요.


## 8. 접근성

- 색만으로 상태 구분 금지 → 아이콘(`check`/`error`) + 배지 텍스트 병행.
- `contentDescription`: 상태 아이콘·복사 버튼·삭제 버튼 필수. 게이지는 "다음 변경까지 1시간 47분" 텍스트 제공.
- 터치 48dp, 리스트 행 최소 56dp.
- 동적 글자 크기 1.3x에서 2줄 허용 (IP는 `maxLines=2` + `softWrap`).
- 애니메이션: `LocalReducedMotion` 대응 — 카운트다운 스프링 제거, 값만 갱신.

## 9. 구현 단계 (T-번호 제안)

| T | 작업 | 검증 |
|---|---|---|
| T-46 | 목업 + 디자인 스펙 (`docs/design/*`) | 사용자 승인 |
| T-47 | 토큰 계층 (`Color.kt`/`Theme.kt`/`Type.kt`/`Shapes.kt`) + 테마 모드 Prefs + 설정 UI | A90 라이트/다크 스크린샷, ERROR 0 |
| T-48 | 공통 컴포넌트 재작성 (StatusCard/CountdownGauge/RecentChangeCard/ConditionChip/MainActionButton) | 홈 2테마 비교 |
| T-49 | 기록·속도 화면 재구성 (세그먼트 필터, 비교 카드) | A90 실측 2테마 |
| T-50 | DUN·설정 재구성 (그룹 카드, 배너, 테마 섹션) | A90 실측 + TalkBack |
| T-51 | 엣지투엣지 + 대형 화면(NavigationRail) + 모션 | 360/412dp, A90 회전 |

## 10. 리스크 · 롤백

- **위험 낮음**: 순수 UI 레이어. 로테이션·스케줄·DUN 로직 무접촉 → 실패 시 화면 단위 revert.
- **주의**: `enableEdgeToEdge()` 적용 시 하단 내비/게스트 영역과 리스트 마지막 항목 겹침 → `contentPadding` 필수.
- **주의**: 동적 색상 ON 상태에서 success/warning 커스텀 role이 배경화면 색과 충돌하지 않도록 고정 팔레트 유지(성공/경고는 dynamic 미적용).
- 롤백: `git revert` + `./gradlew assembleDebug` → A90 재설치. Prefs 신규 키(`themeMode`, `dynamicColor`)는 기본값 폴백.

## 11. 검증 계획 (A90 단독)

| TC | 시나리오 | 기준 | 결과 (A90, 2026-10-21) |
|---|---|---|---|
| TC-19 | 시스템 다크/라이트 전환 | 앱 재시작 없이 즉시 반영, 크래시 0 | 통과 — `cmd uimode night no/yes` 각각 `dark=false`/`dark=true`, 동일 PID |
| TC-20 | 설정에서 라이트/다크 고정 | 재시작 후 유지, 시스템 전환 무시 | 통과 — 다크 고정 → `force-stop` 후 재시작 `dark=true` 유지 |
| TC-21 | 동적 색상 ON/OFF (API 31) | primary 계열 변화, success/error 불변 | 통과 — OFF `#93CCFF` / ON `#76D1FF`, success는 정적 |
| TC-22 | 글자 크기 1.3x / 디스플레이 크기 최대 | 잘림 0, 스크롤 정상 | 통과 — 홈 2줄 랩 + 스크롤로 주 액션 도달, 설정 세그먼트 3버튼 정상 |
| TC-23 | 전 화면 ERROR 0 + 홈 카운트다운 정확도 | logcat `SpotShift E` 0, 예상 시각 일치 | 통과 — 5탭 순회 ERROR 0·크래시 0, `1:36:13`↔`14:59 예정` 일치 |
