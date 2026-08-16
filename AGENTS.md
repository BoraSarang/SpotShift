# AGENTS.md — SpotShift 프로젝트 확장 가이드

> 공통 규칙: `~/.config/opencode/AGENTS.md` (v2.1.0-common) 참조 — 이 파일은 프로젝트별 확장만 담음
> v2.1 공통 항목 중 Chrome/Firefox/Safari/모노레포/E2E는 본 프로젝트(N/A: Android 단독 네이티브 앱)에 해당 없으므로 제외하고, 적용 가능한 항목만 반영함

**프로젝트**: SpotShift (핫스팟 IP 변환기)
**제작자**: BoRaSaRang · **Email**: leeborasarang@gmail.com
**버전**: v0.1 (2026-08-16 초기 세팅)

---

## 1. 프로젝트 개요

- Android 기기를 핫스팟으로 사용하며 일정 주기마다 IP 자동 변경 + 모바일 네트워크/핫스팟 자동 복원
- 무루트 (Shizuku 기반), 단독 Android 앱 (서버 없음)
- 상세: docs/PRD.md, docs/DESIGN.md, docs/plans/PLAN_v1.0_android.md

## 2. 프로젝트 상태

- [x] T-1 문서 골격 (PLAN/TODO/AGENTS.android/DESIGN/PRD/error_message_ko)
- [x] T-2 Gradle 프로젝트 골격 + S22 실검증 (v0.1.1 — 데이터 재연결 우선 전략 확정)
- [x] T-3~T-8 v0.2 사용자 요구사항 6종 (셀룰러 전용/주기/스킵/기록 초기화/알림/토글) + T-13 상태 알림 개선
- [x] T-14~T-16 v0.3 홈 디자인 개선 (예상 시각 병기/진행 단계/문의 섹션) — v0.3.1까지 (배지 제거/변경 이력 안내/서비스 자동 복원)
- [ ] v0.4 대기 (후보: 자동 주기 경과 후 실제 자동 변경 실검증)

## 3. 디렉토리 구조

```
SpotShift/
├── AGENTS.md               # 이 파일 (프로젝트 확장)
├── AGENTS.android.md       # Android 플랫폼 확장 (S22 환경)
├── error_message_ko.json   # 사용자 노출 메시지 (앱 리소스)
├── docs/                   # PRD/DESIGN/TODO/plans
├── .agent/                 # 세션 로그 (session-*.md)
├── scripts/                # 빌드/검증 스크립트
└── android/                # Gradle 프로젝트 (Kotlin + Compose)
```

## 4. 언어 절대 규칙 (v2.1 1.6장 적용)

- 사용자에게 보이는 모든 자연어 응답·추론·문서 본문은 **무조건 한국어**
- 코드 변수명/함수명/주석/로그 메시지/커밋 type는 영어 허용. 커밋 subject/body 한국어 권장

## 5. 문서 우선 원칙 (v2.1 1.7장 적용)

1. 코딩 전 `docs/plans/PLAN_v{버전}_{platform}.md` 확인/작성
2. 세션 시작 3분 내 코드 수정 금지 — TODO/PLAN/session 로그 먼저 읽기
3. 15분마다 또는 파일 수정 후 `.agent/session-*.md` 저장
4. 세션 단절 시 `git diff` + session 로그 + TODO로 복구

## 6. 에러 코드 체계 (v2.1 8.5장 적용)

- 형식: `E-AND-{CATEGORY}-{NUM4}` (CATEGORY: NET/PERM/SCH/SRV/STOR)
- 사용자 노출 문구는 `error_message_ko.json`에만 저장. 코드는 KEY 참조
- DebugLogger는 반드시 `error_code` 필드 포함

## 7. DoD 체크리스트 (v2.1 20.2장 축약)

```
[ ] 플랫폼 명시 (android)
[ ] 문서 우선: docs/plans/PLAN_v1.0_android.md + TODO 업데이트
[ ] 코드 + DebugLogger 경유 + error_code 포함
[ ] 언어 절대 준수 (한국어)
[ ] ./gradlew assembleDebug 성공 + S22 설치 + 로그 ERROR 0개 (한국어 첨부)
[ ] error_message_ko.json 업데이트 (신규 에러코드)
[ ] docs/CHANGELOG.md에 [android] 태그 + error_code 기록 (한국어)
[ ] 세션 로그 8줄 요약 저장
```

## 8. 브랜치 규칙

- 기능: `feat/{feature}` (예: feat/rotation-engine)
- 버그: `fix/bd-{id}`
- 커밋: `type(android): subject` 예) `feat(android): add shizuku manager`

## 9. 금지사항

- `print()` 직접 호출 금지 → DebugLogger 경유
- 크로스플랫폼 프레임워크 (Flutter/KMP/RN) 사용 금지 — 네이티브 Kotlin
- 시크릿 커밋 금지 (본 프로젝트는 외부 시크릿 없음)
- 핫스팟 SSID/비밀번호 영구 변경 금지 (복원만)
