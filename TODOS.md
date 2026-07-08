# TODOS

## Versioning

### versionCode 필드 폭 충돌 가능성

**What:** `build.gradle.kts`의 `computedVersionCode` 공식(`major*1000 + minor*100 + patch*10 + build`)은 minor/patch/build 각 자리가 10 이상이 되면 다른 버전과 versionCode가 충돌한다 (예: 1.0.10.0과 1.1.0.0이 동일한 1100).

**Why:** MinseoStore 등에서 versionCode로 업데이트 여부를 판단하는데, 충돌하면 최신 버전이 이전 버전보다 낮은 코드를 갖거나 두 릴리스가 구분 안 될 수 있다.

**Context:** 이 공식은 워크스페이스 루트 CLAUDE.md에 문서화된 표준 패턴을 그대로 적용한 것으로, 다른 Minseo 앱들도 동일한 패턴을 쓸 가능성이 높다. Minseo9만 개별적으로 고치면 워크스페이스 전체와 불일치가 생기므로, 고칠 거면 워크스페이스 표준 자체를 바꿔야 한다. patch/build가 10 미만으로 유지되는 동안은 실제로 발생하지 않는다.

**Effort:** S (필드 폭을 넓히는 것 자체는 간단하지만 워크스페이스 전체 컨벤션 변경이 필요)
**Priority:** P3
**Depends on:** 워크스페이스 CLAUDE.md 버전 관리 규칙 변경 논의

## Battery Optimization

### 삼성 절전 앱 관리 자동 안내 불가

**What:** 배터리 최적화 예외 요청은 표준 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`만 호출하고, 삼성 One UI의 별도 "절전 앱 관리" 화면으로 직접 딥링크하지는 못한다. 다이얼로그 안내 문구로만 유도한다.

**Why:** 테스트 단말이 전부 삼성 기기라 표준 예외만으로는 백그라운드에서 서비스가 꺼질 수 있다.

**Context:** 삼성 절전 앱 관리는 공개 API/딥링크가 One UI 버전마다 달라 안정적으로 호출할 방법이 없다. 안내 문구가 현재의 현실적인 완화책이다.

**Effort:** M (기기별 분기 처리 필요, 유지보수 부담)
**Priority:** P3
**Depends on:** None

## Completed

### GBIS API 연동 + Foreground Service 모니터링 구현
**Completed:** v1.0.0.0 (2026-07-07)

### VERSION 파일 + computedVersionCode 자동화 적용
**Completed:** v1.0.0.0 (2026-07-07)

### 배터리 최적화 예외 요청 UX 추가
**Completed:** v1.0.0.0 (2026-07-07)
