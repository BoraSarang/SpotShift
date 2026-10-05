# CHANGELOG.md — SpotShift

> 형식: `[android] 버전 — 날짜 — 요약 (에러코드/성능 기록)`

## v0.4.1 — 2026-10-05 — 무조건 변경 확정 + 스케줄 스킵 폐지 + 무음 버그 수정 [android] (미릴리즈)

- **의미 변경**: 속도 기준은 변경 전 건너뛰기가 아니라 변경 후 검증. 주기가 되면 무조건 변경 → 측정 → 기준(1~50Mbps, 기본 2) 미달이면 재변경 최대 3회. T-10 주기 내 스킵 폐지 (shouldSkipByRotation 호출 제거)
- **수정**: IpRotationService 채널 삭제 크래시 (SecurityException) — deleteNotificationChannel 제거·없을 때만 생성. Prefs 통째 saveConfig 폐지 → 키별 update* 13종. 수동/원격 경로 콜드스타트 fresh read (config.value 초기값 알림=true 오발송 수정) + RotationEngine 진입 [CFG] 로그
- **스크립트**: spotshift_rotate.sh 기본=요청만, --wait/-w/--verbose/-v 대기, No route to host 시 kill-server/start-server 자동복구
- **스마트 조건 재정의 (T-24)**: 셀룰러 모드만 실행 전 관문(스킵 시 로그+기록). 시간대 설정 UI 없어 죽은 코드 삭제. 신호 체크가 와이파이 RSSI(-127 쓰레기값)를 보던 버그 → SignalMonitor LTE RSRP로 교체. 배터리·신호는 실행을 막지 않고 기록 note에 동봉 ("배터리 79% · RSRP -100dBm"). 홈 칩도 설정값 표시에서 실시간 값으로 전환
- 빌드: `./gradlew assembleDebug` 성공, S22 설치+실행. 검증 대기: 다음 주기 자동 변경 실동작 + 무음 재확인

## v0.4.0 — 2026-10-04 — 속도 조건 + 부팅 복원 [android] (구현 완료·실검증 대기)

- **신규 T-17**: `SpeedChecker`(1MB 다운로드 Mbps 환산, INTERNET 권한만) + 스케줄러 속도 게이트 (저속만 로테이션→30초 후 재측정 최대 3회, 정상은 스킵+기록, 측정 실패는 페일오픈) — E-AND-NET-0004
- **신규 T-18**: `BootReceiver`(BOOT_COMPLETED/QUICKBOOT_POWERON) + 설정 "부팅 시 자동 시작"(기본 ON) + Shizuku 미연결 시 재시작 유도 알림 — E-AND-SCH-0002
- UI: 설정 "속도 조건" 섹션(토글/기준 0.5~3.0Mbps/최대반복) + 홈 상태카드 속도 기준/최근 측정 표시 + 홈 "최근 변경" 카드(x→y·방식·측정속도·시각) + 기록 스킵 중립색
- 수정: Shizuku 승인 후 상태 고착 버그 (StateFlow 반응형 전환 — HomeViewModel.shizukuReady) + 최근 측정 속도 저장/표시 (Prefs.lastSpeed) + 실행 시 Shizuku 미승인이면 자동 요청 (MainActivity onResume, 프로세스당 1회) + Shizuku 사망 시 권한 버튼이 Shizuku 앱 실행으로 폴백 (무응답 수정) + 메인 버튼 미승인 시 권한 요청 동작 + 설정 "배터리" 섹션 (최적화 제외 요청·복귀 시 갱신) + 측정 엔드포인트 교체 (tele2 연결 거부 실측 → Cloudflare __down 1MB) + 알림 단일화 (시작 알림 폐지·완료 ping 10초 후 자동 소멸·기존 잔재 자동 정리) + 설정 "완료 시 소리 알림" 토글 + 전용 알림 아이콘 (ic_notification) + 홈 접속 상태 표시 (SignalMonitor: 통신사·RAT·RSRP/RSRQ/SINR, 위치/전화 권한) + 측정 1회=기록 1건 (SPEED_CHECK) + Mac 원격 변경 (EXTRA_AUTOROTATE + spotshift_rotate.sh, 실검증 성공)
- 빌드: `./gradlew assembleDebug` 성공, S22 설치+실행 ERROR 0, BootReceiver 등록 확인
- 검증 대기: TC-09~13 (재부팅·속도 분기 — 테더 세션 보호로 원격 재부팅 불가, 다음 실기회에서 검증)

