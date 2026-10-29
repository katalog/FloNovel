한국어 | [English](README.en.md)

# 오프라인 엔진 실험

합성 TXT와 공식 KOReader Linux 런타임을 이용하는 검증 코드다. Dropbox·Supabase에
접속하거나 Kindle에 설치하지 않으며 개인 책을 열지 않는다. 실험 범위는 루트
AGENTS.md에 명시되어 있다.

## 준비와 실행

기기의 KOReader와 같은 v2026.07.1 공식 Linux x86-64 배포본을 .work/runtime/에
풀어 .work/runtime/lib/koreader/luajit이 존재하게 한다. 배포 주소와 SHA-256은
[영어 문서](README.en.md)에 기록했다. 현재 PC 검증 환경은 WSL Ubuntu 24.04다.

Linux/WSL에서 이 프로젝트 폴더를 기준으로 실행한다.

```sh
python3 experiments/run_engine_smoke.py
```

실행기는 런타임 버전을 확인하고 합성 UTF-8 입력을 만든 뒤 KOReader에 포함된 LuaJIT와
실제 crengine을 실행한다. 입력 정보와 항목별 JSON 결과는 .work/results/engine-smoke/에
저장한다. 실패가 있으면 종료 코드도 실패를 나타낸다.

## 확인 범위

폰트 등록, 두 TXT 파싱 모드의 문서 로딩, XPointer 유효성, 본문 접근을 확인한다.
7건 통과는 엔진 실행 가능성을 확인한 결과이며, 원문 UTF-16 위치 변환·화면 표시·플러그인
UI·Kindle 실기기 호환 검증 완료를 뜻하지 않는다. Linux와 Kindle의 버전은 같지만 실행
아키텍처는 다르다.

런타임과 결과 파일은 비공개 작업 자료이며 플러그인 배포물에 포함하지 않는다.

## 위치 변환과 실제 리더 실험

```sh
python3 experiments/run_position_probe.py
```

합성 TXT를 생성하고 실제 엔진의 DOM을 조사한 뒤, 원문 UTF-16 위치와 XPointer 및 엔진에서
읽은 문자를 비교한다. 이어서 임시 KO_HOME과 SDL dummy screen에서 실제 ReaderUI 및
검증용 flonovel.koplugin을 로딩해 메뉴·action·리더 anchor·폰트 변경·재열기를 확인한다.
Kindle에 설치하지 않으며 기존 KOReader 사용자 설정과 내장 플러그인은 사용하지 않는다.

결과는 .work/results/position-probe/에 저장한다. 원문이 바뀐 구분선·제어 제목과 비어 있는
파일은 현재 매핑이 거부한다. 이런 거부 검사의 통과를 전체 TXT 호환으로 해석하면 안 된다.
현재 실험은 EOF/숨겨진 공백 위치를 임의로 반올림하지 않는다. 자세한 판정과 한계는
[실험 결과](../.work/results/position-probe/assessment.ko.md)에 기록했다.

## Kotlin 기준값과 확장 검증

Windows에서 이 프로젝트를 기준으로 실행한다.

```powershell
./experiments/run_position_probe.ps1
```

기존 Desktop 빌드 classes와 Gradle 캐시의 Kotlin 컴파일러를 사용한다. 기존 앱을 먼저 빌드해야
하며 새 라이브러리는 설치하지 않는다. 합성 입력 준비, 실제 Kotlin 기준값 생성, hash/parity,
원문 경계 후보, 긴 문서/같은 화면 분할점 이동을 함께 검증한다. Linux용 Python 명령만 실행하면
Kotlin 기준값을 필요로 하는 확장 ReaderUI 검사는 수행하지 않는다.

진단 옵션은 알려진 변형 뒤의 보존된 본문과 정확한 node 끝만 대응한다. 변형된 구분선 내부,
표현할 수 없는 개행/EOF, surrogate 중간 위치는 추정하지 않는다. 기본 엄격 모드의 거부 동작은
그대로다. 각 ReaderUI 입력 사본과 sidecar도 새 KO_HOME 아래에서 격리한다.
[최신 확장 결과](../.work/results/position-probe/extended-assessment.ko.md)를 참고한다.

