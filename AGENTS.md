# AGENTS.md

이 문서는 FloNovel 프로젝트의 최상위 작업 지침이자 계약서이다. 소설 리더 앱 두 개(Android · Desktop)가 단일 저장소에 있으며, **코드를 직접 공유하지 않고 아래의 계약을 엄격히 준수함으로써 상호 호환**된다.

안티그라비티 IDE(및 다른 에이전트 세션)는 모든 작업에서 이 문서를 최우선 규칙으로 준수해야 한다.

> **언어 규칙**:
> - 사용자와의 대화, 그리고 본 가이드 문서(`AGENTS.md`, `CLAUDE.md`)만 **한국어**를 사용한다.
> - 커밋되어 코드베이스에 남는 모든 것(**코드, 주석, README, 커밋 메시지, 워크플로, PR 등**)은 반드시 **영어로만** 작성한다.

---

## 1. 프로젝트 구조 및 빌드 규칙

### 디렉터리 레이아웃
```text
FloNovel-android/   Android 앱 (Kotlin · Compose · Room)
FloNovel-desktop/   Desktop 앱 (Kotlin/JVM · Compose Desktop)
.work/              비공개 작업 및 계획 파일 (gitignored — 절대 커밋 금지)
```

### 빌드 및 테스트 규칙
- **루트에 Gradle 프로젝트가 없다.** 두 앱은 완전히 독립적인 Gradle 빌드를 가지므로, 반드시 해당 앱 디렉터리로 이동(`cd`)하여 Gradle 명령을 실행해야 한다.
- **`BUILD SUCCESSFUL` 문구만으로 테스트 통과를 단정하지 마라.** 실행된 테스트가 0건이어도 빌드 성공으로 출력된다. 항상 `build/test-results/`의 XML 파일을 확인하여 실제 실행 건수를 검증하라.

```bash
# Android 검증
cd FloNovel-android && ./gradlew testDebugUnitTest   # 기본 빠른 게이트
cd FloNovel-android && ./gradlew assembleDebug

# Desktop 검증
cd FloNovel-desktop && ./gradlew test                # 단위 테스트
cd FloNovel-desktop && ./gradlew run                 # 실행 검증
```

> **주의**: Android 계측 테스트(`app/src/androidTest`)는 실제 기기/에뮬레이터가 필요하고 외부 네트워크 폰트를 조회하므로 기본 게이트로 실행하지 않는다.

---

## 2. 핵심 계약 — 하나라도 위반 시 반대편 앱이 조용히 깨짐

### (1) 읽기 위치 (Reading Position)
- 읽기 위치는 **디코딩된 원문의 문자 오프셋(Char Offset)**이다. 페이지 번호나 바이트 오프셋이 아니다. 글자 크기나 창 크기가 바뀌어도 가리키는 텍스트가 변하지 않아야 하기 때문이다.
  - Desktop: `BookRecord.anchor`
  - Android: `BookEntity.lastReadCharOffset`
- **anchor는 단 하나뿐이다.** "화면 표시용 위치"와 "동기화용 위치"를 분리하지 않는다.
- 페이지 번호는 저장하는 값이 아니며, 레이아웃/화면 크기에 따라 런타임에 동적으로 계산되는 파생값이다.

### (2) 이동 규칙 (Desktop vs Android 분리)
- **Desktop**: 1-pane과 2-pane 모두 동일한 원칙 — *"현재 화면에 보이는 양의 절반만큼 전진"*으로 동작한다. 구현은 `reader/ReaderNavigator.kt`의 `advance(ratio)` 단 한 벌로 처리한다.
- **Android**: 1-pane 전용이며 `ReaderViewModel` 내에 자체 페이지 이동 로직이 있다.
- **두 앱의 이동 로직을 억지로 하나로 합치려 하지 마라.**

### (3) 전처리와 등록 (Preprocessing & Registration)
- 순서는 반드시 **전처리 → 등록**이다. 전처리가 파일명과 문자 오프셋을 모두 바꾸므로, 순서가 뒤바뀌면 읽던 위치를 영구히 잃게 된다.
- `preprocessedAt` 속성이 이미 존재하는 책은 **절대로 재전처리하지 않는다.**
- 전처리는 **멱등(Idempotent)**이어야 한다. 이미 처리된 파일에 다시 수행해도 바이트 단위로 100% 동일한 결과가 나와야 한다.
- 원본 텍스트를 비원자적으로 덮어쓰지 말며, 최초 처리 시 반드시 원본 백업을 보존한다.

### (4) 챕터 (Chapter Detection)
- 챕터 목록은 DB나 파일에 **저장하지 않는다.** 책을 열 때마다 원문에서 실시간으로 탐지한다.
- 전처리기가 부여한 `##` 표식은 **줄 길이 제한 없이 전적으로 신뢰**한다. (임의로 60자 제한 등의 가드를 붙이지 말 것. 과거 정규식 가드로 인해 챕터 목록의 19.6%가 유실된 전력이 있음).
- 줄 길이 가드는 오직 사용자가 직접 등록한 커스텀 정규식에만 적용된다.

