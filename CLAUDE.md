# CLAUDE.md

이 저장소의 작업 규칙이다. 소설 리더 앱 두 개(Android · Desktop)가 있고 **코드를
공유하지 않는다.** 대신 아래 계약을 양쪽이 지켜서 호환된다. 이 문서의 대부분이 그
계약이다.

> 이 파일과 `AGENTS.md` 만 한국어다. **커밋되어 GitHub에 올라가는 나머지 전부 —
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
cd FloNovel-desktop && ./gradlew test
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

### 동기화

- 테이블 `flonovel_sync`. 요청 헤더 `x-flonovel-secret`.
- **Supabase 요청에 `Authorization` 헤더를 넣지 마라.** JWT로 파싱하려다 실패하는데
  에러 메시지가 원인을 안 알려준다. (Dropbox 쪽 `Authorization: Bearer` 는 정상이다.)
- **`user_key` 를 보내지 마라.** 서버 트리거가 `hex(sha256(x-flonovel-secret))` 로
  계산하고, 그 해시가 곧 그 사용자의 데이터 파티션이다.
- **push 전에 원격을 조회해서 충돌을 해결하지 마라.** 트리거가 update 시
  `char_offset` 을 `greatest(new, old)` 로 클램프해 max-wins 를 강제한다.
  클라이언트가 충돌 해결을 하면 그것과 싸운다.
- 파일은 **단방향**이다: Desktop → Dropbox → Android.
- Dropbox 경로는 앱 폴더 루트 기준 **상대경로**만 쓴다 (`/books`,
  `/.flonovel/secret.json`). `/Apps/<이름>/...` 같은 절대경로는 쓰지 않는다 —
  앱 폴더 이름은 사용자마다 다르다.

---

## 2. 만들지 않을 것

```text
❌ 실제 구현이 하나뿐인 인터페이스 / 추상 클래스
❌ Gradle 모듈 분리
❌ DI 프레임워크, ORM, 리액티브 프레임워크
❌ "나중에 쓸" 빈 클래스나 확장 지점
❌ 사용하지 않는 의존성
```

새 인터페이스를 만들기 전에 답하라 — **지금 실제 구현이 둘 이상인가?**

현재 있는 인터페이스와 그 정당성:

| | 구현체 | 판단 |
|---|---|---|
| `TextFitter` (desktop) | `ComposeTextFitter` · `FakeTextFitter` | 정당. **reader 패키지에 주입되는 유일한 인터페이스** |
| `FolderBrowser` (android) | `SafFolderBrowser` · `FakeFolderBrowser` | 정당 |
| `SettingsController` (android) | ViewModel 들 | 정당. 설정 시트를 특정 VM에서 분리 |
| `BookDao` (android) | Room 생성 | Room 이 인터페이스를 요구함 |

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
| UI 문자열 | Android 는 `values/`(영어) + `values-ko/`. **Desktop 은 아직 한국어 하드코딩** — 현지화 작업 예정 |

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
□ 미사용 코드·의존성 없음
□ 양쪽 앱이 여전히 §1 계약에 합의하는가
```

**"컴파일된다"는 완료가 아니다.**
