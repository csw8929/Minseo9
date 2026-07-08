# Minseo9 워크스페이스 배포 앱 편입

## 배경

Minseo9(6900번 도착 알림)가 새로 추가된 프로젝트로, 워크스페이스의 "활성 배포 앱" 파이프라인(MinseoStore + `scripts/apk.sh`)에 정식 편입했다.

## 변경 사항

1. **`app/build.gradle.kts`** — 다른 배포 앱들과 동일하게 `applicationVariants.all` 블록을 추가해 디버그 APK 출력 파일명을 `Minseo9.apk`로 고정. (기존에는 기본값인 `app-debug.apk`로 출력되어 `apk.sh`의 flat 복사/NAS 업로드 규칙과 맞지 않았음.)
2. **`D:\workspace\CLAUDE.md`**
   - "Android apps" 서브프로젝트 맵 테이블에 Minseo9 행 추가.
   - "활성 배포 앱 목록 (MinseoStore 관리 대상)" 테이블에 Minseo9 행 추가, 앱 개수를 8개 → 9개로 갱신.
3. **`D:\workspace\scripts\apk.sh`**
   - `PROJECTS` 배열에 Minseo9 엔트리 추가 (root: `Minseo9`, apk: `app/build/outputs/apk/debug/Minseo9.apk`, package: `com.example.minseo9`, default 브랜치: `master`).
   - 상단 주석/사용법의 프로젝트 키워드 목록에 `minseo9` 추가.
4. **`DESIGN.md`** — Product Context의 "배포 없음, 직접 설치만" 문구를 "MinseoStore(개인용 앱 스토어)를 통해 배포"로 수정. (배포 대상으로 편입되며 사실과 달라진 부분을 반영.)

## 확인된 사항

- `VERSION` 파일(`1.0.0.0`) 및 `app/build.gradle.kts`의 `computedVersionCode` 패턴은 이미 워크스페이스 표준을 따르고 있어 별도 조치 불필요.
- MinseoNas 라이브러리는 사용하지 않는 앱이라 0단계(MinseoNas 버전 동기화) 대상에서 제외.
- `apk.sh` 하단의 "알 수 없는 프로젝트" 안내 메시지(`사용 가능: minseo | ...`)는 이미 이전부터 `minseo5`, `minseo7` 등이 누락되어 있던 별개의 기존 이슈로, 이번 작업 범위에서는 수정하지 않음.

## TODO (별도 확인 필요)

- Minseo9의 `CLAUDE.md`가 스킬 라우팅 안내만 담고 있고, 다른 배포 앱들처럼 "What this is / Build / Test" 섹션이 없음. 필요 시 보완 검토.