### (5) 경로 정규화 (Path Normalization)
양쪽 앱이 동일한 책에 대해 반드시 일치하는 고유 키를 생성해야 한다. 정규화 순서는 **구분자 변경 → NFC 정규화 → 소문자 변환**이며, 이 순서를 임의로 변경해서는 안 된다.

```kotlin
relativePath.replace('\\', '/')            // 1. 구분자 통일
    .let { Normalizer.normalize(it, NFC) } // 2. NFC 정규화
    .lowercase()                           // 3. 소문자 변환
```

### (6) 동기화 계약 (Sync Contract)
- **Supabase**:
  - 테이블: `flonovel_sync` / 요청 헤더: `x-flonovel-secret`
  - **Supabase 요청에 `Authorization` 헤더를 절대 포함하지 마라.** (JWT 파싱 실패를 유발함. 단, Dropbox의 `Authorization: Bearer`는 정상).
  - **요청 본문에 `user_key`를 보내지 마라.** 서버 트리거가 `hex(sha256(x-flonovel-secret))`로 자동 계산하여 유저 파티션을 격리한다.
  - **Push 전에 클라이언트가 원격을 조회해 임의로 충돌을 해결하지 마라.** 서버 트리거가 update 시 `greatest(new, old)`로 `char_offset`을 클램프하여 Max-wins 정책을 강제한다.
- **Dropbox & 파일 동기화**:
  - 소설 파일 동기화는 **단방향**이다: Desktop → Dropbox → Android.
  - Dropbox 경로는 항상 앱 폴더 기준 **상대 경로**만 사용한다 (`/books`, `/.flonovel/secret.json`). `/Apps/...` 형태의 절대 경로는 절대 사용하지 않는다.

---

## 3. 만들지 말아야 할 것 (Anti-Patterns)

불필요한 복잡성과 오버엔지니어링을 철저히 배제한다.

```text
❌ 실제 구현체가 하나뿐인 인터페이스나 추상 클래스
❌ 무분별한 Gradle 모듈 분리
❌ DI 프레임워크 (Hilt, Koin 등), 무거운 ORM, 리액티브 프레임워크
❌ "미래에 쓸지도 모르는" 가상의 확장 지점이나 빈 클래스 (YAGNI 위반)
❌ 사용하지 않는 외부 라이브러리/의존성 추가
```

> **인터페이스 생성 기준**: 새 인터페이스를 작성하기 전 *"현재 시점에 서로 다른 실제 구현체가 둘 이상 존재하는가?"*를 자문하라.
> 현재 허용된 인터페이스:
> - `TextFitter` (Desktop: `ComposeTextFitter`, `FakeTextFitter`) — reader 패키지의 유일한 외부 추상화
> - `FolderBrowser` (Android: `SafFolderBrowser`, `FakeFolderBrowser`)
> - `SettingsController` (Android: ViewModel 간 설정 시트 분리)
> - `BookDao` (Android: Room 라이브러리 요구사항)

---

## 4. 안정성 및 예외 처리

```text
❌ 동기화나 전처리 실패로 인해 사용자의 책 읽기가 차단되는 상황
❌ 업로드나 전처리 실패를 사용자 알림/로그 없이 조용히 삼키는 행위
❌ 일부 실패를 전체 프로세스 크래시로 확산시키는 행위
❌ 구체적 예외를 단순 boolean 플래그 하나로 뭉개서 실패 원인을 상실하는 행위
❌ 로그나 UI 에러 메시지에 인증 토큰, 시크릿 키를 평문으로 노출하는 행위
```

---

## 5. 코드 스타일 및 명명 규칙

| 구분 | 규칙 |
|---|---|
| **언어** | Kotlin |
| **코드·주석·커밋** | **영어 (English only)** |
| **사용자 대화** | 한국어 |
| **파일 경로** | 문자열 연결 금지, 반드시 `java.nio.file.Path` 사용 |
| **플랫폼 분기** | `platform/` 패키지 내로만 엄격히 격리 (`ConfigDir.kt`, `FolderPicker.kt`) |
| **주석 원칙** | 코드가 말하는 "What"이 아니라, **설계 의도와 겪었던 문제 맥락("WHY")**을 기록 |

> **주석 보존 관행**: 이 코드베이스는 버그가 재발하지 않도록 과거에 발생했던 이슈의 배경을 주석으로 상세히 남기는 관행이 있다. 기존 주석을 임의로 축소하거나 삭제하지 마라.