## v0.1.0 — 2026-08-16 — 초기 프로젝트 세팅 [android]

- 문서 골격: PLAN_v1.0_android.md / TODO.md / AGENTS.android.md / DESIGN.md / PRD.md / error_message_ko.json (E-AND-NET-0001~0003, E-AND-PERM-0001~0002, E-AND-SCH-0001, E-AND-SRV-0001, E-AND-STOR-0001)
- Gradle 프로젝트 골격: Kotlin 2.0.21 + Compose(BOM 2024.09.03) + AGP 8.5.2 + Gradle 8.9, minSdk 26 / targetSdk 35
- Shizuku 연동: ShizukuManager(리플렉션 newProcess), AirplaneController(에어플레인 토글)
- IP 로테이션: RotationEngine(에어플레인 우선 → 핫스팟 폴백), IpVerifier(ipify)
- 스케줄러: RotationScheduler + Conditions(배터리/시간대/신호 조건)
- 서비스: IpRotationService(포그라운드) + SpotShiftTileService(Quick Settings)
- UI: Material 3 다크 OLED(홈 대시보드/기록/설정 3탭) + CountdownGauge 원형 게이지 + 런처 아이콘(Wi-Fi+순환 화살표)
- 빌드: `./gradlew assembleDebug` 성공 (첫 빌드 36s)
- 검증: 갤럭시 S22 실기기 검증 대기 (TC-01~08)

## v0.1.1 — 2026-08-16 — API 36 실검증 반영: 데이터 재연결 우선 전략 [android]

- **핵심 전략 변경**: 에어플레인 우선 → **모바일 데이터 재연결 우선 + 에어플레인 폴백**
  - S22(API 36) 실검증: `svc data disable/enable`로 IP 변경 성공(110.70.15.195→175.223.19.98) + 핫스팟 TetheredState 유지
- **API 36 제약 발견 (S22 실검증)**: `am broadcast AIRPLANE_MODE` 차단(exit=255), TetheringManager 리플렉션/`cmd wifi start-softap` 모두 `TETHER_PRIVILEGED`(Error 14)로 차단 → 핫스팟 재시작 폴백 제거
- **신규**: `DataController`(svc data 기반), `RotationPhase.ROTATING_DATA/FALLBACK_AIRPLANE`, `RotationRecord.METHOD_DATA_RECONNECT`
- **수정**: `AirplaneController` → `cmd connectivity airplane-mode`로 교체 (S22 성공), `HotspotController.getApConfig` → API 33+ `getSoftApConfiguration` 지원, 설정 탭 폴백 문구 갱신, error_message_ko.json NET-0003 문구 갱신
- 검증: TC-01 성공 (데이터 재연결 → IP 변경 + 핫스팟 유지, ERROR 0)

## v0.2 — 2026-08-16 — 사용자 요구사항 6종 구현 + S22 실검증 [android]

