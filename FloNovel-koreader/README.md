[한국어](README.md) | [English](README.en.md)

# FloNovel for KOReader

탈옥한 Kindle의 KOReader용 단일 플러그인입니다. 현재 개발 버전이며 PC의 KOReader
v2026.07.1에서 검증합니다. Kindle 실행과 실제 Dropbox/Supabase 연동은 별도 확인이 필요합니다.

설치 단위는 `flonovel.koplugin` 하나이며 챕터 이동, Dropbox 파일 동기화, 위치 공유를
각각 켜고 끕니다. 기본값은 챕터 이동만 켜짐입니다.
책 읽기와 파일 목록 화면 모두 도구 탭 첫 항목의 **FloNovel**에서 바로 설정합니다.

## 설치와 사용

1. KOReader를 종료하고 USB로 연결합니다. 개발 패키지의 `flonovel.koplugin`을
   Kindle의 `koreader/plugins/` 아래에 복사합니다.
2. USB 연결을 해제하고 평소 책 목록의 KOReader 항목으로 실행합니다.
3. UTF-8 TXT를 열고 메뉴의 **FloNovel**에서 챕터 목록·이전/다음 챕터·분할점 이동을 사용합니다.
   TXT 레이아웃은 원문 그대로(preformatted)여야 합니다. 플러그인은 전역 레이아웃 설정을 바꾸지 않습니다.
4. 챕터당 분할 수는 1/2/4/8/16/32 중 선택합니다. KOReader 제스처 설정에서
   `FloNovel:`로 시작하는 동작에 원하는 제스처를 지정할 수 있습니다.

챕터는 열 때 원문의 `##` 접두사를 탐지합니다. 사용자가 확정한 `^##` 형식이며 긴 제목에도
60자 제한을 걸지 않습니다. 기존 앱과 같은 Unicode trim, UTF-16 분할 공식과 짧은 챕터의
LF 20줄 예외를 사용합니다. 숨은 공백이나 서로게이트 중간 등 정확히 대응하지 못하는 분할점은
사유를 표시하고 이동을 보류합니다. 일반 읽기는 계속할 수 있습니다.
페이지 시작이 빈 줄이면 전체 DOM 순서를 검증한 뒤 그 줄의 정확한 원문 시작 위치로 변환합니다.
LF·CRLF·UTF-8 BOM·앞뒤 빈 줄은 ReaderUI 회귀 검사에 포함하며, 검증된 마지막 빈 요소는
원문의 논리적 EOF로 대응합니다.

## Dropbox 연결

Python 3.9 이상이 있는 PC에서 `tools/connect.py --help`를 확인합니다. 기존 FloNovel의
**같은 Dropbox 앱 키**를 사용하며 새 앱을 만들거나 기존 앱의 토큰을 복사하지 않습니다.

```text
python tools/connect.py --app-key PUBLIC_APP_KEY --library-root /mnt/us/documents/FloNovel --supabase-url https://PROJECT.supabase.co --publishable-key PUBLIC_API_KEY --output PRIVATE_DIRECTORY/config.json
```

기존 Desktop의 공개 연결값을 사용하려면 세 연결 옵션 대신
`--desktop-properties ../FloNovel-desktop/local.properties`를 사용할 수 있습니다.
이 옵션은 앱 키·Supabase URL·공개 API 키만 읽으며 토큰이나 앱 시크릿은 가져오지 않습니다.

브라우저에서 승인하고 일회용 코드를 PC 프롬프트에 입력합니다. 출력 파일은 저장소 밖에
보관합니다. 이 파일에는 비밀번호처럼 보호해야 하는 refresh token이 있습니다. 채팅에 붙이거나
커밋하지 마세요. 앱 시크릿은 필요하지 않습니다. 개발용 시크릿 파티션에는 `--dev`를 사용합니다.

선택한 Kindle 서재 폴더를 만든 후 파일을 `koreader/settings/flonovel/config.json`으로
복사합니다. `KO_HOME`을 따로 쓴다면 그 설정 폴더를 사용합니다. FloNovel 메뉴에서
**연결 확인 및 공유 시크릿 읽기**를 실행합니다. 이 작업은 기존 `/.flonovel/secret.json`을
읽으며, Supabase 설정이 있으면 `__connection_test__` 행으로 `source=koreader`와 max-wins를
검사합니다. 실패하면 위치 자동 전송을 허용하지 않습니다. 시크릿을 새로 만들지 않습니다.

