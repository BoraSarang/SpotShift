# PLUGIN_CONTRACT.md — SpotShift 연동 계약 v1

> 제공자: SpotShift (Android) · 소비자: RelayConsole (macOS) 등
> 원칙: 서로 간섭 없음. 둘 다 꺼져 있어도 각자 정상 동작. 상시 연결 없음 (adb 요청 시점에만 만남).

## 1. 명찰 (Discovery)

SpotShift는 능력을 두 겹으로 선언한다.

* 정적 명찰: `AndroidManifest`의 meta-data 3키 (`spotshift.plugin.version=1`, `spotshift.plugin.actions=autorotate`, `spotshift.plugin.logTag=SpotShift`). 문서용 선언.
* 실제 discovery: `dumpsys package`는 application meta-data를 출력하지 않으므로(기기 실측), 명시적 브로드캐스트 프로브로 질의한다. 앱 꺼져 있어도 응답하며 UI 없음, 상시 연결 없음:

```bash
adb -s <serial> shell am broadcast -a com.borasarang.spotshift.PLUGIN_PROBE \
  -n com.borasarang.spotshift/.receiver.PluginProbeReceiver
adb -s <serial> shell "logcat -d -s SpotShift:*" | grep PLUGIN
```

응답 한 줄 형식:

```
[PLUGIN] version=1 actions=autorotate logTag=SpotShift allowed=<true|false>
```

`연동 허용` OFF여도 응답은 한다 (`allowed=false`로 꺼짐 상태 전달). 무응답 = 미설치 또는 구버전.

## 2. 액션 v1 — `autorotate` (원격 IP 변경 요청)

호출 (fire-and-forget, 결과 대기 없음):

```bash
adb -s <serial> shell am start -n com.borasarang.spotshift/.MainActivity --ez spotshift.autorotate true
```

* 앱 미실행 시 콜드스타트 후 실행, 실행 중이면 즉시 실행 (`singleTop`).
* 성공/실패는 §3 로그로만 전달. 호출 측에 콜백 없음.

## 3. 결과 로그 (Report)

태그: `SpotShift` (`adb -s <serial> shell logcat -d -s SpotShift:*`).

| 상황 | 한 줄 형식 |
|---|---|
| 완료/실패 | `[REMOTE] 원격 변경 결과 changed=<true\|false> <oldIp\|-> → <newIp\|-> <errorCode\|note>` |
| 거부 (연동 OFF) | `[REMOTE] 거부됨 (연동 OFF)` |

형식은 정규식으로 파싱 가능해야 하며, 필드 순서·키 이름(`changed=`·`→`)을 바꾸면 계약 버전 올림.

## 4. ON/OFF

* SpotShift 설정 `연동 허용` (기본 ON). OFF면 액션 무시 + 거부 로그 (§3).
* 소비자 측도 자체 토글 보유. 양쪽 AND — 한쪽이라도 OFF면 조용히 각자.
* 거부·미지원은 에러코드 + 원인 포함 (원인 없는 실패 표시 금지).

## 5. 버전 규칙

* 계약 버전은 정수 올림만. 필드 추가는 기존 파서 안 깨지는 선에서 허용.
* 모르는 버전·모르는 액션 만나면 소비자는 연동 끄고 `미지원` 표시 (추측 실행 금지).

## 6. 소비자 구현 가이드 (RelayConsole)

1. 설치 확인: `pm list packages`에 명찰 패키지 존재 여부.
2. 기능 확인: §1 프로브 발송 → `[PLUGIN]` 응답 파싱 (무응답이면 미지원).
3. 동작: 플러그인 탭에 항목 표시 + 사용/사용안함 토글 → 액션 버튼 (`IP 변경 요청`).
4. 결과: §3 스크랩 → `IP 변경 완료 x→y` 표시 (스캔은 캐시 + 느린 틱/수동 새로고침).

## 7. 변경 이력

* v1 (2026-10-08): 최초 — `autorotate` 1액션 + 로그 보고. (T-32)
* v1.1 (2026-10-08): discovery를 dumpsys→브로드캐스트 프로브로 변경 (dumpsys 미표시 실측). `PluginProbeReceiver` + `연동 허용` 토글 + 거부 로그. (T-33)
