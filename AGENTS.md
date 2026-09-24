# AGENTS.md

이 저장소의 작업 규칙이다. 소설 리더 앱 두 개(Android · Desktop)가 있고 **코드를
공유하지 않는다.** 대신 아래 계약을 양쪽이 지켜서 호환된다. 이 문서의 대부분이 그
계약이다.

**모든 에이전트(Claude Code, Codex, Antigravity 등)가 따르는 단일 기준 문서다.**
`CLAUDE.md` 는 이 파일을 불러오기만 한다. 규칙을 고치거나 더할 때는 여기에만 쓴다 —
두 곳에 따로 쓰면 에이전트마다 다른 규칙을 따르게 된다. 코드 주석이 `AGENTS.md §1`
처럼 절 번호로 이 문서를 가리키므로, 절 번호와 제목을 바꾸면 그 참조도 함께 고친다.

> 이 파일과 `CLAUDE.md` 만 한국어다. **커밋되어 GitHub에 올라가는 나머지 전부 —
> 코드, 주석, README, 워크플로, 커밋 메시지 — 는 영어로만 쓴다.**

## 구조

```text
FloNovel-android/   Android 앱 (Kotlin · Compose · Room)
FloNovel-desktop/   Desktop 앱 (Kotlin/JVM · Compose Desktop)
.work/              비공개 작업 파일 — gitignore 됨
```

**루트에 Gradle 프로젝트가 없다.** 두 앱이 각자 독립 빌드라, 고치는 앱 폴더 안에서
Gradle을 실행해야 한다.

```bash
cd FloNovel-android && ./gradlew testDebugUnitTest   # 빠른 게이트
cd FloNovel-android && ./gradlew assembleDebug
cd FloNovel-desktop && ./gradlew test
cd FloNovel-desktop && ./gradlew run                 # 실행 확인
```

Android 계측 테스트(`app/src/androidTest`)는 기기가 필요하고 일부는 네트워크로 폰트를
받아오므로 기본 게이트가 아니다.

**`BUILD SUCCESSFUL` 은 통과가 아니다.** 테스트가 0건 실행되고도 그렇게 나온다.
`build/test-results/` 의 XML을 파싱해 실제 건수를 세라.

---

## 1. 계약 — 하나라도 어기면 반대편 앱이 조용히 깨진다

### 읽기 위치

- 위치는 **디코딩된 원문의 문자 오프셋**이다. 페이지 번호도, 바이트 오프셋도 아니다.
  글자 크기 한 단계만 바뀌어도 "37페이지"가 가리키는 내용이 달라지기 때문이다.
  - Desktop: `BookRecord.anchor`
  - Android: `BookEntity.lastReadCharOffset`
- **anchor 는 하나뿐이다.** "표시용 위치"와 "동기화용 위치"를 따로 두지 않는다.
- 페이지는 저장하는 값이 아니라 레이아웃마다 다시 계산되는 파생값이다.

### 이동 규칙 (Desktop 전용)

Desktop 은 1-pane / 2-pane 을 **같은 규칙**으로 움직인다 — "보이는 양의 절반만큼
전진". 구현은 `reader/ReaderNavigator.kt` 의 `advance(ratio)` **한 벌**이다.
1-pane 이면 절반이 화면의 절반, 2-pane 이면 pane 하나라서 우측이 좌측으로 온다.

Android 는 1-pane 뿐이라 이 규칙의 적용 대상이 아니고, `ReaderViewModel` 안에 자체
페이지 이동이 따로 있다. **두 앱의 이동 코드를 합치려 들지 마라.**

### 전처리와 등록

- 순서는 **전처리 → 등록**, 예외 없다. 전처리가 파일명과 문자 위치를 둘 다 바꾸므로
  거꾸로 하면 읽던 위치를 잃는다.
- `preprocessedAt` 이 있는 책은 **다시 전처리하지 않는다.**
- 전처리는 멱등이다. 이미 처리된 텍스트에 다시 돌려도 바이트 단위로 같은 결과가
  나와야 하고, `FloNovel-desktop/src/test/resources/fixtures/` 의 픽스처로 그걸 검증하는
  테스트가 있다 (`TextPreprocessorTest.p1_idempotencyOnFixtureNovelFiles`).
