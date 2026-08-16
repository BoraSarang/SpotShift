# 세션 로그 — 2026-08-16 (android) — 6차 세션 (v0.3.1 배지 수정 + 서비스 자동 복원)

## 8줄 요약

1. **무엇을**: v0.3.1 — 배지 "1" 제거 + 변경 이력 없음 안내 + 앱 시작 시 서비스 자동 시작 + 상태 알림 IP 재조회
2. **플랫폼**: android (Galaxy S22 SM-S901N, Android 16/API 36, adb serial R5CT215F4QK)
3. **빌드 결과**: `./gradlew assembleDebug` 성공 (에러 0) — v0.3.1 (versionCode 4)
4. **남은 TODO**: (없음) v0.4 후보: 자동 주기 경과 후 실제 자동 변경 실검증
5. **다음 에이전트 전달**:
   - **setShowBadge는 NotificationChannel 전용 API** (NotificationCompat/Notification.Builder에 없음 — javap 확인). 삼성 시스템에서 기존 채널 delete→재생성은 **즉시 반영 안 됨** (pm clear로 채널 초기화 후 신규 생성 시에만 적용 — 배포 시 문제없음)
   - **pm clear 주의**: 데이터(설정/기록/lastRotationAt) 초기화됨 → 홈 "2:00:00 다음 변경까지" + 노티 "현재 IP/예상 시간" 생략은 **정상 로직** (lastRotationAt=0이면 예상 시간 계산 불가)
   - **Shizuku 권한**: 서버 재시작(libshizuku.so) 후 앱 권한이 초기화될 수 있음 → 설정 탭 "권한 요청" → "항상 허용" 다이얼로그 → 화면 재진입 시 "연결됨"
   - **v0.3.1 신규**: SpotShiftApp.onCreate에서 enabled=true면 IpRotationService 자동 시작 (재설치/재부팅 복원) — 로그 "[FEATURE] SpotShiftApp 진입 자동 IP 변경 ON — 서비스 자동 시작"
   - 수동 변경은 HomeViewModel.engine, 서비스 알림은 서비스 자체 engine 사용 — 서비스 currentIp는 시작 시 조회 실패 시 null 잔류 → startCountdownUpdate에서 30초마다 재조회하도록 수정됨
6. **문서 업데이트**: CHANGELOG.md(v0.3.1), TODO.md(T-14~16 완료 유지), 세션 로그 본문
7. **오프라인 큐**: N/A (서버 없음)
8. **E2E/k6**: N/A — 검증: 배지 사라짐(mShowBadge=false), "아직 변경 이력이 없습니다" 안내, IP 변경 110.70.58.185→39.7.50.166, 홈 "1:54:44 · 다음 변경까지 · 14:42 예정" 정확, 노티 완전 형식("현재 IP 39.7.50.166 · 예상 시간 01:55"), 앱 시작 시 서비스 자동 시작, ERROR 0

## 6차 세션 후속 (카운트다운 고정 버그) — 2026-08-16

- **문제**: 홈 화면 남은 시간이 고정 (1:49:44 그대로) — `tick` 상태가 1초마다 증가만 하고 **어디서도 읽히지 않아** Compose snapshot invalidation이 발생해도 구독 scope가 없어 재구성이 안 됨
- **수정**: HomeScreen의 `tick`(증가 전용) → `now`(System.currentTimeMillis()를 1초마다 갱신하는 상태)로 교체 — elapsed/remaining 계산이 now를 읽어 1초마다 재구성됨. **Compose 주의사항: 상태는 "읽는 곳"이 있어야 재구성된다**
- **S22 검증**: 1:47:38 → 1:47:24 감소 확인 (1초 단위 갱신)
- 문서: CHANGELOG.md(v0.3.1 카운트다운 수정), 세션 로그 본문

---

## 5차 세션 (v0.3 홈 화면 디자인 개선) 요약

---

## 4차 세션 (v0.2.1) 요약

1. **무엇을**: v0.2 사용자 요구사항 6종 구현 + S22 실검증 (T-9~12)
2. **플랫폼**: android (Galaxy S22 SM-S901N, Android 16/API 36, adb serial R5CT215F4QK)
3. **빌드 결과**: `./gradlew assembleDebug` 성공 (에러 0) — v0.2. 알림 중복 수정 후 재빌드 2회
4. **남은 TODO**: (없음 — T-8~12 완료) v0.3 후보: 자동 주기 단축 테스트(주기 경과 후 실제 자동 변경), 타일 상태 동기화
5. **다음 에이전트 전달**:
   - **API 36 추가 발견**: `Settings.ACTION_TETHERING_SETTINGS`가 API 36에서 **제거됨** → `ACTION_WIRELESS_SETTINGS`로 교체 (javap으로 확인)
   - **알림 중복 원인**: 서비스+ViewModel이 동일 RotationEngine 콜백 구독 → 알림 발행을 RotationEngine 내부로 통합 (context 주입, CHANNEL_EVENT)
   - **POST_NOTIFICATIONS**: Manifest 선언 + MainActivity 런타임 요청. 재설치 시 Android가 자동 복원함 (granted 유지)
   - **자동 IP 변경 토글 = 서비스 라이프사이클**: setEnabled→startForegroundService/stopService. adb로는 exported=false라 서비스 직접 시작 불가
   - **Compose Slider 주의**: 탭 점프는 steps 스냅 미적용, 드래그만 스냅 적용(30분 단위 정상). uiautomator dump의 y좌표는 스크롤로 이동하므로 최신 dump 기준 확인
   - IP 변경 실검증: 175.223.19.98→39.7.47.142→110.70.54.232 (핫스팟 TetheredState 유지)
   - adb 잠금 화면: mWakefulness=Dozing이면 화면 OFF → keyevent 26으로 wake, 패턴 잠금은 사용자 해제 필요
