# UI 리디자인 — Calm Utilitarian 디자인 시스템 적용

작성일: 2026-07-08

## 배경

기존 화면은 기본 Material3 테마 그대로에 라디오버튼/멀티라인 텍스트로 두 차량 정보를 한 번에 나열하는 구조라 밋밋했다 (`/plan-design-review`에서 디자인 완성도 2/10 평가). "아주 구려.. 좀 예쁘게 만들어줘" 요청에 따라 `/design-consultation`으로 DESIGN.md를 먼저 확정한 뒤 실제 화면에 적용했다.

## 디자인 방향

- **핵심 인상:** "버스 놓칠 걱정 없이 안심하게 기다릴 수 있다"
- **방향:** Calm Utilitarian — 카카오버스/네이버지도처럼 여러 노선을 밀도 높게 보여주는 대신, 버스 1개만 보는 이 앱의 특성에 맞춰 큰 카운트다운 숫자 하나를 중심으로 한 여유로운 화면
- **색상:** 급함(빨강/주황)이 아닌 안정감을 주는 파랑(`#1C6E8C`)/앰버(`#C08A3E`, 3분 이하일 때만) 계열
- **타이포그래피:** Pretendard (ExtraBold/Bold/Medium/Regular), 기본 시스템 폰트 배제
- 전체 내용은 `DESIGN.md` 참고

## 구현 내역

### 리소스
- `res/values/colors.xml` 재작성 (accent/warn/text/surface 팔레트), `res/values-night/colors.xml` 다크모드 대응
- `res/values/themes.xml`에 colorPrimary 등 팔레트 연결
- `res/font/pretendard_{extrabold,bold,medium,regular}.otf` 추가 (orioncactus/pretendard, OFL 라이선스)
- 신규 drawable: `bg_button_primary`, `bg_button_outline`, `bg_segmented`, `bg_segment_checked`, `selector_segment_background`, `pulse_dot`
- 신규 color selector: `selector_segment_text`

### 레이아웃 (`activity_main.xml`)
- 상단: 펄스 점(모니터링 중 표시) + 조용한 노선 라벨
- 중앙: 큰 카운트다운 숫자(Hero) + 단위 + 부가정보(정류장 수·잔여좌석), 정보 없을 땐 안내 문구로 대체
- 하단: 세그먼트 형태로 재구성한 차량 선택(기존 RadioGroup 재사용, 시각만 변경) + 시작(채움)/종료(아웃라인) 버튼

### 코드
- `BusMonitorService`: 상태 브로드캐스트에 `EXTRA_ETA_MINUTES`/`EXTRA_LOCATION_NO`/`EXTRA_SEAT_COUNT`를 구조화된 값으로 추가 전송 (기존 알림용 텍스트 포맷은 유지)
- `MainActivity`: 기존의 "두 차량을 한 텍스트뷰에 문자열로 합쳐서 표시"하던 로직을 제거하고, 구조화된 값을 받아 Hero 숫자/캡션/아이들 문구를 직접 렌더링하도록 변경. 모니터링 중일 때 펄스 점 애니메이션(1.6s 반복) 추가

## 검증

```bash
./gradlew clean test lintDebug assembleDebug
```
결과: BUILD SUCCESSFUL, Lint 0 에러, 유닛 테스트 전체 통과.

플립(R3CX705W62D)에 `adb install -r`로 설치 완료. 실제 화면 확인은 사용자 진행.

## 확인 리스트 (사용자 진행)
1. 화면 열었을 때 파랑 계열 색상 + Pretendard 폰트로 보이는지 (기본 시스템 폰트로 보이면 폰트 리소스 미적용 상태)
2. 차량 선택이 세그먼트(알약형 토글)처럼 보이는지, 탭 시 전환되는지
3. "모니터링 시작" 눌렀을 때 배터리 최적화 다이얼로그 → 큰 숫자로 도착정보가 바뀌는지
4. 모니터링 중 노선 라벨 옆 점이 깜빡이는지(펄스 애니메이션)
5. 도착까지 3분 이하로 남으면 숫자 색이 파랑에서 앰버로 바뀌는지
6. 다크모드 전환 시 배경/텍스트 색이 자연스럽게 바뀌는지
