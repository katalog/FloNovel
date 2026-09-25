# AGENTS.md

소설 리더 앱 두 개(Android · Desktop)의 작업 규칙이다. 두 앱은 **코드를 공유하지 않고**
아래 계약을 양쪽이 지켜서 호환된다.

**모든 에이전트(Claude Code, Codex, Antigravity 등)의 단일 기준 문서다.** `CLAUDE.md` 는
이 파일을 불러오기만 하므로 규칙은 여기에만 쓴다. 코드 주석이 `AGENTS.md §1` 처럼 절
번호로 가리키므로, 절 번호를 바꾸면 그 참조도 함께 고친다.

> 이 파일과 `CLAUDE.md` 만 한국어다. **커밋되어 GitHub에 올라가는 나머지 전부 —
> 코드, 주석, README, 워크플로, 커밋 메시지 — 는 영어로만 쓴다.** 사용자와의 대화는 한국어.

## 구조

```text
FloNovel-android/   Android 앱 (Kotlin · Compose · Room)
FloNovel-desktop/   Desktop 앱 (Kotlin/JVM · Compose Desktop)
.work/              비공개 작업 파일 — gitignore 됨
```

작업 목록·계획·인계 파일은 `.work/` 에 넣는다. **루트는 기본이 공개**라서 거기 남긴 것은
GitHub 에 올라간다.

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

- 순서는 **전처리 → 등록 → 업로드**, 예외 없다. 전처리가 파일명과 문자 위치를 둘 다
  바꾸므로 거꾸로 하면 읽던 위치를 잃는다.
- `preprocessedAt` 이 있는 책은 **다시 전처리하지 않는다.**
- 전처리는 멱등이다. 이미 처리된 텍스트에 다시 돌려도 바이트 단위로 같은 결과가 나온다.
- 원본을 비원자적으로 덮어쓰지 않는다. 최초 처리 때 백업을 남긴다.
- Dropbox 에는 전처리된 파일만 존재한다. 그래서 전처리기가 두 앱 모두에 있는데, 코드를 공유하지
  않는 대신 **같은 픽스처에 바이트 단위로 같은 출력**을 내는지 양쪽 테스트로 맞춘다.
  한쪽만 고치면 두 기기가 같은 책을 서로 다르게 고쳐 올리며 충돌 사본을 계속 만든다.
  - 픽스처와 기대 출력은 양쪽 `src/test/resources/fixtures/parity/` 에 같은 사본으로 있고, 각
    앱 테스트가 상대 사본과 바이트 비교까지 한다. 전처리를 바꾸면 Desktop 에서
    `./gradlew test -PupdateGolden` 으로 기대 출력을 다시 만들고 Android 쪽에 복사한다.
  - Android 이식본은 `\d` `\s` `.` `\p{IsHangul}` 와 IGNORE_CASE 를 쓰지 않는다. Android 의
    정규식 엔진(ICU)에서는 뜻이 넓어서(`\d` 가 전각 숫자까지) 같은 입력이 다르게 나온다.
    문자 범위를 직접 쓰고 스크립트는 `Character.UnicodeScript` 로 판정한다.
  - 인코딩 판별도 양쪽이 같은 결정을 한다(EUC-KR 판정은 MS949 로, 판정 불가는 UTF-8).
  - Android(SAF, 원자적 교체 없음)는 원본을 `.flonovel/original/` 에 백업 → 숨김 임시 파일에
    쓰기 → 원본 삭제 → 이름 변경 순으로 하고, 끊기면 다음 동기화가 마무리한다. URI 가 바뀌므로
    **처음 열기(또는 올리기) 전**에만 한다. base 가 SYNCED 인 책은 여는 시점 확인을 건너뛴다.

### 챕터

- 챕터 목록은 **저장하지 않는다.** 책을 열 때마다 다시 탐지한다.
- 전처리기가 붙인 `##` 표식과 전처리기 자체의 제목 판정은 **길이 제한 없이** 다룬다.
  60자 줄 길이 가드는 사용자가 넣은 정규식에만 건다(`##` 에 물렸을 때 챕터 줄 19.6% 를 버렸다).

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
  클라이언트가 충돌 해결을 하면 그것과 싸운다.