6. **문서 업데이트**: TODO.md(T-9~12 완료), CHANGELOG.md(v0.2), PLAN_v1.0_android.md(v0.2 반영 — 이전 세션), 세션 로그 본문
7. **오프라인 큐**: N/A (서버 없음)
8. **E2E/k6**: N/A (단독 앱) — v0.2 검증 완료: 주기 슬라이더 30~720분/30분 단위, 주기 내 스킵 로그, 기록 초기화, 알림 2건(시작+완료), 토글-서비스 연동, 셀룰러 판단(activeNetwork=CELLULAR)

---

## 2차 세션 (v0.1.1) 요약

1. **무엇을**: T-8 진행 — S22 실검증 + API 36 제약 극복. Shizuku 시작(13.6.0 libshizuku.so 직접 실행), 권한 부여, TC-01 성공
2. **플랫폼**: android (Galaxy S22 SM-S901N, Android 16/API 36, adb serial R5CT215F4QK)
3. **빌드 결과**: `./gradlew assembleDebug` 성공 (에러 0) — v0.1.1
4. **남은 TODO**: T-8 나머지 — TC-02(재시도), TC-03(에어플레인 폴백), TC-04~08, DoD 마무리
5. **다음 에이전트 전달**:
   - Shizuku 13.x 시작: 앱 UI "명령어 보기" → `adb shell /data/app/.../lib/arm64/libshizuku.so` 직접 실행 (start.sh 없음)
   - API 36 제약: `am broadcast AIRPLANE_MODE` 차단 → `cmd connectivity airplane-mode` 사용(AirplaneController 수정 완료). TetheringManager 리플렉션/`cmd wifi start-softap` TETHER_PRIVILEGED로 차단 → 핫스팟 재시작 불가
   - **새 전략**: DataController(`svc data disable/enable`) 우선 — 핫스팟 무중단 IP 변경 실검증 완료 (110.70.15.195→175.223.19.98)
   - `adb install` 실패 시 `-t` 플래그 사용, adb 단절 시 kill-server/start-server
6. **문서 업데이트**: PLAN_v1.0_android.md(전략 변경/API 36 제약), TODO.md(T-3~T-4 완료), CHANGELOG.md(v0.1.1), error_message_ko.json(NET-0003)
7. **오프라인 큐**: N/A (서버 없음)
8. **E2E/k6**: N/A (단독 앱) — TC-01 성공 기록: 데이터 재연결 1회 시도 → IP 변경 성공 + 핫스팟 TetheredState 유지 + ERROR 0
---

## 세션 7차 — v0.3.1 릴리즈 배포 + 랜딩/README 설치 가이드 (2026-08-16 13:2x~)

1. **무엇을**: T-완료 — 릴리즈 서명 설정 + S22 릴리즈 설치 + GitHub 배포 + 랜딩 페이지(설치 방법 세밀) + README + Pages
2. **플랫폼**: android (단독)
3. **빌드 결과**: `assembleRelease` 성공 (19s) — app-release.apk 11.4MB, 서명 28d941f9, v0.3.1(4). S22 설치 성공 (디버그→릴리즈 전환 위해 uninstall 후 재설치). 로그 ERROR 0
4. **남은 TODO**: 없음 (v0.4 대기 — 자동 주기 경과 후 실제 자동 변경 실검증)
5. **전달**: local.properties 커스텀 키는 AGP 미노출 → build.gradle.kts에서 Properties 명시 파싱 (import java.util.Properties + rootProject.file). gh 2.96.0엔 `release publish` 없음 → `gh release edit --draft=false`. keystore/암호는 local.properties (커밋 제외)
6. **문서 업데이트**: CHANGELOG(v0.3.1 릴리즈 배포), README.md(설치 가이드), index.html(랜딩)
7. **배포**: https://borasarang.github.io/SpotShift/ (Pages main 루트, HTTP 200) · Release v0.3.1 APK 첨부
8. **설치 가이드 핵심**: Shizuku(무선 디버깅 활성화) → APK(알 수 없는 소스 허용 — 브라우저별 경로) → 권한 요청(항상 허용) → 주기 설정 + 핫스팟 ON. 해외(일본) 사용 안내 포함

## 세션 8차 — 랜딩 재디자인 + 스크린샷 교체 (2026-08-16 14:0x~14:4x)

1. **무엇을**: 랜딩 3D 히어로 → 실제 S22 스크린샷 교체, 스크린샷 갤러리 제거, README 메인 화면 1장만 유지, 랜딩 전면 재디자인(밝은 편집적 스타일 + 모바일 고정 다운로드 바), 하단 CTA → GitHub 링크
2. **플랫폼**: android (배포/랜딩)
3. **빌드 결과**: 빌드 불필요(HTML). Pages 재배포 4회 모두 HTTP 200. 스크린샷 3장(홈/설정/알림) 서빙 200
4. **남은 TODO**: 없음 — v0.4 대기 (자동 주기 경과 후 실제 자동 변경 실검증)
5. **전달**: 텍스트 전용 모델 → 스크린샷은 adb screencap + uiautomator dump로 구조 검증 후 촬영. 알림 스크린샷은 JPEG 변환(2.5MB→294KB). 사용자 판단: 랜딩 "어색하지만 큰 문제 아님 — 넘어감"
6. **문서 업데이트**: index.html(재디자인), README.md(메인 스크린샷 1장), docs/screenshots/android/* (스크린샷 3장)
7. **배포 상태**: https://borasarang.github.io/SpotShift/ 라이브 · Release v0.3.1 APK 첨부됨 · 릴리즈 서명으로 S22 설치 완료
8. **E2E/k6**: 해당 없음 (단독 앱, 서버 없음)
