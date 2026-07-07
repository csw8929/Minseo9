# 버스 도착 알림 서비스 (Minseo9) — 설계 및 GBIS API 연동 진행상황

작성일: 2026-07-07

## 1. 개요

Minseo9는 "정류장 + 버스를 지정하면 도착까지 20/15/10/5/3/1분 단위로 반복 알림을 보내는" 개인/가족용 Android 앱이다. `/office-hours` 세션을 통해 설계를 확정했고(3라운드 어도버서리얼 리뷰 후 9/10 PASS), 현재는 데이터 소스인 경기도 버스정보(GBIS) API 연동을 준비 중이다.

전체 설계 문서: `C:\Users\USER\.gstack\projects\Minseo9\USER-unknown-design-20260707-210110.md` (Status: APPROVED)

## 2. 설계 요약

- **핵심 기능**: 정류장 1개 + 버스 1개 선택 → "모니터링 시작" → Foreground Service가 짧은 주기(초기값 30초)로 폴링 → 도착까지 20/15/10/5/3/1분 지점마다 알림
- **MVP 범위**: 단일 정류장 + 버스 1개, 동시 모니터링 1건, 검색/즐겨찾기 없음(하드코딩 또는 간단 입력)
- **배포**: 가족 전용, MinseoStore/Play Store 배포 없음, APK 직접 설치(`adb install -r`)
- **재사용 대상**: Minseo7의 `service/OverlayService.java`, `reminder/ReminderScheduler.java`, `reminder/ReminderReceiver.java`, `receiver/BootReceiver.java` 패턴
- **임계값 교차 처리**: 폴링 지연으로 여러 임계값을 동시에 지난 경우, 알림 1건에 병합해서 발송(예: "15분·10분 전입니다")
- **종료 조건**: 도착 확인(GBIS 응답에 도착 상태 필드가 있으면 그 기준, 없으면 ETA≤0) 또는 1분 알림 발송 후 자동 종료. 사용자 수동 종료도 가능
- **부팅 후 복구**: 재부팅 시 SharedPreferences에 저장된 모니터링 상태(정류장ID/버스/시작시각)가 있으면 BootReceiver가 자동으로 Foreground Service 재기동
- **알려진 리스크**: GBIS ETA 데이터 자체의 정밀도가 통상 ±1~2분 오차 가능 — "1분 단위 정밀 알림" 목표가 API 데이터 정밀도에 좌우됨. 실측 후 필요하면 "최근접 임계값 근사치 알림"으로 기대치 조정

## 3. GBIS API 신청 (완료)

data.go.kr(공공데이터포털)에서 아래 2개 API를 신청하고 **승인**받음:

1. **경기도_버스도착정보 조회** — https://www.data.go.kr/data/15080346/openapi.do
2. **경기도_정류소 조회** — https://www.data.go.kr/data/15080666/openapi.do (정류소명/번호로 정류소ID 조회)

승인된 두 API는 마이페이지에서 발급된 동일한 서비스키를 공유해서 사용한다(API마다 새 키가 생기는 게 아니라, 신청한 API 목록이 그 키에 묶이는 방식).

참고 매뉴얼:
- 정류소 조회 API 명세 — https://www.gbis.go.kr/gbis2014/publicService.action?cmd=mBusStation
- 버스 도착정보 항목조회 매뉴얼 — https://www.gbis.go.kr/gbis2014/publicService.action?cmd=mBusArrival
- 기반정보 항목조회 매뉴얼 — https://www.gbis.go.kr/gbis2014/publicService.action?cmd=mBaseInfo

## 4. 발급받은 API 키 확인된 요청/응답 스펙

### 4-1. 정류소 조회 (getBusStationListv2)

```
GET https://apis.data.go.kr/6410000/busstationservice/v2/getBusStationListv2
```

**요청 파라미터**

| 이름 | 타입 | 설명 |
|---|---|---|
| serviceKey | string | 인증키(공공데이터포털 발급) |
| keyword | string | 검색할 정류소명 또는 정류소번호 |
| format | string | 응답 포맷(json/xml) |

**응답 필드 (busStationList)**

| 이름 | 타입 | 설명 |
|---|---|---|
| stationId | int | 정류소아이디 (도착정보 조회 시 사용) |
| stationName | string | 정류소명 |
| mobileNo | string | 정류소번호 |
| regionName | string | 정류소 위치 지역명(시/군 단위, 예: "군포", "용인") |
| x, y | number | 좌표 |
| centerYn | string | 중앙차로 여부(N:일반, Y:중앙차로) |

응답 예시(resultCode 0=정상, 4=결과없음):

