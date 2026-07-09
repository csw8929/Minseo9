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

## Reliability

### ACTION_STATUS 브로드캐스트 위조 가능성 (API 24-32)

**What:** `BusMonitorService`가 `ACTION_STATUS`를 `sendBroadcast` + `setPackage`로 발행하고, `MainActivity`는 `RECEIVER_NOT_EXPORTED`로 등록해서 수신한다. API 33+에서는 OS가 `RECEIVER_NOT_EXPORTED`를 강제하지만, API 24-32(현재 minSdk=24)에서는 context-registered receiver에 대해 이 플래그가 OS 레벨로 강제되지 않아 같은 기기에 설치된 다른 앱이 동일 액션 문자열로 위조된 브로드캐스트를 보내 ETA/좌석/정류장 값을 조작할 수 있다.

**Why:** 이번 리디자인으로 브로드캐스트가 구동하는 화면 요소가 작은 상태 텍스트에서 큰 카운트다운 숫자(사용자가 버스를 놓치지 않기 위해 의존하는 핵심 신호)로 커져서 영향 범위가 커졌다.

**Context:** Claude adversarial 리뷰(2026-07-08)에서 발견. 실제 테스트 단말(탭/폴드/플립/미니/xr)은 모두 API 33+로 추정되어 즉각적 위험은 낮지만, minSdk=24 선언과는 맞지 않는 신뢰 경계다.

**Effort:** M (브로드캐스트 대신 바운드 서비스 콜백이나 인메모리 pub/sub로 교체 필요)
**Priority:** P2
**Depends on:** None

### 도착 시 유령 포그라운드 알림 레이스

**What:** `updateForegroundNotification()`은 `mainHandler.post()`로 비동기 처리되는데, 버스가 도착해 `finishMonitoring()`이 같은 `pollArrival()` 호출 안에서 동기적으로 `stopForeground()`/`stopSelf()`를 부르면, 나중에 실행되는 posted runnable이 이미 종료된 서비스에 대해 알림 ID 1001을 다시 `notify()`할 수 있다. 결과적으로 모니터링이 끝났는데도 "감시 중" 알림이 유령처럼 남을 수 있다.

**Why:** 사용자가 실제로는 끝난 모니터링을 계속 진행 중이라고 오인할 수 있다.

**Context:** Claude adversarial 리뷰(2026-07-08)에서 발견. 이 리디자인 PR과 무관한 기존 로직(`BusMonitorService.pollArrival`/`finishMonitoring`)의 레이스 컨디션.

**Effort:** S-M (posted runnable 안에서 `isMonitoringActive`/인스턴스 종료 플래그 확인, 또는 `finishMonitoring`에서 대기 중인 handler 메시지 취소)
**Priority:** P2
**Depends on:** None

### 모니터링 시작 시 API 이중 호출

**What:** `validateAndStartMonitoring()`이 검증용으로 GBIS API를 한 번 호출하고 결과를 버린 뒤, `BusMonitorService.start()`가 `EXTRA_FORCE_REFRESH`로 즉시 자체 조회를 한 번 더 한다. 시작 버튼을 누를 때마다 API 호출이 2배가 된다.

**Why:** data.go.kr 공개 API 키는 일일 호출 한도가 있어, 시작/종료를 반복하면 한도 소모가 누적된다.

**Context:** 이번 세션에서 추가한 `validateAndStartMonitoring` 검증 플로우가 원인. Codex/Claude adversarial 리뷰(2026-07-08) 공통 지적.

**Effort:** S (검증 시 이미 가져온 `Arrival` 값을 서비스 시작 시 재사용하도록 전달)
**Priority:** P3
**Depends on:** None

### formatStatus의 선택 차량 재조회 TOCTOU

**What:** `pollArrival()`이 `selectedVehicle`을 한 번 읽고, `formatStatus()`가 내부에서 `getSelectedVehicle(this)`를 다시 읽는다. 두 읽기 사이에 사용자가 라디오 버튼을 바꾸면 같은 poll 사이클에서 표시 텍스트의 "▶" 표시와 실제 임계값/종료 판단에 쓰인 차량이 서로 어긋날 수 있다.

**Why:** 발생 확률은 낮지만 발생 시 사용자에게 잘못된 차량이 선택된 것처럼 보이는 조용한 불일치.

**Context:** Claude adversarial 리뷰(2026-07-08)에서 발견. 이 PR과 무관한 기존 로직.

**Effort:** S (이미 읽은 `selectedVehicle` 값을 `formatStatus`/`formatVehicleLine`에 인자로 전달)
**Priority:** P3
**Depends on:** None

### resetNotificationState()가 이미 실행 중인 서비스의 인메모리 상태를 지우지 못함

**What:** `resetNotificationState(Context)`는 static이라 SharedPreferences만 지우고, 이미 살아있는 `BusMonitorService` 인스턴스의 `notificationStates` 맵은 건드리지 못한다. 현재 모든 호출 지점(`stop()`, `startMonitoring()` 직전)이 서비스가 곧 재시작되는 시점에만 호출되어 우연히 안전하지만, 이 보장은 호출 지점의 관례에만 의존한다.

**Why:** 향후 다른 경로(예: 서비스가 살아있는 동안 리셋을 호출하는 코드)가 추가되면 인메모리 상태와 SharedPreferences가 조용히 어긋날 수 있다.

**Context:** Claude adversarial 리뷰(2026-07-09)에서 발견. 현재 코드에서는 실제로 발생하지 않는 잠재적 함정.

**Effort:** S (인스턴스 메서드로 전환하거나, 살아있는 서비스에 대해 맵도 함께 초기화)
**Priority:** P3
**Depends on:** None

### notifyThreshold()의 알림 ID가 차량을 구분하지 않음

**What:** `BusArrivalNotifier.notificationId()`는 `2000 + threshold`만 사용해 어떤 차량(이번/다음)의 알림인지 구분하지 않는다. 현재는 선택된 차량 하나만 알림을 보내므로 실제로 충돌하지 않지만, 데이터 모델(차량별 독립 상태)과 알림 발행 로직(선택된 차량만 발행) 사이에 암묵적 결합이 있다.

**Why:** 향후 두 차량을 동시에 알림받는 기능을 추가하면 이 ID 스킴이 충돌한다.

**Context:** Codex 리뷰(2026-07-09)에서 발견. 현재 코드 경로에서는 재현되지 않음.

**Effort:** S (알림 ID에 차량 슬롯 포함)
**Priority:** P3
**Depends on:** None

## Completed

### GBIS API 연동 + Foreground Service 모니터링 구현
**Completed:** v1.0.0.0 (2026-07-07)

### VERSION 파일 + computedVersionCode 자동화 적용
**Completed:** v1.0.0.0 (2026-07-07)

### 배터리 최적화 예외 요청 UX 추가
**Completed:** v1.0.0.0 (2026-07-07)
