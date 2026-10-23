# PLUGIN_CONTRACT.md — SpotShift 연동 구현 기록

> 규칙 원천: RelayConsole `docs/PLUGIN_SDK.md` (유일 원천). 이 문서는 구현 기록만 남긴다.
> 제공 레벨: **L2** · 계약 버전: **2**.

## 구현 매핑

| SDK | SpotShift |
|---|---|
| §2 프로브 | `receiver/PluginProbeReceiver.kt` — 명시적 브로드캐스트, 앱 미실행 응답, UI 없음. `plugin/PluginContract.probeLine()` (v2 + `appVersion`) |
| §3 Provider | `plugin/PluginInfoProvider.kt` — `content://com.borasarang.spotshift.plugin/info` 단일 행. `iconBase64`는 런처 어댑티브 벡터 96px PNG 런타임 렌더 (T-36) |
| §4 액션 | `receiver/PluginActionReceiver.kt` — 브로드캐스트 `--es cmd autorotate`, 헤드리스 (수동 경로 미접촉). v1 MainActivity 전면 경로 제거済. **T-52: 수신기는 접수/거부만 하고 실행은 `scheduler/RemoteRotationWorker`에 위임** (브로드캐스트 타임아웃 ANR 회피) |
| §5 `[REMOTE]` | `plugin/PluginContract.remoteOk/remoteFail/remoteInvalid/remoteDenied/remoteBusy()` — `action=`·`ok=` 전부, 실패 `errorCode=` |
| §6 `[EVENT]` | `scheduler/RotationWorker.doRotate()` — `type=ip_changed` (자율 주기 변경 성공 시) |
| §7 ON/OFF | 설정 `연동 허용` (기본 ON, `RotationConfig.pluginAllowed`). OFF면 액션 무시 + 거부 줄 + 이벤트 침묵 |
| §8 직접 로그 | `plugin/PluginLog.kt` — `android.util.Log` 직접 (DebugLogger.i/d는 릴리즈 증발) + 한 사건 한 줄 |

## 동시 요청 (T-52)

정책: **진행 중이면 새 요청은 즉시 거부하고, 진행 중인 작업은 그대로 계속한다.** 대기 큐·코얼레싱 없음.

- 단일 실행 게이트: `core/RotationGate.kt` — 수동(홈)·속도 탭·주기 워커·감시 워커·원격이 공유. 리스는 10분 후 자동 만료(비정상 종료 대비).
- 거부 응답 (즉시, 회전 시작 전):

```
[REMOTE] action=autorotate ok=false errorCode=E-AND-PLG-0004 note=busy (source=remote elapsed=2s)
```

- `note`의 `source`는 진행 중인 작업의 출처(`manual`/`speed`/`scheduled`/`watchdog`/`remote`), `elapsed`는 경과 초.
- 주기/감시 경로가 게이트에 막히면 실행하지 않고 `E-AND-SCH-0006`으로 기록만 남긴다(원격 응답 없음).
- `[REMOTE] ok=true/false` 결과 줄은 워커가 회전 종료 시 **한 줄**로 출력한다(형식 불변, 응답 시점만 비동기).

## 요청자 표시 (T-60)

외부 요청이 기록·홈에 **누가** 보냈는지 남긴다. 계약 형식은 그대로이고 아래는 선택 사항이다.

- 권장: 요청 시 `caller` extra를 함께 보낸다.

```
adb shell am broadcast -a com.borasarang.spotshift.PLUGIN_ACTION \
  -n com.borasarang.spotshift/.receiver.PluginActionReceiver \
  --es cmd autorotate --es caller RelayConsole
```

- `caller`가 없으면 API 34+ 에서는 발신 UID(`getSentFromUid`)의 패키지/앱 이름, adb(shell)는 `adb(shell)`로 추정한다. API 31 이하 + adb 요청은 식별할 수 없어 UI에 `외부 요청`으로만 표시된다.
- 표시 위치: 기록 항목 `외부 요청 · <caller> · 데이터 재연결 …`, 홈 히어로 `최근 변경 · 외부 요청 · <caller> · …`, `[ROT] 시작 출처=REMOTE` 로그.
- `[REMOTE]` 결과 줄은 새 필드를 만들지 않고 `note=` 끝에 ` · 요청자=<caller>`를 덧붙인다 (예: `note=측정 9.10Mbps ≥ 기준 2.0Mbps — 달성 · 요청자=RelayConsole`).

## 변경 이력

* v1 (T-32/33): `autorotate` 1액션 + 로그 보고, MainActivity 전면 경로.
* v2 (T-35): SDK v2 L2 승격 — Provider·브로드캐스트 액션·`[EVENT]`·직접 로그. 전면 경로 제거.
* v2.1 (T-52): 동시 요청 정책(BUSY `E-AND-PLG-0004`) + 수신기 즉시 반환(실행은 WorkManager 위임).
  기존 `[REMOTE]` 줄 형식은 그대로이므로 소비자 파싱 변경은 불필요 — `E-AND-PLG-0004` 처리만 추가하면 된다.
* v2.2 (T-60): 요청자 표시 — 선택 extra `caller`, `[REMOTE]` note에 `요청자=` 추가(필드 구조 불변).
