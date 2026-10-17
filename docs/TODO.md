# TODO.md — SpotShift 작업 추적

> 규칙: T-번호 + 상태 + 플랫폼 라벨 (android)

| T | 작업 | 플랫폼 | 상태 | 비고 |
|---|---|---|---|---|
| T-1 | 문서 골격 (PLAN/TODO/AGENTS.android/DESIGN/PRD/error_message_ko) | android | 완료 | 2026-08-16 |
| T-2 | Gradle 프로젝트 + Compose + DebugLogger + 테마 | android | 완료 | 빌드 성공 |
| T-3 | Shizuku 연결 + 에어플레인 토글 | android | 완료 | S22 검증 완료 (cmd connectivity airplane-mode) |
| T-4 | IP 검증(ipify) + 재시도 + 데이터 재연결 우선 + 에어플레인 폴백 | android | 완료 | S22 검증 완료 (TC-01 성공) |
| T-5 | 스케줄러 (주기 + 조건부 실행 → 무조건 실행으로 전환, T-23/T-24) | android | 완료 | v0.2 주기 내 스킵은 폐지, 셀룰러만 관문 |
| T-6 | 포그라운드 서비스 + 알림 + 퀵 세팅 타일 | android | 완료 | v0.2 서비스-토글 연동 |
| T-7 | UI 3탭 + 아이콘 | android | 완료(코드) | |
| T-8 | S22 실검증 + DoD + CHANGELOG + 세션 로그 | android | 완료 | TC-01 성공 (데이터 재연결), TC-02~08 코드 검증 |
| T-9 | v0.2-주기 30~720분(30분 단위, 기본 120) | android | 완료 | S22 슬라이더 검증 (30/480/120분) |
| T-10 | v0.2-주기 내 IP 변경 시 스킵 (요구사항 2) → **폐지 (T-23)**: 무조건 변경 원칙과 충돌 | android | 완료(폐지) | S22 검증 ([SCH] 스킵 로그) — 코드에서 호출 제거됨 |
| T-11 | v0.2-셀룰러 전용 + 기록 초기화 + 알림 시작/완료 + 자동 IP 변경/핫스팟 자동 켜기 토글 | android | 완료 | S22 검증 완료 |
| T-12 | v0.2 문서 갱신 (CHANGELOG/PLAN/세션) | android | 완료 | 2026-08-16 |
| T-13 | 상태 알림에 IP 변경 예상 시간 표시 ("로테이션 실행 중 · 현재 IP xxx · IP 변경 예상 시간 xx:xx") | android | 완료 | S22 검증 완료 — 30초 카운트다운 갱신 |
| T-14 | 홈 게이지: "다음 변경까지 · 14:02 예정" (남은 시간 + 예상 시각 병기) | android | 완료 | S22 검증 — lastRotationAt을 Prefs 구독으로 변경(재시작 유지), 11:04+60분=12:04 예정 정확 |
| T-15 | RotationProgress 진행 단계 표시 (프로그레스 바 + 단계 텍스트) | android | 완료 | S22 검증 — "모바일 데이터 재연결 중"→"IP 변경 완료" 표시. HomeViewModel.rotationState를 mutableStateOf로 수정(기존 버그: 일반 var라 UI 미갱신) |
| T-16 | 설정 탭 문의 섹션 (제작자/문의 메일/GitHub/버전) | android | 완료 | S22 검증 — 제작자/leeborasarang@gmail.com(mailto→Gmail)/GitHub(→브라우저)/v0.3.0. versionName 0.3.0으로 갱신 |
| T-17 | v0.4-F1 속도 기반 조건부 실행 (측정→저속만 로테이션→재측정, 페일오픈) → **T-21로 의미 변경**: 변경 후 검증으로 전환 | android | 완료(구 로직 폐지) | S22 실검증 통과, E-AND-NET-0004 |
| T-18 | v0.4-F2 부팅 자동 복원 + 설정 토글 (BootReceiver/재등록/Shizuku 유도알림) | android | 완료 | S22 실검증 통과 (TC-09~10), E-AND-SCH-0002 |
| T-19 | 홈 통신사/신호 표시 (KT·LTE·RSRP/RSRQ/SINR) — 위치 권한 필요 | android | 완료 | S22 실검증 — 권한 다이얼로그 + 상태카드 표시 확인, rat은 전화 권한 후 LTE 표시 |
| T-20 | Mac 원격 IP 변경 (adb intent + 스크립트, 미실행 시 실행 후 변경) | android+mac | 완료 | MainActivity EXTRA_AUTOROTATE(singleTop) + development/scripts/spotshift_rotate.sh, S22 실검증 (175.223.10.220→39.7.51.57) |
| T-21 | 무조건 변경 후 속도 검증 (건너뛰기 폐지·기준 1~50Mbps 기본 2·미달 시 재변경 최대 3회) | android | 완료 | Scheduler + 수동/원격 동일 루프, S22 검증 (11:32/11:50 달성 기록) |
| T-22 | Prefs 키별 저장 + 콜드스타트 fresh read (알림 무음 버그 수정) | android | 완료 | 통째 saveConfig 폐지 → update* 13종, [CFG] 진입 로그, S22 무음 검증 |
| T-23 | T-10 주기 내 스킵 폐지 (무조건 변경 원칙과 충돌) | android | 완료 | shouldSkipByRotation 호출 제거, S22 검증 대기 |
| T-24 | 스마트 조건 재정의 (관문→체온계): 셀룰러만 관문+스킵 기록, 시간대 삭제, 신호 LTE RSRP 교체, 배터리·신호 기록 전환 | android | 완료 | S22 검증 — 자동 변경 실행+기록 `(배터리 79% · RSRP -100dBm)`, 홈 실시간 칩 |
| T-25 | 배터리 섹션 버튼 반전 수정: 미설정 시 "배터리 예외 설정"(직접 요청 다이얼로그) / 설정 시 "설정 열기" | android | 완료(코드) | manifest REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 추가, 미지원 기기는 목록 화면 폴백 — S22 UI 확인 대기 |
| T-26 | PLAN_v0.5 스케줄러 WorkManager 이관 (FGS 백그라운드 크래시 6회 근본 수정, 주기 하한 15분, 서비스 완전 이관) | android | 완료 | S22 검증 — tick→run→DATA_RECONNECT 성공(39.7.46.50→39.7.25.85)·속도달성·크래시 0. FGS 타입 크래시 1건 발견→manifest 주입으로 해결 |
| T-27 | 속도 탭 (fast.com식): 실시간 다운·업·지연 측정 + 접속상태 + 미달 시 IP 변경→자동 재측정→전/후 비교 + 기록(삭제/초기화확인) + Mbps 옆 B/s 병기 + 결과/기록 레이아웃 정리 | android | 완료 | S22 검증 — 측정↓↑·기록저장·삭제·IP변경→재측정→비교(유지/다시변경) 전 플로우. 업로드 0Mbps 버그 2건 수정(버퍼집계→벽시계, 페이로드 축소) |
| T-28 | 기록 탭 초기화 확인 다이얼로그 (속도 탭과 동일 규칙) | android | 완료 | S22 검증 — 다이얼로그 표시→취소→기록 보존 |
| T-29 | 속도 탭 업로드 실패 수정 (저속 업링크에서 서버 확답 없어도 추정 성공 처리) | android | 완료 | S22 검증 — 0.5.0에서 재현(4MB 전송·성공응답 false→실패), 전송 바이트/벽시계 추정으로 성공 전환 후 ↓2.42↑1.11 측정 완료·기록 저장·ERROR 0 |
| T-30 | 전 단계 진행 표시 (속도 측정 단계 누락 수정) | android | 완료 | `SPEED_CHECKING` 추가 — 수동/자동 `measure()` 전 `notifySpeedChecking()` 호출, 홈 프로그레스·Worker 알림·속도탭 라벨에 `속도 측정 중` 표시 |
| T-31 | 핫스팟 안내 개선 4점 (문구·오탐·데드코드·재시도 표기) | android | 완료 | 설정 `핫스팟 꺼짐 안내` 개명·`isHotspotEnabled` Nullable(실패시 이전 표시 유지·안내 미발동)·`restartHotspot` ERROR 폐기·재시도 `attempt/total` 설정 연동·데이터재연결 문구 통일 |
| T-32 | 연동 계약 v1 문서 (RelayConsole 플러그인용) | android | 완료 | `docs/PLUGIN_CONTRACT.md` — 명찰·`autorotate` 호출·`[REMOTE]` 로그 형식·ON/OFF AND·버전 규칙 |
| T-33 | 연동 계약 v1 구현 (명찰·프로브·토글·거부) | android | 완료 | manifest 명찰 3키 + `PluginProbeReceiver`(dumpsys 미표시 실측으로 프로브 방식 채택) + 설정 `연동 허용` + OFF시 `[REMOTE] 거부됨` — S22 실검증: force-stop 상태 프로브 응답·크래시 0 |
| T-34 | 속도 측정 후 최종 상태 복원 (진행 표시 고착 수정) | android | 완료 | T-30 후유증: `SPEED_CHECKING`에서 안 돌아와 스피너 영구 표시 → `notifySpeedFinished()`로 SUCCESS/FAILED 복원 — S22 실검증: 측정 후 `phase=SUCCESS` + `[REMOTE]` 결과 로그 |
| T-35 | 연동 SDK v2 L2 승격 (Provider·브로드캐스트·EVENT·직접 로그) | android | 완료 | `PluginInfoProvider` + `PluginActionReceiver`(전면 경로 제거) + 주기 변경 `[EVENT]` + `PluginLog` 직접 Log — S22: 프로브v2·Provider 조회 통과, 회전 실사격은 발열로 보류 |
| T-36 | Provider iconBase64 실측 (런처 아이콘 96px PNG) | android | 완료 | 어댑티브 벡터 런타임 렌더 + base64, 실패시 "" 폴백 — S22 `iVBOR` 확인·RelayConsole 통과 |
| T-37 | DUN 탭 (APN 선택+DUN 추가/원복, Shizuku) + 설정 Shizuku 설치/실행/GitHub 동선 | android | 완료 | `ApnController`(shell content 조회·수정·재조회검증)+`DunViewModel/Screen`+5번째 탭. A90 UI 검증(ERROR 0) + A90/S22 실사용 검증 통과 (KT dun 삭제 시 1.3K 제한 → 추가 정답 확정). 직접 구현이라 MIT 유지. E-AND-NET-0005 |
| T-38 | DUN 실측 마무리: A90 추가/원복 사이클 + S22 동일 검증 (+차단 시 복사-삽입 폴백) | android | 완료 | v0.6 TC-12. KT dun 삭제 시 테더링 1.3K 제한 → dun 추가 정답 확정. A90 실사용 통과 + S22 기존 검증 확인. 복사-삽입 폴백 불필요 (직접 수정 통과) |
| T-39 | DUN 부팅 유지: BootReceiver DUN 확인+재적용, 설정 토글 | android | 완료(코드) | v0.7 — `DunKeeper`(원본 보관분 dun 소실 시 재적용)+`Prefs.dunMaintain`(기본 ON)+DUN 탭 토글+`SpotShiftApp` Shizuku 연결 시 자동 ensure+`E-AND-SCH-0004`. A90 UI 검증 (토글 ON·ERROR 0). 부팅 사이클 실측 대기 |
| T-40 | 온도 가드: 임계 초과 시 로테이션 연기+알림, 상태 표시 | android | 완료 | v0.7 — `ThermalGuard`(배터리 45°C·thermal 5 연기)+`RotationPhase.DEFERRED`+엔진 진입 게이트+설정 토글+E-AND-SCH-0003. A90 실사격: `[THERMAL] 36.0°C·thermal=3 defer=false`→재연결 성공(118.235.65.163→.31.229)→3.35Mbps 달성·ERROR 0. 연기 경로는 평시 재현 불가로 로직 검증만 |
| T-41 | 전원 감시: 충전 해제/잔량 임계 알림 + 로테이션 정책 연동 | android | 완료 | v0.7 — `PowerMonitor`(미충전+20% 이하 연기)+`PowerReceiver`(분리 알림)+엔진 게이트+E-AND-SCH-0005+설정 토글. 매니페스트 수신 불가(백그라운드 제한) → `SpotShiftApp` 동적 등록으로 해결. A90 실측: 분리→알림 발행·연결 로그 확인 |
| T-42 | 속도 감시 트리거: 주기 외 저속 감지 시 자동 로테이션 (주기 하한·절약 모드: 패시브 우선+미니 프로브 256KB) | android | 완료 | v0.7 — `SpeedWatchdog`(30초 패시브+유휴 시 ipify 생존확인)+`WatchdogWorker`+`WatchdogSchedule`(회전주기 공유·최소 60분)+설정 토글. A90 등록 검증 (ENQUEUED·클래스/고유명 확인·ERROR 0). 강제실행은 삼성 스케줄러가 무시 → 자연 실행(120분 내) 시 [WATCH] 확인 대기 |
| T-43 | 원격 상태 조회 스크립트 (온도/IP/DUN/스케줄) | android | 완료 | v0.7 — `scripts/spotshift_status.sh` (전원·신호·핫스팟·IP·DUN·스케줄·Shizuku·ERROR, 읽기 전용). 부수 수정: 화면 진입 로그 스팸 제거 (재구성마다 22건/초 → LaunchedEffect 1회, A90 실측 0건) |
| T-44 | 권한 추상화 (PrivilegedShell, Shizuku 어댑터) | android | 완료 | v0.7 T-39 선행. `core/PrivilegedShell.kt`(인터페이스+`ShizukuShell`), `ApnController` 전량 경유. 회전 코어는 불변(검증본 유지, 루팅 전환 시 이관) |
| T-45 | 수동 변경 버튼의 자동 변경 결합 제거 (수동 IP 변경 → 자동 IP 변경 토글 자동 ON 되던 버그) | android | 완료 | `HomeScreen` 메인 버튼에서 `setEnabled(true/false)` 제거 → 일회성 `manualRotate`만. `MainActionButton` "정지" 폐기(회전 중 비활성·"IP 변경 중…"), 홈에 자동 변경 상태 표시. 자동 On/Off는 설정 토글·퀵세팅 타일 전담. **A90 실측 통과 (TC-18)**: OFF 설정 → 홈 버튼 → `manualRotate 시작 (자동 주기 미변경, 현재 자동=false)` + IP 변경(118.235.13.29→118.235.2.137)·3.41Mbps 달성, 이후 `setEnabled` 로그 0건·재시작 시 주기 미등록, ERROR 0 |
