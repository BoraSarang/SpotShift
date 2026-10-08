# PLUGIN_CONTRACT.md — SpotShift 연동 구현 기록

> 규칙 원천: RelayConsole `docs/PLUGIN_SDK.md` (유일 원천). 이 문서는 구현 기록만 남긴다.
> 제공 레벨: **L2** · 계약 버전: **2**.

## 구현 매핑

| SDK | SpotShift |
|---|---|
| §2 프로브 | `receiver/PluginProbeReceiver.kt` — 명시적 브로드캐스트, 앱 미실행 응답, UI 없음. `plugin/PluginContract.probeLine()` (v2 + `appVersion`) |
| §3 Provider | `plugin/PluginInfoProvider.kt` — `content://com.borasarang.spotshift.plugin/info` 단일 행 (`label·description·contractVersion·appVersion·allowed··actionsJson`) |
| §4 액션 | `receiver/PluginActionReceiver.kt` — 브로드캐스트 `--es cmd autorotate`, 헤드리스 (수동 경로 미접촉). v1 MainActivity 전면 경로 제거済 |
| §5 `[REMOTE]` | `plugin/PluginContract.remoteOk/remoteFail/remoteInvalid/remoteDenied()` — `action=`·`ok=` 전부, 실패 `errorCode=` |
| §6 `[EVENT]` | `scheduler/RotationWorker.doRotate()` — `type=ip_changed` (자율 주기 변경 성공 시) |
| §7 ON/OFF | 설정 `연동 허용` (기본 ON, `RotationConfig.pluginAllowed`). OFF면 액션 무시 + 거부 줄 + 이벤트 침묵 |
| §8 직접 로그 | `plugin/PluginLog.kt` — `android.util.Log` 직접 (DebugLogger.i/d는 릴리즈 증발) + 한 사건 한 줄 |

## 변경 이력

* v1 (T-32/33): `autorotate` 1액션 + 로그 보고, MainActivity 전면 경로.
* v2 (T-35): SDK v2 L2 승격 — Provider·브로드캐스트 액션·`[EVENT]`·직접 로그. 전면 경로 제거.