### 고유 식별자 및 이름 규칙
- `moonkata`는 패키지의 조직 도메인 세그먼트(`com.moonkata.flonovel.*`)에만 한정하여 남긴다.
- 식별자 명명:
  - Android 패키지 / applicationId: `com.moonkata.flonovel.android` (debug는 `.dev`)
  - Desktop 패키지: `com.moonkata.flonovel.desktop`
  - Gradle group: `com.moonkata.flonovel`
  - Room DB: `flonovel_database`
  - DataStore: `flonovel_settings`
  - Dropbox 시크릿 파일: `/.flonovel/secret.json` (dev는 `secret-dev.json`)
  - Supabase 테이블: `flonovel_sync` / 요청 헤더: `x-flonovel-secret`
  - JVM 시스템 프로퍼티: `flonovel.dropbox.app_key`, `flonovel.dev`, `flonovel.supabase.*`
- **Desktop 설정 디렉터리 이름 불일치 금지**:
  - `FloNovel` / `FloNovelDev` 대소문자 철자가 정확해야 한다. Linux 환경에서는 대소문자가 다르면 폰트 폴더가 엉뚱한 경로로 분리된다.
- **Android 백업 제외 규칙 동기화**:
  - `res/xml/backup_rules.xml`과 `res/xml/data_extraction_rules.xml`에 지정된 `datastore/flonovel_settings.preferences_pb` 이름을 절대 깨뜨리지 마라. 토큰과 시크릿이 클라우드로 유출되는 것을 막는 보안 설정이다.

---

## 6. 테스트 원칙

- 새로운 동작(Behavior)을 구현할 때는 반드시 이를 검증하는 테스트를 함께 작성한다.
- `reader/` 테스트는 `FakeTextFitter`를 활용하여 무거운 UI 렌더링 없이 순수 로직 단위로 검증한다.
- 전처리·챕터 탐지 테스트는 `FloNovel-desktop/src/test/resources/fixtures/`에 체크인된 픽스처를 활용하여 검증한다. 픽스처는 전각 공백, 탭 들여쓰기, 인접 중복 라인, 3연속 개행, 60자 초과 챕터 제목 등 **실제 파일에서 겪었던 사례를 재현**한 것이다. 새로운 사례가 필요하면 픽스처를 추가하라.
- **테스트가 개인 파일시스템의 절대 경로를 참조하도록 작성하지 마라.** 저장소를 클론한 누구나 전체 테스트를 실행할 수 있어야 하며, 공개 저장소이므로 경로와 파일명 자체가 노출된다. 대용량 파일이 필요한 경우 `TestFixtures.createSyntheticLargeFile`로 합성하여 사용한다.
- private 함수를 억지로 열어 테스트하지 말고, public API 동작(Behavior)을 통해 테스트한다.
- 테스트 실패 시, 테스트 단언문을 수정하여 넘기려 하지 말고 실패 원인 코드를 수정하라.

---

## 7. 저장소 외적 종속성 (External Dependencies)

코드베이스 외부에 존재하는 리소스이므로, 이름을 변경할 경우 외부 환경과 동기화해야 한다:
- **Supabase**: 테이블, 트리거, RLS 정책은 Supabase 대시보드/SQL에서 관리된다.
- **Dropbox**: 앱 키 및 리디렉트 URI는 개발자 콘솔 종속이며, 앱 폴더명은 최초 연동 시 고정된다.
- **GitHub Secrets**: CI/CD 배포 워크플로가 참조하는 7개 시크릿.
- **서명 키스토어**: `local.properties`가 가리키며, 누락 시 디버그 서명으로 조용히 대체되므로 릴리스 시 인증서를 직접 검증해야 한다.

---

## 8. 작업 절차 및 Git 관리

- **작업 파일 위치**: 임시 작업 목록, 마이그레이션 계획, 인계 노트 등은 반드시 `.work/` 폴더에 생성한다. 루트 디렉터리에 작업 메모를 남겨 두지 마라.
- **Git 초기화 시점**: 본 프로젝트의 `git init`은 모든 기능 구현 및 검증이 완전히 마무리된 최종 단계에 수행한다.
- **커밋 단위**: 논리적으로 분리된 단위로 작게 커밋하며, 포매팅·리팩터링·기능 추가를 단일 커밋에 섞지 않는다. 커밋 메시지는 영어로 작성한다.
- **불확실할 때**: 추측하여 설계를 변경하지 말고, 아키텍처나 계약에 걸치는 의문점은 명확히 확인 후 진행한다.

---

## 9. 작업 완료 기준 (Definition of Done)

작업 완료를 선언하기 전 반드시 다음 항목을 체크하라:

```text
[ ] 각 앱 디렉터리에서 Gradle 빌드 성공 (컴파일 성공만으로 완료 아님)
[ ] 단위 테스트 통과 (실제 실행 건수를 XML 리포트로 확인 완료)
[ ] 기존 기능 및 UI 동작에 의도치 않은 회귀(Regression) 없음
[ ] 불필요한 단일 구현체 인터페이스나 추상 계층이 추가되지 않음
[ ] 미사용 import, 코드, 라이브러리 의존성이 정리됨
[ ] §2의 읽기 위치, 전처리, 동기화 계약이 완벽히 보존되었는가
```