## 파일과 위치 동기화

파일 동기화는 메뉴의 **지금 Dropbox 파일 동기화**로 실행합니다. 첫 버전은 전체 목록을
조회하며 실시간 파일 감시나 영속 증분 커서를 사용하지 않습니다. `/books`의 전처리된 TXT를
내려받고, 이전에 추적한 개별 TXT가 로컬에서 삭제되면 `parent_rev`를 걸어 Dropbox에 반영합니다.
같은 내용의 기존 로컬 파일은 해시 확인 후 추적합니다. 로컬 폴더 전체가 사라진 경우에는
그 안의 책을 개별 삭제로 추정하지 않습니다.

로컬 수정·양쪽 수정과 자동 이동은 보존·보류합니다. 해시로 식별한 이동은 삭제로 세지 않습니다.
새 TXT나 수정한 원문의 업로드, 폴더 삭제 저널은 지원하지 않습니다. 완전한 파일 양방향 동기화가
아닙니다. 원격 수정은 로컬이 기준 내용과 같을 때만 받으며, 교체 전 원문과 원격에서 삭제된
로컬 원문은 서재의 `.flonovel/recovery/`에 보관합니다. 이 보관 파일은 자동으로 지우지 않습니다.
열린 책의 다운로드·삭제·충돌 처리는 닫을 때까지 보류합니다. 대량 삭제와 원격 빈 목록에서는
확인을 요청하고 승인 후 같은 계획인지 재검사합니다. 실패/보류 사유는 **동기화 상태**에서 확인합니다.

위치 공유는 파일 동기화와 독립입니다. 책 열기/복귀 때 원격 위치를 확인하고 원격이 500자보다
더 뒤면 이동할지 묻습니다. 페이지 이동 후 10초 쉬면 현재 ReaderRolling anchor에서 위치 요청을
만들고 온라인에서 전송합니다. 닫기/절전 때에도 요청을 저장합니다. 네트워크 작업은 취소할 수 있는
별도 프로세스에서 실행하며 오프라인 작업은 다음 전송에서 재시도합니다. 본문 해시나 계정·앱·서재·
시크릿 파티션이 달라진 요청은 보내지 않습니다. Supabase에 Authorization이나 user_key를 보내지
않으며 push 전 GET 충돌 해결 없이 서버 max-wins를 따릅니다.

책을 열 때는 원격 행을 먼저 조회하므로 원격 위치가 없거나 오프라인인 조회에서는 책 전체
매핑을 시작하지 않습니다. 챕터 분석은 Unicode 한 번 순회로 처리하며 본문 해시는 가능한 경우
번들 native crypto를 사용하고 없으면 Lua로 계산합니다. 본문 해시 검증은 유지합니다.
PC에서 측정한 속도 개선은 실제 Kindle에서 별도로 확인해야 합니다.
준비 단계별 시간과 해시 계산 방식은 책 경로나 자격증명 없이 로그에 기록합니다.

계정이나 서재를 바꾸면 상태 재사용을 거부합니다. 새 연결을 확인한 다음 메뉴의 상태 초기화를
명시적으로 사용하세요. 초기화는 책을 지우지 않으며 기존 미전송 위치 요청은 비웁니다.

## 현재 제한과 제거

- 다운로드는 디스크에 스트리밍하며 16 MiB 제한을 적용하지 않습니다. 저장 공간/파일시스템 오류와 해시 검증은 유지합니다.
- 챕터 이동·위치 공유에 쓰는 위치 매핑 상한은 현재 파일당 16 MiB입니다. 그보다 큰 책도 다운로드하고 KOReader 자체로 읽을 수 있으며, 매핑 메모리 지원 확대는 별도 개선 대상입니다.
- UTF-8 TXT와 검증된 preformatted DOM만 지원합니다. 다른 인코딩·엔진 변환은 추측하지 않습니다.
- 경로는 JVM 21의 비문맥 lowercase 표와 NFC를 사용합니다. 문맥에 따라 바뀌는 대문자 Greek Sigma와
  해당 JDK에서 정의되지 않은 Unicode 경로는 보류합니다. NFKC casefold를 쓰지 않습니다.