### 동기화 — 파일 (Dropbox)

- 경로는 앱 폴더 루트 기준 **상대경로**만 쓴다(`/books`, `/.flonovel/secret.json`).
  `/Apps/<이름>/...` 는 쓰지 않는다 — 앱 폴더 이름은 사용자마다 다르다.
- **양방향이고, Dropbox `/books` 가 기준이다.** 두 앱 모두 추가·수정·삭제를 올리고 상대 변경을 받는다.
- **판정은 3자 비교다.** 기기마다 파일별 base(`rev`, `content_hash`, 로컬 크기, 로컬 수정시각)를
  저장하고 **로컬↔base**, **원격↔base** 를 따로 판정한다. 로컬과 원격을 직접 비교해 "한쪽에만
  있으니 새 파일/지운 파일"이라고 추론하지 마라 — 상대 기기가 추가한 파일을 지운다.
  base 는 Desktop 이 설정 폴더의 `sync-state.json`, Android 가 Room `sync_base` 에 둔다.
- **내용 비교는 `content_hash` 다.** 수정시각은 해시를 다시 계산할지 정할 때만 쓰고, **어느
  쪽이 최신인지 정하는 데 쓰지 않는다**(기기 시계가 달라 무한 재다운로드를 만든 적이 있다).
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
- **대량 삭제는 자동으로 하지 않는다.** 한 번의 동기화가 20개 이상, 또는 5개 이상이면서 추적
  중인 파일의 30% 이상을 지우게 되거나, 원격 `/books` 가 비어 있으면 멈추고 묻는다. 양방향 모두.
  계정을 잘못 연결하거나 앱 폴더를 초기화하면 양쪽 라이브러리가 함께 지워지기 때문이다.
- **다운로드 중인 파일은 업로드하지 않는다.** 다운로드 전에 base 에 `DOWNLOADING` 을 기록하고,
  그 상태의 파일은 다시 받는다. 빠지면 SAF 에 남은 잘린 파일이 "로컬 수정"으로 보여 원격을 덮는다.
- **이동과 이름 변경**: 한 회차에서 "base 가 있던 파일이 사라짐"과 "base 없는 새 파일"이 내용
  해시로 1:1 짝지어지면 이동이다. 로컬 이동은 Dropbox `move_v2`, 원격 이동은 로컬 파일 이동(재전송
  없음). 짝이 애매하면 삭제+추가로 둔다. 읽기 기록을 따라 옮기고 Supabase 위치를 새 경로 키로
  복사한다. 이동은 대량 삭제로 세지 않는다. Android 에서 전처리가 바꿀 이름으로 옮긴 경우는
  이동이 아니라 전처리를 거친다.
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

현재 있는 인터페이스 (Room DAO 는 Room 이 요구해서 예외):

| | 구현체 |
|---|---|
| `TextFitter` (desktop) — reader 패키지에 주입되는 유일한 인터페이스 | `ComposeTextFitter` · `FakeTextFitter` |
| `FolderBrowser` (android) | `SafFolderBrowser` · `FakeFolderBrowser` |
| `LibraryFiles` (android) | `SafLibraryFiles` · `FakeLibraryFiles` |
| `SettingsController` (android) | ViewModel 들 |
| `BookDao` · `SyncBaseDao` (android) | Room 생성 |

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
| 경로 | `java.nio.file.Path`. 문자열 연결 금지 |
| 플랫폼 분기 | `platform/` 안에서만 (`ConfigDir.kt` · `FolderPicker.kt`) |
| 주석 | **"왜"** 를 쓴다. "무엇"은 코드가 말한다 |
| UI 문자열 | 양쪽 모두 `values/strings.xml`(영어, 기본값) + `values-ko/strings.xml`. Android 는 `res/`, Desktop 은 `src/main/resources/` 에 있고 `i18n/Strings.kt` 가 읽는다. **새 문자열은 두 파일에 함께 넣는다** |

