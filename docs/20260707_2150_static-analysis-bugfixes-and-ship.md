# 정적분석 버그 수정 및 v1.0.0.0 배포

작성일: 2026-07-07

## 개요

기존 구현(설계는 `20260707_bus-arrival-notification-setup.md` 참고)에 대해 Android Lint 정적분석과 코드 리뷰를 진행하고, 발견된 버그를 수정해 `fix/static-analysis-bugs` 브랜치로 배포했다.

## 1. Android Lint 정적분석 결과

`./gradlew lintDebug` 실행 결과: **에러 2건, 경고 28건**.

### 에러 (크래시/오동작 유발)

1. **`GbisArrivalClient.java` — `NewApi`**: `URLEncoder.encode(String, Charset)` 오버로드는 API 33 이상에서만 지원되는데 minSdk는 24. API 33 미만 기기에서 API 호출마다 크래시.
2. **`MainActivity.java` — `UnspecifiedRegisterReceiverFlag`**: API 33 미만 분기의 `registerReceiver`에 EXPORTED/NOT_EXPORTED 플래그 누락.

### 경고 (수정한 것들)

- `WakelockTimeout`: `wakeLock.acquire()`에 타임아웃 없음
- `ObsoleteSdkInt`: minSdk 24에서 항상 참인 `SDK_INT >= N`, `>= M` 체크
- `UnusedResources`: `R.color.black/white`, `R.string.monitor_started`
- `SetTextI18n`: 에러 메시지 문자열 직접 concat

## 2. 코드 리뷰로 추가 발견한 로직 버그

Lint로는 못 잡는 프로젝트 로직 버그를 수동 리뷰로 발견:

- **`BusMonitorService.pollArrival()`의 "정보 없음" 판정 버그**: 선택된 차량과 무관하게 항상 `predictTime1`(1번 차량) 기준으로 판정. "다음 차량"(2번 차량)을 선택한 상태에서 1번 차량 정보만 없어도 전체를 "정보 없음"으로 오판하고 알림을 건너뜀.

## 3. 스펙 리뷰 + Adversarial 리뷰 (Claude + Codex)

`/ship` 워크플로우의 스페셜리스트 리뷰(Testing/Maintainability)와 adversarial 리뷰(Claude 서브에이전트 + Codex)에서 추가로 발견해 수정한 항목:

1. **VERSION 파일 미추적(P0, Claude·Codex 둘 다 지적)**: `build.gradle.kts`가 `VERSION`을 무조건 읽도록 바꿨는데 `VERSION` 파일이 git에 추가되지 않아 다른 환경/CI에서 클린 체크아웃 시 빌드 자체가 실패했을 것. → `git add VERSION`으로 해결.
2. **WakeLock 경쟁 상태(Claude)**: 폴링 스레드의 `renewWakeLock()`과 서비스 종료 시 메인 스레드의 `releaseWakeLock()`이 동기화 없이 같은 필드를 건드려, 서비스 종료 후에도 WakeLock이 최대 10분간 재무장될 수 있었음. → 세 메서드(`acquireWakeLock`/`renewWakeLock`/`releaseWakeLock`)를 `synchronized`로 묶어 해결.
3. **폴링 영구 중단 위험(Claude)**: `pollArrival()`이 `IOException`만 잡고 있어, 예상치 못한 `RuntimeException`이 한 번이라도 발생하면 `ScheduledExecutorService`가 이후 모든 폴링을 조용히 영구 중단함(재시작 전까지 복구 불가, 사용자에게 아무 신호 없음). → `RuntimeException`도 잡아서 상태로 알림.
4. **배터리 최적화 다이얼로그 누수(Claude)**: 화면 회전/폴더블 접기-펼치기로 Activity가 재생성되면 `AlertDialog`가 `WindowLeaked`로 누수되고, 죽은 Activity에 대해 버튼을 누르면 문제가 될 수 있었음. → `onStop()`에서 다이얼로그를 dismiss하도록 수정.
5. **배터리 예외 요청 인텐트의 `SecurityException` 미처리(Codex)**: OEM/정책 제한 환경에서 `ActivityNotFoundException` 외에 `SecurityException`도 던질 수 있음. → catch 절에 추가.
6. **선택 차량 정보 일시 없음 시 상단 알림 미갱신(Codex)**: "정보 없음" 상태를 인앱 브로드캐스트로는 알리지만 포그라운드(상태바) 알림은 갱신하지 않아, 오래된 도착정보가 계속 표시될 수 있었음. → 해당 분기에서도 상단 알림 갱신.

### 고치지 않고 TODOS.md로 남긴 항목

- **`versionCode` 필드 폭 충돌 가능성**(Claude·Codex 둘 다 지적): `major*1000+minor*100+patch*10+build` 공식은 각 자리가 10 이상이면 다른 버전과 충돌 가능. 워크스페이스 루트 CLAUDE.md의 표준 패턴을 그대로 적용한 것이라 Minseo9만 개별 수정하면 워크스페이스 전체와 불일치가 생김 → P3 TODO로 기록.
- **삼성 절전 앱 관리 자동 딥링크 불가**: 공개 API가 없어 안내 문구로만 대응 중 → P3 TODO로 기록.

## 4. 이번 기회에 같이 처리한 설계 문서 잔여 항목

`/ship`의 Plan Completion Audit에서 `office-hours` 설계 문서의 미완료 항목 2개를 발견해 사용자 승인 하에 같이 구현:

1. **VERSION 파일 + `computedVersionCode` 자동화** (워크스페이스 표준 패턴 적용)
2. **배터리 최적화 예외 요청 UX** (표준 예외 요청 + 삼성 절전 앱 관리 안내 다이얼로그)

## 5. 검증

```bash
./gradlew clean test lintDebug assembleDebug
```

결과: `BUILD SUCCESSFUL`, Lint 0 에러, 유닛 테스트(`GbisArrivalClientArrivalTest`, `GbisArrivalClientEncodeTest`) 전체 통과.

## 6. 배포

- 브랜치: `fix/static-analysis-bugs` (base: `master`)
- 버전: `v1.0.0.0` (최초 VERSION 파일 도입)
- 4개 커밋으로 분리(bisectable): encode 크래시 수정+테스트 → BusMonitorService 로직/WakeLock/폴링 수정 → MainActivity 리시버/배터리 UX → 버전/체인지로그/TODOS
- PR: `csw8929/Minseo9` 저장소에 `fix/static-analysis-bugs` → `master`
