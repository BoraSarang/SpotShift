# PLAN_v0.4_android.md — SpotShift v0.4 (속도 조건 + 부팅 복원)

> 버전: v0.4 (2026-10-04) · 플랫폼: Android · 작성자: BoRaSaRang
> 공통 규칙: `~/.config/opencode/AGENTS.md` + `AGENTS.md`(프로젝트 확장) + `AGENTS.android.md` 참조
> 전제: v0.3.1 릴리즈 완료 (T-1~T-16). 본 문서는 v0.4 신규 2건만 다룸

## 1. 배경 (실사용 실측, 2026-10-04)

- 환경: S22 = LTE 테더링 게이트웨이 (Mac이 핫스팟 클라이언트), QoS 3Mbps, Wi-Fi 없음, DUN은 DunUnlocker로 우회済
- 실측 ①: 재부팅 후 자동 변경 중단. 원인 확정 — 코드에 `BOOT_COMPLETED` 수신기 없음 (grep 0건) + Shizuku도 부팅 시 사망
- 실측 ②: 주기 도래 시 무조건 로테이션 → 3M 파이프에서 불필요한 단절 발생. 저속일 때만 실행하는 조건 분기 필요
- 실측 ③: 데이터 절약 모드는 테더(DUN)를 함께 차단 → 최적화 수단에서 제외 (재발 방지)

## 2. 목표 (2건)

| ID | 기능 | 설정 위치 |
|---|---|---|
| F1 | 속도 기반 조건부 실행 | 홈(임계값 표시) + 설정(임계/측정주기/최대반복) |
| F2 | 부팅 시 자동 복원 + 설정 토글 | 설정("부팅 시 자동 시작", 기본 ON) |

## 3. F1 — 변경 후 속도 검증 (2026-10-05 의미 변경)

1. 주기 도래 → 무조건 RotationEngine 실행 (건너뛰기 없음, T-10 스킵 폐지)
2. 변경 후 속도 측정 (1MB 다운로드 시간)
3. 측정값 ≥ 임계값(1~50Mbps, 기본 2) → 달성 기록 + 종료
4. 측정값 < 임계값 → 재변경 (최대 N회, 기본 3회, 도달 시 포기)
5. 측정 실패 → 에러코드 후 검증 없이 종료 (E-AND-NET-0004)

> 구 로직 ("측정→저속만 로테이션→재측정, 정상은 스킵")은 폐지. 코드는
> RotationScheduler.runSpeedGatedRotation + HomeViewModel.manualRotate가 동일 루프.

- 권한: 일반 `INTERNET`만 (Shizuku 불필요)
- 측정 트래픽: 1MB/회 (주기 10분 기준 월 최대 ~4GB — 설정 화면에 명시)
- 에러코드: `E-AND-NET-0004` (속도 측정 실패) — `error_message_ko.json` 추가
- 기존 T-10(주기 내 변경 스킵)과 별개 조건으로 AND 결합

## 4. F2 — 부팅 시 자동 복원

1. `BootReceiver` (`RECEIVE_BOOT_COMPLETED`) 등록 — manifest + 런타임
2. 부팅 시: 저장된 스케줄 재등록 + "부팅 시 자동 시작" 토글 존중 (OFF면 미등록)
3. Shizuku 미연결 상태면 상태 알림으로 재시작 유도 ("Shizuku를 다시 시작하세요" + 앱 실행 인텐트)
   - Shizuku 자동 시작은 무루트에서 불가 — 사용자 1탭은 잔류, 문서에 명시
4. 에러코드: `E-AND-SCH-0002` (부팅 복원 실패) — `error_message_ko.json` 추가

## 5. 금지사항 (v0.4 재확인)

- APN/DUN 설정 손대기 금지 (우회 깨지면 테더 사망)
- 데이터 절약 모드 연동 금지 (테더 차단 실측)
- 핫스팟 SSID/비밀번호 영구 변경 금지 (복원만) — 기존 규칙 유지
- `print()` 직접 호출 금지 → DebugLogger + error_code — 기존 규칙 유지

## 6. 검증 (S22 실기기)

- TC-09: 재부팅 → 스케줄 자동 재등록 + 다음 변경 시각 표시 (토글 ON/OFF 각각)
- TC-10: 재부팅 + Shizuku 미시작 → 재시작 유도 알림 표시
- TC-11: 저속(임계 이하) → 로테이션 실행 + 재측정 반복
- TC-12: 정상 속도 → 스킵 + 기록 ("속도 정상으로 건너뜀")
- TC-13: 측정 실패(비행기모드 등) → 페일오픈 로테이션 + `E-AND-NET-0004` 로그

## 7. DoD (AGENTS.md 7장 축약)

```
[ ] 플랫폼 명시 (android)
[ ] TODO.md T-17/T-18 추가 + 본 문서
[ ] 코드 + DebugLogger 경유 + error_code 포함 (NET-0004, SCH-0002)
[ ] error_message_ko.json 업데이트
[ ] ./gradlew assembleDebug 성공 + S22 설치 + TC-09~13 통과 + 로그 ERROR 0개
[ ] docs/CHANGELOG.md에 [android] 태그 + error_code 기록
[ ] 세션 로그 8줄 요약 저장
```
