[한국어](README.md) | [English](README.en.md)

# FloNovel for KOReader

탈옥한 Kindle의 KOReader용 단일 플러그인입니다. 현재 개발 버전이며 PC의 KOReader
v2026.07.1에서 검증합니다. Kindle 실행과 실제 Dropbox/Supabase 연동은 별도 확인이 필요합니다.

설치 단위는 `flonovel.koplugin` 하나이며 챕터 이동, Dropbox 파일 동기화, 위치 공유를
각각 켜고 끕니다. 기본값은 챕터 이동만 켜짐입니다.

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

계정이나 서재를 바꾸면 상태 재사용을 거부합니다. 새 연결을 확인한 다음 메뉴의 상태 초기화를
명시적으로 사용하세요. 초기화는 책을 지우지 않으며 기존 미전송 위치 요청은 비웁니다.

## 현재 제한과 제거

- 위치 매핑/다운로드 상한은 파일당 16 MiB입니다. 그보다 큰 책도 KOReader 자체로 읽을 수 있습니다.
- UTF-8 TXT와 검증된 preformatted DOM만 지원합니다. 다른 인코딩·엔진 변환은 추측하지 않습니다.
- 경로는 JVM 21의 비문맥 lowercase 표와 NFC를 사용합니다. 문맥에 따라 바뀌는 대문자 Greek Sigma와
  해당 JDK에서 정의되지 않은 Unicode 경로는 보류합니다. NFKC casefold를 쓰지 않습니다.
- 세 클라이언트의 실제 서버 왕복, Kindle의 메모리·CA·절전 동작은 아직 통합 검증 대상입니다.

KOReader 플러그인 관리에서 FloNovel을 끈 후 재시작하면 비활성화됩니다. 제거는 KOReader 종료 후
`koreader/plugins/flonovel.koplugin/`만 지웁니다. 서재 책과 복구 보관물은 유지하세요.
연결까지 지울 경우 `koreader/settings/flonovel/`의 자격증명을 별도로 제거하고 Dropbox에서
해당 연결을 해제합니다. USB 연결 중에는 KOReader를 실행하지 않습니다.

## 개발 검증

Linux/WSL에서 압축을 푼 공식 KOReader v2026.07.1과 Python 3, OpenSSL이 필요합니다.

```text
python3 tests/run.py --runtime /path/to/koreader --results /private/path/results
python3 tools/package.py --results /private/path/results/SESSION/summary.json --output dist/flonovel-koreader-dev.zip
```

테스트는 합성 원문·실제 ReaderUI/crengine·기존 Kotlin 기준값·로컬 HTTPS 서버를 사용하며
Kindle나 실제 계정에는 쓰지 않습니다. 패키지는 통과한 소스 해시와 13개 제품 파일 allowlist를
검사합니다. 조사 자료·개인 책·토큰·runtime·work.md는 포함하지 않습니다.