- 세 클라이언트의 실제 서버 왕복, Kindle의 메모리·CA·절전 동작은 아직 통합 검증 대상입니다.

KOReader 플러그인 관리에서 FloNovel을 끈 후 재시작하면 비활성화됩니다. 제거는 KOReader 종료 후
`koreader/plugins/flonovel.koplugin/`만 지웁니다. 서재 책과 복구 보관물은 유지하세요.
연결까지 지울 경우 `koreader/settings/flonovel/`의 자격증명을 별도로 제거하고 Dropbox에서
해당 연결을 해제합니다. USB 연결 중에는 KOReader를 실행하지 않습니다.

## 개발 검증

Windows의 WSL2·WSLg에서 화면을 직접 조작하려면 다음을 실행합니다.

```powershell
.\FloNovel-koreader\tools\start_emulator.ps1
```

공식 Linux v2026.07.1 런타임이 `.work/runtime/lib/koreader`에 있어야 합니다.
실제 SDL GUI를 열고 `.work/emulator/desktop`에 별도 설정·캐시와 합성 책
128 KiB/2 MiB/8 MiB를 만듭니다. 매 실행에서 제품13파일을 갱신하며 해당 프로필의
설정과 읽기 상태는 보존합니다. 창을 닫은 후 다시 실행하세요. `-Profile 다른이름`으로
프로필을 나눌 수 있으며 이름은 영문 소문자·숫자·하이픈만 사용합니다. 기본값은 Lua 부하를
더 드러내는 인터프리터 실행이고 `-Jit`으로 일반 JIT 실행을 사용할 수 있습니다.
기본 CPU 제한은 한 코어의 5%이며 systemd 사용자 scope가 100ms 주기로 적용합니다.
하위 작업도 같은 제한을 받고 다른 WSL 프로그램에는 영향을 주지 않습니다.
`-CpuPercent 2`로 더 느리게, `-CpuPercent 0`으로 제한 없이 실행할 수 있습니다.
CPU 제한에는 systemd 사용자 관리자가 필요하며 적용 실패를 조용히 무시하지 않습니다.

`-NoIndex`로 Linux 합성 환경 전용의 무색인 위치 공유 실험을 사용할 수 있습니다.
프로필마다 선택을 기억하며 `-FullMap`으로 기존 방식으로 돌아갑니다. 에뮬레이터 복사본만
패치하고 문단을 구분할 수 있는 `no-index-8MiB.txt`를 만듭니다. 일반 위치 갱신에서는
전체 매핑/챕터 준비를 하지 않고 검색·큐 저장·전송은 취소 가능한 별도 프로세스로 실행합니다.
수신 목표는 native 원문과 대조한 뒤 이동을 제안합니다. 챕터 기능은 요청할 때 기존 매퍼를
준비합니다. 반복 문단으로 모호하거나 텍스트가 아닌 anchor는 명시적으로 보류합니다.
이 실험 모드는 Kindle 배포판이 아닙니다.

GUI 프로필은 챕터와 위치 공유를 켜며 FloNovel 위치 요청은 실제 계정·외부 통신 없이
로컬 모의 전송과 JSON max-wins 상태로 처리합니다. Dropbox 동작은 별도 오프라인 제품
검사에서 확인합니다. 이 모의 서버는 실서버나 TLS를 검증하지 않습니다. CPU 구조·메모리
한계·절전·전자잉크 동작은 Kindle 확인이 필요하며 PC 시간을 Kindle 시간으로 표시하지 않습니다.

Linux/WSL에서 압축을 푼 공식 KOReader v2026.07.1과 Python 3, OpenSSL이 필요합니다.

```text
python3 tests/run.py --runtime /path/to/koreader --results /private/path/results
python3 tools/package.py --results /private/path/results/SESSION/summary.json --output dist/flonovel-koreader-dev.zip
```

테스트는 합성 원문·실제 ReaderUI/crengine·기존 Kotlin 기준값·로컬 HTTPS 서버를 사용하며
Kindle나 실제 계정에는 쓰지 않습니다. 패키지는 통과한 소스 해시와 13개 제품 파일 allowlist를
검사합니다. 조사 자료·개인 책·토큰·runtime·work.md는 포함하지 않습니다.