- 원본을 비원자적으로 덮어쓰지 않는다. 최초 처리 때 백업을 남긴다.
- 업로드까지 포함한 순서는 **전처리 → 등록 → 업로드**다. Dropbox 에는
  전처리된 파일만 존재한다. 그래서 전처리기가 두 앱 모두에 생기는데, 코드를 공유하지
  않는 대신 **같은 픽스처에 바이트 단위로 같은 출력**을 내는지 양쪽 테스트로 맞춘다.
  한쪽만 고치면 두 기기가 같은 책을 서로 다르게 고쳐 올리며 충돌 사본을 계속 만든다.
  - 픽스처와 기대 출력은 양쪽 `src/test/resources/fixtures/parity/` 에 같은 사본으로 있고, 각
    앱 테스트가 상대 사본과 바이트 비교까지 한다. 전처리를 바꾸면 Desktop 에서
    `./gradlew test -PupdateGolden` 으로 기대 출력을 다시 만들고 Android 쪽에 복사한다.
  - Android 이식본은 `\d` `\s` `.` `\p{IsHangul}` 와 IGNORE_CASE 를 쓰지 않는다. Android 의
    정규식 엔진(ICU)에서는 뜻이 넓어서(`\d` 가 전각 숫자까지) 같은 입력이 다르게 나온다.
    문자 범위를 직접 쓰고 스크립트는 `Character.UnicodeScript` 로 판정한다.
  - 인코딩 판별도 양쪽이 같은 결정을 한다(EUC-KR 판정은 MS949 로, 판정 불가는 UTF-8).
  - SAF 는 원자적 교체가 없어서, Android 는 원본을 `.flonovel/original/` 에 백업한 뒤 숨김 임시
    파일에 쓰고, 원본을 지우고, 임시 파일 이름을 바꾼다. 중간에 끊기면 다음 동기화가 마무리한다.
    파일 URI 가 바뀌므로 책을 **처음 열기 전**(또는 처음 올리기 전)에만 한다. Dropbox 에서 받아
    동기화가 끝난 책(base 가 SYNCED)은 이미 전처리된 파일이라 여는 시점 확인을 건너뛴다 — 확인
    자체가 파일 전체를 읽고 정규화해서, 큰 소설은 폰에서 여는 데 몇 초가 더 걸렸다.

### 챕터

- 챕터 목록은 **저장하지 않는다.** 책을 열 때마다 다시 탐지한다.
- 전처리기가 붙인 `##` 표식은 **길이 제한 없이 신뢰**한다. 줄 길이 가드는 사용자가
  직접 넣은 정규식에만 적용된다. 두 그룹을 한 리스트로 합친 것이 예전에 `##` 프리셋에
  60자 제한을 물려서 라이브러리 챕터 줄의 **19.6%** 를 조용히 버리게 만들었다.
- 전처리기 자체의 제목 판정에는 길이 제한을 넣지 않는다.

### 경로 정규화

순서는 **구분자 → NFC → 소문자**다. 양쪽 앱이 같은 책에 같은 키를 만들어야 한다.
순서를 바꾸면 모든 키가 바뀐다.

```kotlin
relativePath.replace('\\', '/')            // 1
    .let { Normalizer.normalize(it, NFC) } // 2
    .lowercase()                           // 3
```

### 동기화 — 읽기 위치 (Supabase)

- 테이블 `flonovel_sync`. 요청 헤더 `x-flonovel-secret`.
- **Supabase 요청에 `Authorization` 헤더를 넣지 마라.** JWT로 파싱하려다 실패하는데
  에러 메시지가 원인을 안 알려준다. (Dropbox 쪽 `Authorization: Bearer` 는 정상이다.)
- **`user_key` 를 보내지 마라.** 서버 트리거가 `hex(sha256(x-flonovel-secret))` 로
  계산하고, 그 해시가 곧 그 사용자의 데이터 파티션이다.
