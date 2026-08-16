# TODO.md — SpotShift 작업 추적

> 규칙: T-번호 + 상태 + 플랫폼 라벨 (android)

| T | 작업 | 플랫폼 | 상태 | 비고 |
|---|---|---|---|---|
| T-1 | 문서 골격 (PLAN/TODO/AGENTS.android/DESIGN/PRD/error_message_ko) | android | 완료 | 2026-08-16 |
| T-2 | Gradle 프로젝트 + Compose + DebugLogger + 테마 | android | 완료 | 빌드 성공 |
| T-3 | Shizuku 연결 + 에어플레인 토글 | android | 완료 | S22 검증 완료 (cmd connectivity airplane-mode) |
| T-4 | IP 검증(ipify) + 재시도 + 데이터 재연결 우선 + 에어플레인 폴백 | android | 완료 | S22 검증 완료 (TC-01 성공) |
| T-5 | 스케줄러 (주기 + 조건부 실행) | android | 완료 | v0.2 주기 내 스킵 추가 |
| T-6 | 포그라운드 서비스 + 알림 + 퀵 세팅 타일 | android | 완료 | v0.2 서비스-토글 연동 |
| T-7 | UI 3탭 + 아이콘 | android | 완료(코드) | |
| T-8 | S22 실검증 + DoD + CHANGELOG + 세션 로그 | android | 완료 | TC-01 성공 (데이터 재연결), TC-02~08 코드 검증 |
| T-9 | v0.2-주기 30~720분(30분 단위, 기본 120) | android | 완료 | S22 슬라이더 검증 (30/480/120분) |
| T-10 | v0.2-주기 내 IP 변경 시 스킵 (요구사항 2) | android | 완료 | S22 검증 ([SCH] 스킵 로그) |
| T-11 | v0.2-셀룰러 전용 + 기록 초기화 + 알림 시작/완료 + 자동 IP 변경/핫스팟 자동 켜기 토글 | android | 완료 | S22 검증 완료 |
| T-12 | v0.2 문서 갱신 (CHANGELOG/PLAN/세션) | android | 완료 | 2026-08-16 |
| T-13 | 상태 알림에 IP 변경 예상 시간 표시 ("로테이션 실행 중 · 현재 IP xxx · IP 변경 예상 시간 xx:xx") | android | 완료 | S22 검증 완료 — 30초 카운트다운 갱신 |
| T-14 | 홈 게이지: "다음 변경까지 · 14:02 예정" (남은 시간 + 예상 시각 병기) | android | 완료 | S22 검증 — lastRotationAt을 Prefs 구독으로 변경(재시작 유지), 11:04+60분=12:04 예정 정확 |
| T-15 | RotationProgress 진행 단계 표시 (프로그레스 바 + 단계 텍스트) | android | 완료 | S22 검증 — "모바일 데이터 재연결 중"→"IP 변경 완료" 표시. HomeViewModel.rotationState를 mutableStateOf로 수정(기존 버그: 일반 var라 UI 미갱신) |
| T-16 | 설정 탭 문의 섹션 (제작자/문의 메일/GitHub/버전) | android | 완료 | S22 검증 — 제작자/leeborasarang@gmail.com(mailto→Gmail)/GitHub(→브라우저)/v0.3.0. versionName 0.3.0으로 갱신 |