주석에 **실제로 겪은 문제**를 남기는 관행이 있다("예전에 X 때문에 마지막 줄이 밀려났다" 식).
이어가고, 기존 것을 지우지 마라.

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

- **Desktop 설정 폴더 이름**: `DropboxConfig.appConfigDirName`(설정 파일)과 `ConfigDir.kt` 의
  `DEFAULT_APP_DIR`(폰트)의 철자가 반드시 같아야 한다 — 대소문자만 달라도 Linux 에서 폴더가
  갈라진다(`ConfigDir.kt` 주석 참고). dev 빌드는 설정만 `FloNovelDev` 로 격리하고 폰트는
  일부러 공유한다.
- **Android 백업 제외 규칙은 DataStore 파일명과 정확히 일치해야 한다.** `res/xml/backup_rules.xml`
  · `data_extraction_rules.xml` 이 `datastore/flonovel_settings.preferences_pb`(Dropbox refresh
  token · Supabase 시크릿)를 제외한다. DataStore 이름만 바꾸면 **자격증명이 클라우드 백업으로 나간다.**

## 6. 테스트

- 새 behavior 에는 테스트를 함께 만든다.
- `reader/` 테스트는 가짜 `TextFitter` 로 UI 없이 돈다.
- 전처리·챕터 탐지 테스트는 `FloNovel-desktop/src/test/resources/fixtures/` 에 체크인된
  픽스처를 쓴다(전처리 멱등성: `TextPreprocessorTest.p1_idempotencyOnFixtureNovelFiles`). 픽스처는 전각 공백·탭 들여쓰기·인접 중복 라인·3연속 개행·60자 초과
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
  권한(scope)도 콘솔에서 켜야 하고, 새 권한은 **다시 연결해야** 기존 토큰에 들어간다.
- **GitHub secrets** — 릴리스 워크플로가 7개를 읽는다. 여기서 키 이름이 바뀌면
  저쪽에도 새 secret 이 필요하다.
- **서명 키스토어** — 저장소 밖에 있고 `local.properties` 가 가리킨다. 그 경로가
  없으면 릴리스 빌드가 **조용히 디버그 키로 서명**하고도 성공한다. 빌드 결과가 아니라
  인증서를 확인해야 한다.

## 8. Git 및 브랜치 워크플로

- `main` 브랜치는 항상 빌드와 단위 테스트가 100% 통과하는 안정 상태를 유지하며, 직접 커밋하지 않는다.
- **신규 기능 추가**: `feature/<feature-name>` 브랜치를 만들어 작업하고 해당 브랜치에 커밋/푸시한다. 구현 및 테스트 검증 완료 후 `main`에 머지하고 원격에 푸시한다.
- **버그 수정**: `fix/<bug-name>` 브랜치를 만들어 작업하고 해당 브랜치에 커밋/푸시한다. 재현 테스트 및 회귀 검증 완료 후 `main`에 머지하고 원격에 푸시한다.
- 머지 완료 후 작업 브랜치는 로컬과 원격에서 정리(삭제)한다.
- 커밋은 작게, 논리 단위로 분리한다. 한 커밋에 포매팅·리팩터·기능을 섞지 않는다.

## 9. 막혔을 때

**추측해서 진행하지 마라.** 설계와 동기화 계약은 확정되어 구현까지 끝난 상태다.
문서에 답이 없는 새 결정이나 아키텍처 변경이 필요해지면 가정하지 말고 알린다.
범위 밖의 문제를 발견하면 범위를 넓히지 말고 기록해서 보고한다.

## 10. 완료 기준

```text
□ 빌드 성공
□ 테스트 통과 (새 테스트 포함, 실제 실행 건수 확인)
□ 의도하지 않은 behavior 변경 없음
□ 새 인터페이스는 §2 를 통과했는가
□ 미사용 import·코드·의존성 없음
□ 양쪽 앱이 여전히 §1 계약에 합의하는가
```

**"컴파일된다"도, `BUILD SUCCESSFUL` 도 완료가 아니다.** 테스트가 0건 실행되고도 그렇게
나온다. `build/test-results/` 의 XML 을 파싱해 실제 건수를 세라.