## 공개 golden fixture·장편·캐시 무효화

WSL/Linux에서 이 프로젝트를 기준으로 실행한다.

```sh
python3 experiments/run_scale_probe.py
```

기존 Android/Desktop parity의 기대 출력을 바이트 비교한 후 비공개 결과 폴더에 복사한다.
실행 후 원본이 변경되지 않았는지도 확인한다. 대상은 전처리된 UTF-8 출력이므로 파일명에
CP949/EUC-KR이 있어도 Lua의 해당 인코딩 디코딩을 검증했다는 뜻은 아니다. 빈 출력은 거부한다.
128KiB·1MiB·4MiB 합성 문서는 분산된 문자 위치의 정확성을 검사한다.

.work/results/scale-probe/scale-results.json에 프로세스 CPU 시간·Lua heap 증가·프로세스
RSS를 기록한다. 엔진 캐시가 이미 있을 수 있으며 RSS는 순간 관측치이지 최대 메모리나 책별
할당량이 아니다. PC 측정으로 Kindle 성능이나 실제 화면 이동 지연을 확정하지 않는다.

cache-results.json은 단일 항목 메모리 캐시 실험이다. 본문 해시·파싱/DOM/매핑 버전·문서
객체·세대·책·서재·계정 범위를 확인하며, 크기와 수정시각이 같아도 내용이 바뀌면 재사용하지
않는다. 닫기 무효화는 검사 코드에서 명시적으로 호출한다. 실제 ReaderUI 생명주기 연결과
영속 캐시 복구는 미구현이다. 여기의 SHA-256은 Dropbox block content hash와 다르다.
챕터 목록이나 두 번째 읽기 anchor를 저장하지 않는다.

## 플러그인 오류·비활성화·재열기

```sh
python3 experiments/run_lifecycle_probe.py
```

새 KO_HOME의 합성 TXT와 진단 플러그인을 실제 discovery/loader/ReaderUI로 실행한다.
문법·모듈 로딩·초기화·이벤트 오류 뒤에도 정상 플러그인·페이지 이동·화면 그리기가 유지되는지
검사한다. 실제 플러그인 관리자 toggle의 저장과 loader 재조회·재활성화·읽기 위치 유지도 확인한다.
기존 runtime의 기본 플러그인은 격리 설정에서 비활성화하며 원래 설정/책은 사용하지 않는다.

결과는 .work/results/a2/lifecycle-results.json, 예상 오류 로그는 process.log에 남는다.
24건은 Lua/ReaderUI 23건과 원문 hash 불변 1건이다. 같은 프로세스의 loader 초기화이며 실제
프로세스/Kindle 재시작 검사가 아니다. 무한 루프·native crash·모든 callback 격리를 보장하지 않는다.

## 일괄 매핑 후보와 요청한 ## 마커

위 명령으로 위치/Kotlin oracle과 scale 입력을 준비한 뒤, 프로젝트 기준 WSL/Linux에서 실행한다.

```sh
python3 experiments/run_bulk_probe.py
```

새 후보는 엔진의 body 직렬화를 한 번 읽고 추출한 node를 기존 mapper의 동일한 원문 검증에
전달한다. 관찰한 body/pre/empty-line 형식과 XML 기본 entity만 지원하며 다른 구조는 거부한다.
기본 mapper를 바꾼 것이 아니다. node/실제 문자 비교 후 새 세션에서 기존 ReaderUI 검사를
실행한다. UI 결과는 bulk- prefix로 저장해 reference 결과와 분리한다. CPU/heap 관측은
Kindle의 실제 대기 시간이나 최대 메모리를 보장하지 않는다.

사용자가 요청한 마커는 전처리된 원문의 trusted ## prefix다. WSL/Linux에서 입력을 준비하고
Windows에서 실제 Kotlin 기대값을 만든다.

```sh
python3 experiments/prepare_marker_cases.py
```

```powershell
./experiments/run_kotlin_oracle.ps1 -SourceName MarkerOracle.kt -EntryPoint MarkerOracleKt
```