```json
{"response":{"msgHeader":{"queryTime":"...","resultCode":0,"resultMessage":"정상적으로 처리되었습니다."},"msgBody":{"busStationList":[{"stationId":225000376,"stationName":"12단지목련","regionName":"군포", ...}]}}}
```

### 4-2. 버스도착정보 조회 — 아직 실제 호출 테스트 안 함 (정류소ID를 아직 못 구함)

## 5. 로컬 환경 이슈 및 해결

### 5-1. API 키 파일 인코딩 문제 (해결됨)

- PowerShell의 `echo "..." > 파일` 리다이렉션은 기본적으로 **UTF-16LE + BOM**으로 저장한다.
- Bash/curl에서 그대로 읽으면 키 값에 null byte가 섞여 `curl` 오류(exit code 35) 발생.
- 해결: `iconv -f UTF-16LE -t UTF-8`로 변환 후 BOM(`\xEF\xBB\xBF`) 제거.
  ```bash
  iconv -f UTF-16LE -t UTF-8 .gbis-key | tr -d '\r\n' > .gbis-key.tmp && mv .gbis-key.tmp .gbis-key
  sed -i '1s/^\xEF\xBB\xBF//' .gbis-key
  ```
- `.gbis-key`는 `.gitignore`에 등록되어 커밋되지 않음.

### 5-2. Windows curl(schannel)의 SSL 오류 (해결됨)

- `apis.data.go.kr` 호출 시 `curl` exit code 35 (`CRYPT_E_NO_REVOCATION_CHECK`) 발생.
- Windows 기본 curl의 schannel 백엔드가 인증서 해지 여부(OCSP/CRL) 확인에 실패하면서 발생하는 문제로, 정부기관 사이트에서 흔히 발생.
- 해결: `curl` 호출 시 `--ssl-no-revoke` 옵션 추가.

## 6. API 동작 확인 (완료)

매뉴얼 예시(`keyword=12`)로 정상 동작 확인:

```bash
curl -sG --ssl-no-revoke "https://apis.data.go.kr/6410000/busstationservice/v2/getBusStationListv2" \
  --data-urlencode "serviceKey=$KEY" \
  --data-urlencode "format=json" \
  --data-urlencode "keyword=12"
```

→ `resultCode: 0`, 다수의 정류소 목록 정상 반환됨 (API 키/승인 상태 정상 확인).

## 7. 정류소 검색 결과

사용자 위치: **용인시 수지구 동천동**, 근처에 "현대홈타운2차" 아파트가 있고 **6900번** 버스가 지나는 정류소를 찾고 싶어함.

`동천동` 키워드 단독 조회로 아래 후보를 확인함:

| stationId | 정류소번호 | 정류소명 | 방향/비고 |
|---:|---:|---|---|
| 228000905 | 29111 | 동천동현대홈타운2차아파트 | 6900번 `routeDestName=광교차고지`, 반대 방향 |
| 228000883 | 29116 | 동천동현대홈타운2차아파트 | 6900번 `routeDestName=잠실종합운동장`, 다음 정류장이 동천동현대홈타운1차아파트인 방향으로 사용할 후보 |

6900번 노선 ID는 `234000027`로 확인됨.

이전 시도한 키워드와 결과:

| 키워드 | 결과 |
|---|---|
| 현대홈타운2차 | 0건 (resultCode 4) |
| 현대홈타운 | 0건 |
| 현대홈타운2차앞 | 0건 |
| 홈타운2차 | 0건 |
| 동천 | 0건 |
| 동천동 | 단독 명령으로 재시도 후 정상 조회됨 |
| 동천역 / 수지구청 / 죽전 | 테스트 스크립트가 반복문에서 한글 키워드가 깨지는 셸 인코딩 문제로 정상 실행 안 됨 |

**원인 추정**
- `getBusStationListv2`의 `keyword`는 정류소명/정류소번호 검색용인데, 아파트 이름이 정류소명에 그대로 포함되지 않는 경우가 많음(정류소명은 보통 도로명·교차로명·다른 랜드마크 기준으로 명명됨).
- 버스 노선번호(6900)로 정류소를 찾으려면 별도의 "노선정보 조회"/"노선별 정류소 조회" API를 추가로 신청해야 함(아직 신청 안 함).

**셸 이슈**: bash의 `for kw in "동천동" "동천역" ...` 형태로 여러 한글 키워드를 한 명령에 넣으면 인코딩이 깨지는 현상을 확인함. 한글 키워드는 **한 번에 하나씩** 별도 명령으로 실행해야 안전함.