- **요구사항 0 (셀룰러 전용)**: Conditions.isCellularMode — activeNetwork가 CELLULAR+비WIFI일 때만 동작. S22 검증: 핫스팟 ON 상태에서도 active default=셀룰러(244) → 통과
- **요구사항 1 (주기)**: 30분~12시간(30분 단위), 기본 120분 — 설정 탭 슬라이더. S22 검증: 30/480/120분 스냅 동작 확인 (탭 점프는 스냅 미적용, 드래그는 정상)
- **요구사항 2 (주기 내 스킵)**: RotationScheduler.shouldSkipByRotation — 마지막 변경 후 주기 미경과 시 스킵 + 이동 IP 변경 감지 시 메타 갱신. S22 검증: `[SCH] 주기 내 IP 변경됨 — 자동 변경 스킵 (E-AND-SCH-0001)` 로그 확인
- **요구사항 3 (기록 초기화)**: 기록 탭 "기록 초기화" 버튼 + Prefs.clearRecords(). S22 검증 완료
- **요구사항 4 (알림)**: RotationEngine 단일 발행 구조로 통합(서비스/ViewModel 중복 발행 제거) — 시작/완료(x→y)/실패 알림. MainActivity POST_NOTIFICATIONS 런타임 요청 추가. S22 검증: 재설치 후 권한 자동 복원 + 알림 정확히 2건(시작+완료) 발송
- **요구사항 5 (토글)**: "자동 IP 변경" 토글이 IpRotationService 라이프사이클과 연동(startForegroundService/stopService), "핫스팟 자동 켜기" 토글 — API 36 자동 ON 제한으로 설정 화면 안내(ACTION_TETHERING_SETTINGS가 API 36에서 제거됨 → ACTION_WIRELESS_SETTINGS로 교체). S22 검증: 토글 OFF→ON 시 서비스 시작 확인
- **수정**: HomeViewModel.notify 제거(엔진 통합), RotationEngine에 context 주입 + CHANNEL_EVENT, Prefs에 lastRotationAt/lastKnownIp 메타 저장(updateRotationMeta/clearRecords), HistoryScreen METHOD_DATA_RECONNECT 표시
- **v0.2.1 — 상태 알림 개선 (2026-08-16)**: 상태 알림 형식 변경 → "로테이션 실행 중 · 현재 IP xxx · IP 변경 예상 시간 xx:xx" — 서비스 시작 시 IP 조회 + 마지막 변경+주기 기준 남은 시간(시:분) 30초마다 갱신 (startCountdownUpdate, nextChangeRemaining)
- 검증: IP 변경 2회 성공 (39.7.47.142→110.70.54.232, 175.223.19.98→39.7.47.142), 핫스팟 TetheredState 유지, 포그라운드 서비스 정상

## v0.3 — 2026-08-16 — 홈 화면 디자인 개선: 예상 시각 + 진행 단계 + 문의 섹션 [android]

- **홈 카운트다운 명확화 (T-14)**: CountdownGauge에 예상 시각 병기 → "다음 변경까지 · 13:38 예정" (마지막 변경 시각+주기로 계산, SimpleDateFormat HH:mm). lastRotationAt을 Prefs(config) 구독으로 변경 — 앱 재시작/자동 변경 후에도 유지. S22 검증: 11:04+60분=12:04 예정 정확 일치
- **진행 단계 인디케이터 (T-15)**: RotationProgress 신규 컴포넌트 — 인디터미네이트 프로그레스 바 + 단계 텍스트 (현재 IP 확인 중 / 모바일 데이터 재연결 중 / IP 변경 확인 중 / 재시도 중 (n/3) / 에어플레인 모드 전환). S22 검증: "모바일 데이터 재연결 중"→"IP 변경 완료" 순차 표시, 버튼 "정지"↔"IP 변경 시작" 토글
- **버그 수정 (HomeViewModel)**: rotationState가 일반 var였음 → mutableStateOf로 변경. 진행 단계/완료 메시지가 UI에 즉시 반영 안 되던 문제 해결 (이전에는 SUCCESS 메시지 미표시)
- **문의 섹션 (T-16)**: 설정 탭 하단에 About 카드 — 제작자 BoRaSaRang / 문의 메일 leeborasarang@gmail.com (mailto→Gmail ComposeActivity) / GitHub github.com/BoraSarang/SpotShift (→브라우저) / 앱 버전. S22 검증: GitHub 클릭 시 Whale 브라우저 열림, mailto 클릭 시 Gmail 작성 화면 열림, `[FEATURE] SettingsScreen 진입 문의 메일 열기` 로그 확인
- **릴리스**: versionName 0.1.0→0.3.0, versionCode 1→3
- **배지 수정 (v0.3 후속)**: 상태 알림(spotshift_status)이 아이콘 배지 카운트에 포함되어 "1"이 표시되던 문제 — 채널에 setShowBadge(false) 적용 + 기존 채널 삭제 후 재생성 (NotificationCompat/Notification.Builder에는 setShowBadge가 없음 — NotificationChannel 전용 API). S22 검증: mShowBadge=false + 홈 아이콘 배지 사라짐 확인. **주의**: 삼성 시스템에서 기존 채널의 delete→재생성은 즉시 반영 안 됨 (채널이 없을 때 첫 생성 시에만 적용 — 배포 시엔 문제없음)
- 검증: IP 변경 3회 연속 성공 (39.7.51.11→175.223.34.159→110.70.58.185), RotationEngine에 [STATE] phase 디버그 로그 추가(thread=main 확인), ERROR 0 (E-AND-NET-0001은 VERIFYING 중 일시적 조회 실패 — 재시도로 복구, 정상 흐름)

