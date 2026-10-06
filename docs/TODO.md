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