WSL/Linux의 runtime 디렉터리에서 비교한다.

```sh
cd .work/runtime/lib/koreader
./luajit ../../../../experiments/check_marker_cases.lua
```

기존 Desktop detector/navigator로 제목·UTF-16 시작 위치·분할점을 비교하며 긴 제목과 Unicode
trim을 포함한다. ##에 커스텀 regex의 길이 제한을 적용하지 않는다. 결과는
.work/results/focused/에 기록한다. 별도 ChapterOracle/inspect_chapter_regex는 ECMAScript/Kotlin
차이 관측용이며 호환성 통과 검사나 제품 의존성이 아니다. 현재 마커에 범용 정규식 이식은 필요 없다.

## 파일 해시·PKCE·경로 키 비교

WSL/Linux에서 합성 입력을 준비하고 Windows에서 실제 Desktop 기대값을 만든다.

```sh
python3 experiments/prepare_contract_cases.py
```

```powershell
./experiments/run_kotlin_oracle.ps1 -SourceName ContractOracle.kt -EntryPoint ContractOracleKt
```

WSL/Linux의 runtime 디렉터리에서 실행한다.

```sh
./luajit ../../../../experiments/check_contract_primitives.lua
```

해시와 PKCE는 Lua/Kotlin/Python 결과를 비교한다. 경로 행은 관측 결과이며 기본 casefold나
simple lowercase가 JVM full lowercase와 같다고 가정하지 않는다. 실제 자격증명·원격 요청은
사용하지 않는다. 결과는 .work/results/finish/에 기록한다. 비공개 조사 결과 폴더의 loopback TLS와
logical EOF 코드는 가능성 검증 후보이며 배포할 제품 클라이언트가 아니다.

## 준비된 희소 위치 색인 실험

```sh
python3 experiments/run_sparse_probe.py --cpu-percent 5
```

고정된 Linux 런타임·합성 입력·인터프리터·systemd 사용자 CPU 제한으로 별도 실험한다.
실제 서비스에 접속하거나 제품 플러그인을 바꾸지 않는다. 결과와 입력/코드 SHA-256은
`.work/results/sparse-probe/`에 저장한다.

최초 한 번 기존 매퍼로 원문과 DOM 전체의 대응을 검증하고 약 4KiB 간격으로 원문 바이트·
UTF-16·요소 수의 기준점을 저장한다. 다시 읽을 때 색인 해시·원문 전체 해시·렌더링 귀속을
검사한다. 준비된 변환은 원문 최대6,148바이트를 읽으며 역변환은 그 구간을 두 번 읽을 수 있다.
일반적인 XPointer는 매번 DOM 앞부분을 재탐색하지 않고 검증한 대응 관계를 재사용한다.

CPU5% 실험의8MiB 색인은59,223바이트였다. 분산된84개 송신 방향 변환은 최대94.99ms,
역변환은 최대91.45ms, 실제 페이지에서 얻은12개 anchor는 최대97.07ms였다. 지원 입력의
검사746건이 통과했고 UTF-16/UTF-8 바이트 오차는0이었다. OS 캐시가 따뜻할 수 있는 PC
측정 표본이며 실제 Kindle의 속도나 모든 입력·스케줄링에서의 상한을 보장하지 않는다.

최초 준비는 여전히43.32초, 색인 재읽기와 전체 원문 확인은0.40초가 걸렸다. 최초 준비에는
기존 전체 매퍼가 포함되므로 향후 통합에서 읽기를 막지 않도록 분리해야 한다. 다운로드 중
원문 색인만 만드는 것으로 DOM 전체 검증을 대체할 수 있다고 확인한 것은 아니다.
1,024바이트 초과 줄·변형/분할된 요소는 명시적으로 보류하며 렌더링 변경도 색인을 무효화한다.
송신 전 기존 본문 해시 가드는 여전히 필요하고 매 조회마다 원문 전체를 다시 해시하지 않는다.
근삿값을 송신하거나 두 번째 읽기 anchor·챕터 목록을 저장하지 않는다. 제품에는 미적용이다.