## v0.3.1 — 2026-08-16 — 변경 이력 없는 상태 안내 + 서비스 자동 복원 [android]

- **변경 이력 없음 안내**: lastRotationAt=0일 때 게이지 대신 "아직 변경 이력이 없습니다 — IP 변경 시작을 누르면 다음 변경 예상 시각을 표시합니다" 카드 표시 (2:00:00 전체 표시 오해 방지)
- **서비스 자동 시작 (SpotShiftApp)**: 앱 시작 시 enabled=true면 IpRotationService 자동 시작 — 재설치/기기 재부팅 후에도 자동 IP 변경 유지 (기존: 토글 OFF→ON 수동 조작 필요)
- **상태 알림 IP 재조회**: startCountdownUpdate에서 currentIp가 null이면(시작 시 IP 조회 실패) 30초 주기로 재조회 — "현재 IP" 구간 누락 복구
- **버그 수정 (배지)**: 상태 알림 채널 setShowBadge(false) — NotificationChannel 전용 API임을 javap으로 확인, 기존 채널 삭제 후 재생성 (삼성 시스템은 기존 채널 delete→재생성 즉시 반영 안 됨 — 신규 생성 시에만 적용)
- **카운트다운 버그 수정**: 홈 남은 시간이 고정되던 문제 — tick(증가만 하고 읽히지 않던 상태)을 `now`(System.currentTimeMillis 갱신 상태)로 교체해 1초마다 재구성되도록 수정. S22 검증: 1:47:38 → 1:47:24 감소 확인
- **릴리스**: versionName 0.3.0→0.3.1, versionCode 3→4
- 검증 (S22): pm clear로 데이터 초기화 상태에서 전 흐름 재검증 — Shizuku 서버 재시작(libshizuku.so) + 권한 재부여("연결됨"), IP 변경 110.70.58.185→39.7.50.166, 홈 "1:54:44 · 다음 변경까지 · 14:42 예정" 정확(12:42+120분), 노티 "로테이션 실행 중 · 현재 IP 39.7.50.166 · IP 변경 예상 시간 01:55" 완전 형식, 앱 시작 시 서비스 자동 시작 로그 확인, ERROR 0

## v0.3.1 — 2026-08-16 — 첫 릴리즈 배포: 서명/Releases/랜딩/Pages [android]

- **릴리즈 서명 설정**: keytool로 release keystore 생성(`keystore/release.jks`, RSA 2048, 유효기간 10,000일) + `signingConfigs.release` 추가 — 경로/암호는 `local.properties`에 분리 (gitignore, 커밋 제외)
  - 해결: local.properties 커스텀 키는 AGP가 자동 노출하지 않음 → `Properties` 명시 파싱 + `rootProject.file()` 기준 경로
- **릴리즈 빌드**: `./gradlew assembleRelease` 성공 — `app-release.apk` (11.4MB, 서명 28d941f9)
- **S22 전환**: 디버그 서명과 충돌로 기존 앱 제거 후 릴리즈 APK 설치 성공 (versionCode 4 / versionName 0.3.1)
- **GitHub 배포**: `BoraSarang/SpotShift` main 브랜치 push (첫 커밋) → `gh release create v0.3.1` + APK 첨부 → Draft → published (https://github.com/BoraSarang/SpotShift/releases/tag/v0.3.1)
- **랜딩 페이지**: 루트 `index.html` — ui-ux-pro-max 디자인 시스템 (Minimalism & Swiss 다크, #0F172A + 액센트 #22C55E, Poppins/Open Sans, App Store Style 구조) + CSS 3D 폰 목업 히어로
  - 설치 방법 4단계 세밀 반영: Shizuku 설치 → 무선 디버깅 활성화(갤럭시 경로) → 알 수 없는 소스 허용 → 권한 연결/시작 + 해외(일본) 사용 안내
  - FAQ 6종(무루트 원리/끊김/배터리/업데이트/개인정보) + 문의 섹션
- **README.md**: GitHub 저장소용 상세 설치 가이드 + 개발/업데이트 안내
- **GitHub Pages**: main 브랜치 루트 배포 → https://borasarang.github.io/SpotShift/ (HTTP 200, 빌드 완료)