## 8. 버스도착정보 조회 확인

### 8-1. 대상 호출

```
GET https://apis.data.go.kr/6410000/busarrivalservice/v2/getBusArrivalItemv2
```

요청 파라미터:

| 이름 | 값 |
|---|---|
| stationId | 228000883 |
| routeId | 234000027 |
| format | json |

### 8-2. 확인된 주요 응답 필드

| 필드 | 의미 |
|---|---|
| routeName | 노선번호. 6900 |
| routeDestName | 노선 목적지. `광교차고지` |
| predictTime1 | 첫 번째 도착 예정 시간. 분 단위 |
| predictTime2 | 두 번째 도착 예정 시간. 분 단위 또는 빈 문자열 |
| locationNo1 | 첫 번째 차량의 남은 정류장 수 |
| locationNo2 | 두 번째 차량의 남은 정류장 수 또는 빈 문자열 |
| stationNm1 | 첫 번째 차량의 현재/근접 정류소명 |
| stationNm2 | 두 번째 차량의 현재/근접 정류소명 또는 빈 문자열 |
| plateNo1 | 첫 번째 차량 번호 |
| remainSeatCnt1 | 첫 번째 차량 잔여좌석 |
| flag | 현재 응답에서 `PASS` 확인 |

예시 응답에서는 `predictTime1=25`, `predictTime2=""`, `locationNo1=20`, `locationNo2=""`가 확인됨. 앱 알림 판단에는 `predictTime1`을 기본 기준으로 사용하고, 필요 시 `predictTime2`는 다음 차량 표시용으로만 사용하면 됨.

## 9. 앱 구현 진행상황

완료:

1. MVP 대상값을 `stationId=228000883`, `routeId=234000027`, 정류소번호 `29116`로 고정
2. API 키를 소스에 넣지 않고 루트 `.gbis-key`에서 읽어 `BuildConfig.GBIS_SERVICE_KEY`로 주입
3. 선택 화면은 고정 텍스트와 "모니터링 시작/종료" 버튼으로 구현
4. `BusMonitorService`에서 `getBusArrivalItemv2`를 30초 주기로 폴링하고 `predictTime1` 기준으로 20/15/10/5/3/1분 임계값 알림 발송
5. 알림 구조는 Minseo6의 `ArrivalNotifier` 패턴을 참고해 `BusArrivalNotifier`와 `BusArrivalActionReceiver`로 분리
6. 서비스 시작/부팅 복구 구조는 Minseo7 패턴을 참고해 `BusMonitorService.start/stop`과 `BootReceiver`로 구현
7. 빌드 환경은 Minseo7의 Gradle 8.10.2 / AGP 8.7.2 조합을 참고해 조정

검증:

```bash
.\gradlew.bat assembleDebug
```

결과: `BUILD SUCCESSFUL`

남은 작업:

1. 실제 단말에서 알림 권한 허용 후 APK 실행 테스트
2. 실제 GBIS 응답에서 `predictTime1` 감소/임계값 교차 알림 동작 확인
3. 필요 시 1분 알림 후 자동 종료 기준을 실측에 맞춰 조정

## 10. 안정화 수정

리뷰에서 지적된 항목 중 사용자 의도와 충돌하지 않는 안정성 문제를 수정함.

적용:

1. 알림 임계값은 사용자 요청대로 `15/10/5/3/1` 유지
2. 여러 임계값 병합 알림은 사용자 요청대로 사용하지 않고, 현재 선택 차량 기준 단일 임계값만 알림
3. 서비스 활성 중 메인 화면 자체 조회가 서비스 상태를 덮어쓰지 않도록 변경
4. 사용자 액션(모니터링 시작, 차량 선택 변경) 직후 서비스가 즉시 한 번 더 조회하도록 변경
5. 수동 종료 시 미리보기 조회로 종료 문구가 덮이지 않도록 변경
6. 선택 차량 도착 또는 1분 알림 후 자동 종료 시 종료 상태를 화면으로 발행
7. `previousEtaMinutes`와 발송 완료 임계값을 SharedPreferences에 저장해 서비스 재시작 시 복구
8. 빈 GBIS 응답/차량 없음 응답은 파싱 실패가 아니라 `정보 없음`으로 처리
9. 도착 알림 ID를 임계값별로 분리해 새 알림이 기존 알림 업데이트처럼 묻히는 상황을 줄임
10. 주기 폴링은 30초 뒤부터 실행하고, 즉시 갱신은 사용자 액션/시작 요청에서 별도로 수행해 중복 조회를 줄임

검증:

```bash
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

결과: 빌드 성공, 단말 설치 성공
