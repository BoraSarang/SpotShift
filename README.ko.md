# SpotShift — 핫스팟 IP 자동 변경기

Android 기기를 핫스팟으로 사용하며, 설정한 주기마다 모바일 네트워크 IP를 자동으로 바꿔주는 무루트 앱입니다. IP 변경 후 모바일 데이터와 핫스팟을 원래 상태로 자동 복원합니다.

- **무루트**: Shizuku 기반 (루트 권한 불필요)
- **서버 없음**: 모든 처리가 기기 안에서만 이뤄짐 (데이터 수집 없음)
- **Android 8.0 (API 26) 이상** · 갤럭시 A90(SM-A908N, Android 12) 실기기 검증
- 랜딩 페이지: https://borasarang.github.io/SpotShift/

---

## 다운로드

| 항목 | 링크 |
|------|------|
| APK (v0.8.0) | [GitHub Releases](https://github.com/BoraSarang/SpotShift/releases/latest) — `app-release.apk` |
| 소스 코드 | [BoraSarang/SpotShift](https://github.com/BoraSarang/SpotShift) |
| 버그 신고 | [GitHub Issues](https://github.com/BoraSarang/SpotShift/issues) |
| 문의 | leeborasarang@gmail.com |

> **해외(일본 등) 사용**: 이 페이지와 APK는 한국·일본 어디서나 접속·다운로드됩니다. Play 스토어가 필요 없으며, IP 변경은 로컬에서 이뤄지므로 로밍 상태와 무관하게 동작합니다.

![메인 화면](docs/screenshots/android/v0.8.0_home.png)
![기록 탭](docs/screenshots/android/v0.8.0_history.png)

---

## 설치 방법 (상세 가이드)

### 준비물
- Android 8.0 이상 기기
- Shizuku 앱 (무루트 권한 시스템 — 필수)

### 1단계: Shizuku 설치

- **Play 스토어**: [Shizuku (moe.shizuku.privileged.api)](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)
- **GitHub**: [RikkaApps/Shizuku Releases](https://github.com/RikkaApps/Shizuku/releases/latest)

### 2단계: Shizuku 활성화 (무선 디버깅 방식 — 갤럭시 One UI 기준)

1. **개발자 옵션 켜기**: `설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드 번호` 7번 탭
2. `설정 → 개발자 옵션 → USB 디버깅` ON
3. `설정 → 개발자 옵션 → 무선 디버깅` ON (팝업이 뜨면 허용)
4. Shizuku 앱 실행 → **"무선 디버깅으로 시작"** 탭
5. 페어링 코드 입력: `설정 → 개발자 옵션 → 무선 디버깅 → 페어링 기기 쌍 연결`에서 코드 확인 → 입력 → **"항상 허용"** 체크
6. Shizuku 화면 상단이 **Running**이 되면 완료

> 부팅 후에는 Shizuku 앱을 다시 열어 재시작해야 합니다.

### 3단계: SpotShift APK 다운로드 + 설치

1. [GitHub Releases](https://github.com/BoraSarang/SpotShift/releases/latest)에서 `app-release.apk` 다운로드
2. 다운로드 알림에서 APK 탭 → "이 앱을 설치할 수 없습니다" 안내가 뜨면 **설정** 탭
3. 갤럭시 경로: `설정 → 생체 인식 및 보안 → 기타 보안 설정 → 알 수 없는 앱 설치` → 사용 브라우저 선택 → **"이 앱 허용" ON**
4. APK 다시 탭 → **설치** → **열기**

> **보안 안내**: "알 수 없는 소스 허용"은 선택한 브라우저에만 적용됩니다. `app-release.apk`(정식 서명) 외 출처 불명 APK는 설치하지 마세요.

### 4단계: 권한 연결 + 시작

1. 앱 실행 → **설정 탭** → **권한 요청** → Shizuku 다이얼로그에서 **"항상 허용"**
2. 설정 탭이 **"연결됨"**으로 바뀌면 성공
3. 설정 탭에서 **변경 주기** (30분~12시간) + 원하는 옵션 (셀룰러 전용 / 가드 / 소리 알림)
4. 기기 **핫스팟 ON** → 설정 탭 **자동 IP 변경** 토글 ON
5. 설정한 시각마다 IP 자동 변경 — **예상 변경 시각**은 홈 화면과 상태 알림에서 확인

> 홈의 **"IP 변경 시작"** 버튼은 그 순간 한 번만 바꾸는 **일회성 수동 변경**입니다. 자동 주기는 켜지지 않으며, 자동 On/Off는 **설정 탭 토글**(또는 퀵세팅 타일)로만 합니다.

---

## 기능

- **주기 자동 변경**: 30분~12시간 (30분 단위, 기본 2시간) 주기 설정, IP 자동 변경
- **자동 복원**: 모바일 데이터 재연결 우선 → 에어플레인 모드 폴백
- **셀룰러 관문**: 셀룰러일 때만 실행 (Wi-Fi 상태는 실행하지 않고 기록만)
- **핫스팟 꺼짐 안내**: 변경 시 핫스팟이 꺼져 있으면 설정 화면을 열어 안내 (Android 16 자동 ON 제한)
- **속도 검증**: 변경 직후 속도 측정 → 기준(1~50Mbps) 미만이면 자동 재변경(최대 5회)
- **속도 탭**: fast.com식 실시간 다운·업·지연 측정 + 측정 기록(개별 삭제/초기화 확인) + 저속 시 바로 IP 변경→재측정 비교
- **DUN 탭**: APN 선택 후 dun 추가/원복으로 테더링 속도 제한 해제 (Shizuku, 원본 type 보관 후 되돌리기)
- **무인 운용 가드**: 온도 가드(45°C·thermal emergency 시 연기) · 전원 감시(충전 해제·20% 이하 연기/알림) · 속도 감시(주기 외 저속 시 자동 변경) — 감시 실패는 페일오픈(변경은 진행)
- **상태 알림**: 현재 IP / 예상 변경 시각 / 진행 단계 (설정에서 OFF 가능)
- **기록 초기화**: 변경 이력 초기화 (확인 후 삭제)
- **연동 지원**: RelayConsole 호환 SDK v2 (정보 조회 + `autorotate` 브로드캐스트, 설정에서 차단 가능)
- **변경 이력 없음 처리**: 첫 실행 시 "아직 변경 이력이 없습니다" 안내

---

## 업데이트

같은 [GitHub Releases 페이지](https://github.com/BoraSarang/SpotShift/releases/latest)에서 새 APK를 받아 설치하면 됩니다. **같은 릴리즈 키로 서명**되므로 설정과 데이터가 유지된 채 업데이트됩니다.

## 개발

```bash
cd android
./gradlew assembleDebug --no-daemon     # 디버그 빌드
./gradlew assembleRelease --no-daemon   # 릴리즈 빌드 (서명: local.properties의 keystore 정보)
```

릴리즈 서명 키(`keystore/release.jks`)와 비밀번호는 `local.properties`에 분리 보관하며, 커밋에서 제외됩니다 (.gitignore).

## 버전 이력

- **v0.8.0 (2026-10-24)** — UI v2(Material3 Expressive·5탭·대형 화면 레일), DUN 탭(테더링 제한 해제), 온도·전원·속도 감시, 변경 출처 표시(수동/자동/속도 감시/외부 요청 + 요청자), 기록 필터
- **v0.5.0** — WorkManager 스케줄러 (백그라운드 재시작 크래시 수정), 속도 탭, 기록 초기화 확인
- **v0.3.1** (2026-08-16) — 첫 릴리즈. 카운트다운 수정 · 서비스 자동 복원 · 배지 제거 · IP 재조회
- **v0.3.0** — 홈 개선 (예상 변경 시각 · 진행 단계 · 문의 섹션)
- **v0.2.x** — 사용자 요구사항 6종 (셀룰러 전용/주기/스킵/기록 초기화/알림/토글)
- **v0.1.x** — 초기 골격 + 실기기 검증 (데이터 재연결 우선 전략)

## 라이선스

MIT — [LICENSE](LICENSE) 참조. 문의: leeborasarang@gmail.com