- **push 전에 원격을 조회해서 충돌을 해결하지 마라.** 트리거가 update 시
  `char_offset` 을 `greatest(new, old)` 로 클램프해 max-wins 를 강제한다.
  클라이언트가 충돌 해결을 하면 그것과 싸운다. (아래 파일 동기화의 충돌 규칙은
  파일 내용에 대한 것이고, 이 규칙과 무관하다.)

### 동기화 — 파일 (Dropbox)

> **양방향이다** (2026-09-23 결정, 2026-09-24 `main` 머지. 계획은 `.work/two-way-sync-plan.md`).
> 단방향(Desktop → Dropbox → Android) 시절 규칙은 폐지됐다.

공통:

- Dropbox 경로는 앱 폴더 루트 기준 **상대경로**만 쓴다 (`/books`,
  `/.flonovel/secret.json`). `/Apps/<이름>/...` 같은 절대경로는 쓰지 않는다 —
  앱 폴더 이름은 사용자마다 다르다.

양방향 규칙:

- **Dropbox `/books` 가 기준이다.** 두 앱 모두 추가·수정·삭제를 올리고, 상대가 바꾼 것을 받는다.
- **판정은 3자 비교다.** 기기마다 파일별 base(마지막으로 맞춘 상태: `rev`,
  `content_hash`, 로컬 크기, 로컬 수정시각)를 저장하고, **로컬↔base** 와 **원격↔base** 를
  따로 판정한다. 로컬과 원격을 직접 비교해 "한쪽에만 있으니 새 파일/지운 파일"이라고
  추론하지 마라. 단방향 시절 PC 가 "로컬에 없으면 원격 삭제", 폰이 "원격에 없으면 로컬
  삭제"를 한 게 이 추론이고, 양방향에서 그대로 두면 상대 기기가 추가한 파일을 지운다.
  - base 저장: Desktop 은 설정 폴더의 `sync-state.json`, Android 는 Room `sync_base`.
- **내용 비교는 Dropbox `content_hash` 다.** 수정시각은 "해시를 다시 계산할지"를 정할
  때만 쓴다. **수정시각으로 어느 쪽이 최신인지 정하지 마라** — 기기 시계가 다르고, 예전에
  시각 비교가 무한 재다운로드를 만든 적이 있다.
- **업로드는 `mode=update(rev)`, 새 파일은 `mode=add`, 삭제는 `parent_rev` 를 건다.**
  `overwrite` 는 쓰지 않는다 — 그 사이 다른 기기가 올린 변경을 조용히 덮는다. Dropbox 가
  거부하면 그 파일은 다음 판정에서 충돌로 처리된다.
- **충돌은 둘 다 남긴다.** 원격이 원래 이름을 갖고, 로컬 쪽은
  `<이름> (충돌 사본 - <기기> - <yyyy-MM-dd>).<확장자>` 로 바꿔 올린다. `충돌 사본` 은 UI
  언어를 따른다(영어는 `conflicted copy`). 기기는 `PC` 또는 `Android`. 이미 있으면 `_1`,
  `_2` 를 붙인다. 수정 vs 삭제는 **수정이 이긴다.**
- **커서는 적용과 base 기록이 모두 끝난 뒤에만 저장한다.** 하나라도 실패하면 이전 커서를
  유지한다. 먼저 저장하면 처리하지 못한 변경을 다시는 받지 못한다.
- **원격 삭제를 PC 에 반영할 때는 항상 휴지통으로 보낸다.** Delete 키 설정(지정 폴더로
  옮기기)과 무관하다.
- **대량 삭제는 자동으로 하지 않는다.** 한 번의 동기화가 20개 이상, 또는 5개 이상이면서
  추적 중인 파일의 30% 이상을 지우게 되거나, 원격 `/books` 가 비어 있으면 멈추고 사용자에게
  묻는다. 양방향 모두 적용한다. (30% 규칙에 5개 하한이 없으면 책 세 권 중 한 권만 지워도
  매번 물어본다.) 계정을 잘못 연결하거나 앱 폴더를 초기화하면 양쪽 라이브러리가 함께 지워진다.
