# 차량 탭 전환 시 알림 중복 발송 버그 수정 (v1.2.0.0 이후 회귀)

## 증상

v1.2.0.0(차량 번호 기반 추적 + park 방식 history 보존)을 배포한 뒤에도, "이번 차량"/"다음 차량" 탭을 전환하면 이미 보낸 임계값 알림(예: 15분 전)이 다시 오는 경우가 실사용에서 보고됨.

## 근본 원인

1. v1.2.0.0의 history 보존 방식은 단일 슬롯(parked_*)이었음. A→B→A로 전환하면 두 번째 전환(B→A) 시 A의 미소비 history를 B의 데이터로 덮어써서 유실됨.
2. 1차 수정으로 도입한 2슬롯 캐시(history_plate_0/1)도 실사용 패턴에서 부족했음: "다음 차량" 슬롯은 시간이 지나며 실제로 다른 물리 버스로 자연스럽게 교체되므로, 세션 도중 3대 이상의 plate가 히스토리 슬롯을 다툴 수 있었고, 두 슬롯이 모두 다른 plate로 채워졌을 때 "슬롯 0을 무조건 덮어쓰는" fallback이 아직 유효한 history를 파괴할 수 있었음.
3. 또한 `switchTarget()`이 UI 스레드에서 SharedPreferences를 직접 mutate했기 때문에, 백그라운드 poll 스레드의 "알림 발송 → 상태 저장" 시퀀스와 겹치는 race가 있었음 (generation guard로 저장 유실은 막았지만, 알림 발송 자체를 막지는 못함).

## 재설계

- `switchTarget()`은 더 이상 prefs를 직접 mutate하지 않고 `switch_pending` 플래그만 세팅한 뒤 `refreshNow()`로 즉시 poll을 트리거함.
- 실제 전환 로직(`applyTargetSwitch`)은 `pollArrival()` 맨 앞에서, poll 스레드(=서비스 전용 단일 스레드 executor)에서만 실행됨. 30초 주기 poll과 강제 poll이 모두 같은 단일 스레드 executor 큐를 통해 순차 실행되므로, 전환과 poll이 물리적으로 겹칠 수 없음.
- history 캐시의 eviction 정책: 두 슬롯이 모두 다른 plate로 채워져 있을 때, 현재 poll의 실시간 plateNo1/plateNo2와 대조해 "이미 노선에서 사라진(non-live)" 슬롯을 우선 evict. 두 슬롯이 모두 아직 live인데 새로 파킹하려는 plate가 non-live라면, 그 history는 버리고(drop) live 슬롯은 건드리지 않음.

## Codex 리뷰에서 추가로 발견/수정한 이슈

1. `selectedVehicle`을 `consumeSwitchPending()`보다 먼저 읽어서 switch가 무시될 수 있는 race → 읽기 순서를 switch 적용 이후로 이동.
2. 위 eviction 정책에서 "두 슬롯 다 live"인데 evict가 필요한 경우 여전히 유효한 history를 지울 수 있었음 → 들어오는 plate가 non-live면 push 자체를 drop하도록 수정.
3. switch 직후에도 이미 진행 중이던 poll이 구 타겟의 임계값 알림을 새로 띄울 수 있었음(사용자가 보고한 증상과 동일 패턴) → `applyTargetSwitch()` 내부에서도 `cancelThresholdNotifications()`를 재호출해, UI 스레드의 즉시 cancel과 poll 스레드의 전환 시점 cancel 두 곳에서 정리하도록 함.

## 검증

- `./gradlew test lintDebug assembleDebug` — BUILD SUCCESSFUL
- Codex adversarial 리뷰 2라운드 진행, 최종 결론 "머지해도 됨"

## 범위

이번 라운드는 사용자 지시에 따라 수정 + 빌드까지만 진행함. 커밋/PR/머지는 아직 하지 않음.

## Unit test 보강

`BusMonitorService`는 `Service`/`Context`/`SharedPreferences`에 강하게 결합돼 있어 순수 JUnit으로 직접 테스트하기 어려움. 기존 `EtaPresenter` 패턴을 따라 이번에 고친 핵심 판단 로직 3개를 Context 없이 동작하는 순수 클래스로 추출하고, 각각에 대해 세밀한 케이스를 작성함(동작은 변경 없음, 리팩터링만):

- `HistoryCacheLogic` — `pushHistory`/`restoreFromHistoryIfMatching`의 슬롯 선택·evict 정책 (`chooseSlotToPush`, `findSlot`). A↔B 반복 전환, 3번째 plate 등장 시 evict 우선순위, "둘 다 live인데 incoming이 non-live면 drop" 케이스 포함.
- `VehicleTargetResolver` — `resolveEffectiveVehicle`의 plate 매칭 판단 (`resolve`, `isBootstrap`). 부트스트랩/슬롯 추적/plate 메타데이터 blip fallback 케이스 포함.
- `ThresholdCrossingLogic` — `notifyCrossedThresholds`의 임계값 교차 판단. 콜드스타트 시 "가장 타이트한 미통지 임계값만 선택"하는 특이 동작, 히스토리 복원 후 이미 통지된 임계값 스킵, 대폭 점프 시 중간 임계값 일괄 발화 등 포함.

테스트 결과: `HistoryCacheLogicTest`(13), `VehicleTargetResolverTest`(13), `ThresholdCrossingLogicTest`(13) 전부 통과, 기존 테스트 포함 총 68개 전부 green. `./gradlew test lintDebug assembleDebug` — BUILD SUCCESSFUL.