- **다운로드 중인 파일은 업로드하지 않는다.** Android SAF 는 원자적 교체가 안 돼서 끊긴
  다운로드가 잘린 파일로 남는다. 다운로드 전에 base 에 `DOWNLOADING` 을 기록하고, 그 상태의
  파일은 다시 받는다. 이걸 빼먹으면 잘린 파일이 "로컬 수정"으로 보여 원격을 덮는다.
- **배포는 Desktop 이 먼저다.** 옛 Desktop 은 원격에만 있는 파일을 지우므로, 양방향
  Android 가 먼저 나가면 폰이 올린 책이 사라진다.
- **동기화 시점**: Desktop 은 Dropbox longpoll 로 원격 변경을 알아채고, 창에 돌아올 때(1분에
  한 번까지)도 동기화한다. Android 는 백그라운드 동기화 없이 라이브러리 화면이 앞으로 올 때
  (앱 실행, 다른 앱에서 복귀, 리더에서 복귀) 1분에 한 번까지 동기화한다.
- **이동과 이름 변경**: 한 회차에서 "base 가 있던 파일이 사라짐"과 "base 없는 새 파일"이 내용
  해시로 1:1 짝지어지면 이동으로 처리한다. 로컬에서 옮겼으면 Dropbox `move_v2`, 원격에서 옮겼으면
  로컬 파일을 옮긴다(재전송 없음). 같은 내용이 여럿이라 짝이 애매하면 삭제+추가로 둔다. 이동한
  책은 로컬 읽기 기록을 따라 옮기고, Supabase 위치를 새 경로 키로 복사한다(max-wins 라 되돌아가지
  않음). 이동은 대량 삭제로 세지 않는다. Android 에서 전처리가 바꿀 이름으로 옮긴 경우는 이동이
  아니라 전처리를 거친다.
- **리더로 열어 둔 책은 건드리지 않는다.** 그 책의 다운로드·삭제·충돌 처리는 닫을 때까지 미루고,
  커서도 저장하지 않아 다음 동기화가 다시 처리한다.

---

## 2. 만들지 않을 것

```text
❌ 실제 구현이 하나뿐인 인터페이스 / 추상 클래스
❌ Gradle 모듈 분리
❌ DI 프레임워크(Hilt, Koin 등), ORM, 리액티브 프레임워크
❌ "나중에 쓸" 빈 클래스나 확장 지점
❌ 사용하지 않는 의존성
```

새 인터페이스를 만들기 전에 답하라 — **지금 실제 구현이 둘 이상인가?**

현재 있는 인터페이스와 그 정당성:

| | 구현체 | 판단 |
|---|---|---|
| `TextFitter` (desktop) | `ComposeTextFitter` · `FakeTextFitter` | 정당. **reader 패키지에 주입되는 유일한 인터페이스** |
| `FolderBrowser` (android) | `SafFolderBrowser` · `FakeFolderBrowser` | 정당 |
| `LibraryFiles` (android) | `SafLibraryFiles` · `FakeLibraryFiles` | 정당. 양방향 동기화를 SAF 없이 JVM 테스트 |
| `SettingsController` (android) | ViewModel 들 | 정당. 설정 시트를 특정 VM에서 분리 |
| `BookDao` (android) | Room 생성 | Room 이 인터페이스를 요구함 |
| `SyncBaseDao` (android) | Room 생성 | Room 이 인터페이스를 요구함 |

## 3. 안정성

```text
❌ 동기화나 전처리 실패로 읽기를 막기
❌ 업로드/전처리 실패를 아무 표시 없이 삼키기
❌ 부분 실패를 전체 실패로 만들기
❌ 예외를 boolean 하나로 뭉개서 사유를 잃기
❌ 시크릿이나 토큰을 로그·에러 메시지에 넣기
```

## 4. 코드 규칙

| | |
|---|---|
| 언어 | Kotlin |
| 코드·주석·커밋 메시지 | **영어** |
| 사용자와의 대화 | 한국어 |
| 경로 | `java.nio.file.Path`. 문자열 연결 금지 |
| 플랫폼 분기 | `platform/` 안에서만 (`ConfigDir.kt` · `FolderPicker.kt`) |
| 주석 | **"왜"** 를 쓴다. "무엇"은 코드가 말한다 |
| UI 문자열 | 양쪽 모두 `values/strings.xml`(영어, 기본값) + `values-ko/strings.xml`. Android 는 `res/`, Desktop 은 `src/main/resources/` 에 있고 `i18n/Strings.kt` 가 읽는다. **새 문자열은 두 파일에 함께 넣는다** |

이 코드베이스는 주석에 **실제로 겪은 문제**를 남기는 관행이 있다. 이어가고, 기존
것을 지우지 마라.

```kotlin
// Measure the whole page span at once. Summing per-paragraph heights does not
// equal the height of measuring them together (line-spacing rounding differs),
// which used to push the last line off the page.
```

## 5. 이름

`moonkata` 는 패키지의 **조직 세그먼트로만** 남아 있다. 나머지는 전부 FloNovel.

| | |
|---|---|
| Android 패키지 · applicationId | `com.moonkata.flonovel.android` (debug 는 `.dev`) |
| Desktop 패키지 | `com.moonkata.flonovel.desktop` |
| Gradle group | `com.moonkata.flonovel` |
| Room DB | `flonovel_database` |
| DataStore | `flonovel_settings` |
| Dropbox 시크릿 | `/.flonovel/secret.json` (dev 는 `secret-dev.json`) |
| Supabase 테이블 | `flonovel_sync` |
| 요청 헤더 | `x-flonovel-secret` |
| JVM 시스템 프로퍼티 | `flonovel.dropbox.app_key` · `flonovel.dev` · `flonovel.supabase.*` |

새 코드·식별자·문자열에 옛 이름을 들이지 마라.

**Desktop 설정 디렉터리 이름은 두 곳에서 나오는데 철자가 반드시 같아야 한다.**

```text
configDir(DropboxConfig.appConfigDirName)  ->  FloNovel / FloNovelDev
    settings.json · books.json · credentials.json
configDir()  ->  DEFAULT_APP_DIR = "FloNovel"
    fonts/
```

예전에는 둘이 대소문자만 달라서(`FloNovel` vs `flonovel`) Windows·macOS 는 한
폴더로 합쳤지만 **Linux 에서는 폰트가 설정 폴더 옆에 따로 떨어졌다.** 지금은
맞춰져 있고, `ConfigDir.kt` 주석이 그 이유를 지키고 있다.

dev 빌드는 설정을 `FloNovelDev` 로 격리하지만 **폰트는 격리하지 않는다** —
`configDir()` 기본값은 dev 를 모른다. 용량 큰 다운로드를 dev/release 가 공유하는
편이 낫다는 판단이고, Dropbox 의 `books/` 를 공유하는 것과 같은 이유다.

**Android 백업 제외 규칙은 DataStore 파일명과 정확히 일치해야 한다.**
`res/xml/backup_rules.xml` 과 `res/xml/data_extraction_rules.xml` 이
`datastore/flonovel_settings.preferences_pb` 를 제외하는데, 그 파일에 Dropbox refresh
token 과 Supabase 시크릿이 들어 있다. DataStore 이름만 바꾸고 이 둘을 잊으면
**자격증명이 클라우드 백업으로 나간다.**

## 6. 테스트

- 새 behavior 에는 테스트를 함께 만든다.
- `reader/` 테스트는 가짜 `TextFitter` 로 UI 없이 돈다.
- 전처리·챕터 탐지 테스트는 `FloNovel-desktop/src/test/resources/fixtures/` 에 체크인된
  픽스처를 쓴다. 픽스처는 전각 공백·탭 들여쓰기·인접 중복 라인·3연속 개행·60자 초과
  챕터 제목처럼 **실제 파일에서 겪은 사례를 재현**한 것이다. 새 사례가 필요하면 픽스처를
  늘려라.
- **테스트가 개인 파일시스템 경로를 가리키게 하지 마라.** 클론한 사람 누구나 전체
  테스트를 돌릴 수 있어야 하고, 저장소가 공개라 경로와 파일명 자체가 노출된다. 큰 파일이
  필요하면 `TestFixtures.createSyntheticLargeFile` 로 합성한다.
- private 함수를 직접 테스트하지 않는다. behavior 로 잡는다.
- 테스트가 실패하면 **테스트를 고치지 말고 원인을 고친다.**

---

## 7. 저장소 밖에 있는 것들

일부 값은 이 저장소 밖에 사본이 있다. 여기서 바꾸면 저쪽도 손으로 바꿔야 하고,
**어긋나도 저장소 안에서는 아무도 못 잡는다.**

- **Supabase** — 테이블·트리거·RLS 정책은 프로젝트에 있지 저장소에 없다. 여기서
  이름을 바꾸면 저쪽에서 SQL을 다시 돌려야 한다.
- **Dropbox** — 앱 키와 리디렉트 URI 는 개발자 콘솔에서 온다. 앱 폴더 이름은 사용자가
  처음 연결할 때 고정되고, **앱 이름을 바꿔도 기존 폴더는 개명되지 않는다.**
  권한(scope)도 콘솔에서 켜야 한다. 양방향 전환으로 Android 가 `files.content.write` 를
  요청하게 되면, **이미 연결한 폰은 다시 연결해야** 새 권한이 토큰에 들어간다.
- **GitHub secrets** — 릴리스 워크플로가 7개를 읽는다. 여기서 키 이름이 바뀌면
  저쪽에도 새 secret 이 필요하다.
- **서명 키스토어** — 저장소 밖에 있고 `local.properties` 가 가리킨다. 그 경로가
  없으면 릴리스 빌드가 **조용히 디버그 키로 서명**하고도 성공한다. 빌드 결과가 아니라
  인증서를 확인해야 한다.

## 8. 작업 노트

작업 목록·계획·인계 파일은 `.work/` 에 넣는다. 루트에 두지 마라. **루트는 기본이
공개**라서 거기 남긴 것은 GitHub 에 올라간다.

## 9. Git 및 브랜치 워크플로

- `main` 브랜치는 항상 빌드와 단위 테스트가 100% 통과하는 안정 상태를 유지하며, 직접 커밋하지 않는다.
- **신규 기능 추가**: `feature/<feature-name>` 브랜치를 만들어 작업하고 해당 브랜치에 커밋/푸시한다. 구현 및 테스트 검증 완료 후 `main`에 머지하고 원격에 푸시한다.
- **버그 수정**: `fix/<bug-name>` 브랜치를 만들어 작업하고 해당 브랜치에 커밋/푸시한다. 재현 테스트 및 회귀 검증 완료 후 `main`에 머지하고 원격에 푸시한다.
- 머지 완료 후 작업 브랜치는 로컬과 원격에서 정리(삭제)한다.
- 커밋은 작게, 논리 단위로 분리한다. 한 커밋에 포매팅·리팩터·기능을 섞지 않는다.
- **커밋 메시지는 영어로 쓴다.**

## 10. 막혔을 때

**추측해서 진행하지 마라.** 설계와 동기화 계약은 확정되어 구현까지 끝난 상태다.
문서에 답이 없는 새 결정이나 아키텍처 변경이 필요해지면 가정하지 말고 알린다.
범위 밖의 문제를 발견하면 범위를 넓히지 말고 기록해서 보고한다.

## 11. 완료 기준

```text
□ 빌드 성공
□ 테스트 통과 (새 테스트 포함, 실제 실행 건수 확인)
□ 의도하지 않은 behavior 변경 없음
□ 새로 만든 인터페이스가 있다면 실제 구현이 둘 이상인가
□ 미사용 import·코드·의존성 없음
□ 양쪽 앱이 여전히 §1 계약에 합의하는가
```

**"컴파일된다"는 완료가 아니다.**
