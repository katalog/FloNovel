[English](README.en.md) · **한국어**

# FloNovel for Desktop

**키보드로 읽는 텍스트 소설 리더. 한 쪽·두 쪽 보기와 Android로 이어지는 서재.**

![Desktop](https://img.shields.io/badge/Windows%20%C2%B7%20macOS%20%C2%B7%20Linux-0078D6)
![Compose](https://img.shields.io/badge/Compose%20Desktop-4285F4?logo=jetpackcompose&logoColor=white)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](../LICENSE)

[전체 안내](../README.md) · [Android 앱](../FloNovel-android/README.md) · [이슈](https://github.com/katalog/FloNovel/issues)

## 기능

### 리더와 이동

- 한 쪽 또는 두 쪽 나란히 보기. **기본 이동량은 보이는 분량의 절반**으로 두 쪽 보기의 오른쪽 내용이 왼쪽으로 옵니다.
- 한 쪽 넘김 애니메이션·속도, 두 쪽 챕터 왼쪽 정렬 옵션.
- 자동 챕터 탐지, 패턴 프리셋·사용자 정규식, 목차, 본문 검색.
- 챕터 내부 점프 지점(기본 4분할). 챕터가 없으면 고정 분량 이동.
- 문자 오프셋 기반 위치·진행률 저장, 시작 시 마지막 책 복원.
- 단축키 재지정과 타이머 자동 넘김, 책 끝에서 자동 넘김 중지.

### 서재와 파일 관리

- 홈 폴더·하위 폴더 탐색, 경로 표시, 최근·이름·날짜·크기 정렬.
- 미독·읽는 중·완독 진행 상태, 낮은 챕터 표식 밀도 표시.
- 홈 폴더 감시로 새 텍스트 파일 전처리·등록. 숨김 파일·폴더 제외.
- UTF-8, EUC-KR/CP949 계열 자동 인코딩 판별.
- 파일 관리자에서 위치 열기, 기본 앱으로 열기.
- 휴지통 또는 지정 폴더로 파일 보내기, 빈 폴더 삭제.

### 독서 환경

- 여섯 테마: 웜 아이보리, 세피아 크림, 다크 네이비, 소프트 그레이, 쿨 라이트, 소프트 다크 브라운.
- 글꼴·굵기·크기·줄 간격·자간·여백·최대 본문 폭.
- 두 쪽 간격·비율, **인터페이스와 글자에 적용되는 UI 배율**.
- 시스템 글꼴, 다운로드 글꼴, `fonts/`의 사용자 `.ttf` / `.otf`.
- 직접 다운로드·제작사 페이지·시스템 전용을 구분하는 글꼴 카탈로그.
- 시스템 언어 또는 수동 한국어·영어 UI, 창 위치·크기 저장.
- 20분마다 20초 휴식을 안내하는 **20-20-20 알림**.
- MP3/AAC 인터넷 라디오, 슬립 타이머, 사용자 방송국 목록.

라디오는 직접 연결되는 스트림을 사용합니다. 웹페이지·YouTube URL은 지원하지 않고 기본 방송국 이용 가능 여부는 외부 서비스에 달려 있습니다. Desktop에는 Android의 TTS가 없습니다.

## 시작하기

1. 아래 빌드 명령으로 실행합니다.
2. **홈 폴더 설정**에서 `.txt` 폴더를 선택합니다.
3. 책을 열고 `,` / `.`로 읽습니다. `F3`는 목차, `F2`는 검색, `F4`는 설정입니다.
4. 클라우드 동기화 설정에서 Dropbox 연결 후 첫 전체 동기화를 시작합니다.

## 기본 단축키

설정에서 다음 동작의 키를 재지정할 수 있습니다.

- `,` / `.`: 이전 / 다음 화면 이동.
- `PgUp` / `PgDn`: 이전 / 다음 챕터 점프 지점.
- `[` / `]`: 이전 / 다음 챕터.
- `Esc`: 뒤로, `F1`: 홈 폴더.
- `F2`: 검색, `F3`: 목차, `F4`: 설정.
- `F7`: 파일 관리자에서 열기, `F8`: 기본 앱으로 열기.
- `P`: 자동 넘김 토글, `Delete`: 선택한 파일 처리 또는 빈 폴더 삭제.

동작은 화면과 선택 항목에 따라 적용됩니다. 삭제 확인에는 다른 기기에도 반영된다는 안내가 포함됩니다.

## 파일 전처리

**전처리는 실제 책 파일을 다시 쓰고 이름을 바꿀 수 있습니다.** 처음 처리할 때 홈 폴더의 `.flonovel/original/`에 원본을 백업합니다.

- 인코딩 판별 후 읽고 UTF-8로 저장.
- 줄바꿈 통일, 들여쓰기 제거, 인접 중복 내용 줄 제거, 문단 간 빈 줄 정리.
- 탐지한 챕터에 `##` 표식과 파일 시작/끝 표식 추가.
- 한글·한자 혼용 파일명에서 한자 제거, 확장자 제외 최대 50개 Unicode 코드 포인트로 축약.
- 임시 파일·원자적 교체 사용, 처리 기록이 있는 책은 재처리하지 않음.

Android와 같은 픽스처로 출력 바이트 일치를 검사합니다. 전처리를 끄는 UI는 없으므로 원래 서식을 유지하려면 폴더 사본을 선택하세요.

## 동기화

[전체 설정 안내](../README.md#동기화-설정-선택)를 참고하세요.

- Dropbox 앱 폴더 `/books`와 양방향 파일 동기화.
- 로컬·원격을 마지막 기준과 각각 비교, 내용 해시·Dropbox 리비전으로 변경 판정.
- 양쪽 수정은 원격 원본·PC 충돌 사본 보존. 수정과 삭제의 충돌은 수정 보존.
- 원격 삭제는 Delete 키의 이동 폴더 설정과 관계없이 **항상 PC 휴지통**으로 이동.
- 대량 삭제·빈 원격 서재 확인. 1:1 내용 해시 매칭이 가능한 이동·이름 변경은 읽기 기록과 함께 이동 처리.
- 열린 책의 다운로드·삭제·충돌 처리는 닫을 때까지 보류.
- 첫 동기화 후 Dropbox 알림, 창 포커스 복귀(최대 1분에 한 번), 수동 요청으로 동기화.
- 진행 상태 표시, 일시 정지·재개.
- 홈 폴더 변경 시 기준·커서 초기화.

Supabase 위치 공유에는 별도 서버 설정이 필요합니다. 공유 키를 생성하도록 **Desktop을 먼저 연결**하세요. 다른 기기가 앞서 있으면 이동을 제안합니다. 일반 업로드는 뒤로 되돌리지 않고, 잘못된 원격 위치는 명시적인 **강제 업로드**로 교체할 수 있습니다. 공유 키 재생성은 새 위치 파티션을 사용하므로 이전 원격 위치가 더 이상 공유되지 않습니다.

## 소스 빌드

현재 [GitHub Releases](https://github.com/katalog/FloNovel/releases)에 Desktop 배포 파일은 없습니다. **JDK 17 이상**으로 실행·빌드합니다.

저장소 루트에서:

```bash
cd FloNovel-desktop
./gradlew test build
./gradlew run
```

Windows에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용합니다.

### 패키징

대상 OS에서 해당 패키징 도구를 준비해 만듭니다(Windows 설치 파일은 WiX 등).

```bash
./gradlew packageDistributionForCurrentOS
```

설정 형식은 Windows `.msi` / `.exe`, macOS `.dmg`, Linux `.deb`입니다. 결과물은 `build/compose/binaries/` 아래에 생성됩니다.

Windows EXE 또는 설치 파일 없는 실행 폴더를 별도로 만들 수 있습니다.

```bash
./gradlew packageExe
./gradlew createDistributable
```

Desktop 릴리스 자동화 워크플로는 현재 포함되어 있지 않습니다.

### 선택적 빌드 설정

[local.properties.example](local.properties.example)을 `local.properties`로 복사합니다.

- `DROPBOX_APP_KEY`: Dropbox App folder 앱 키.
- `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`: 위치 서버.
- `FLONOVEL_DEV=true`: 설정 폴더·위치 파티션을 개발용으로 분리.

동일한 이름의 환경 변수 또는 Gradle 속성 `-PdropboxAppKey`, `-PsupabaseUrl`, `-PsupabasePublishableKey`, `-PflonovelDev`도 지원합니다. 동기화 설정 없이 로컬 독서용으로 빌드됩니다.

> 개발 모드는 설정을 `FloNovelDev`, 공유 키를 `secret-dev.json`에 분리합니다. **Dropbox `/books`는 동일**하고 글꼴 캐시·라디오 목록도 기본 `FloNovel` 폴더를 사용합니다.

## 데이터 저장 위치

기본 설정 폴더:

- Windows: `%APPDATA%/FloNovel/`.
- macOS: `~/Library/Application Support/FloNovel/`.
- Linux: `$XDG_CONFIG_HOME/FloNovel/`, 미설정 시 `~/.config/FloNovel/`.

설정·서재·자격 증명·동기화 기준은 `settings.json`, `books.json`, `credentials.json`, `sync-state.json`입니다. 글꼴은 `fonts/`, 라디오 목록은 `radio_streams.json`입니다. 원본 백업은 **책 홈 폴더의 `.flonovel/original/`**에 있습니다.

## 테스트와 구조

```bash
./gradlew test
```

`src/test/`의 JVM 테스트는 이동·레이아웃, 인코딩·챕터 탐지, 전처리, 동기화, 설정 등을 검증합니다. 리더 테스트는 가짜 `TextFitter`로 UI 없이 실행합니다. 결과 XML은 `build/test-results/test/`입니다.

전처리 출력을 의도적으로 바꿀 때만:

```bash
./gradlew test -PupdateGolden
```

갱신한 `src/test/resources/fixtures/parity/` 기대 출력을 Android 대응 폴더에도 복사하고 양쪽 테스트를 실행하세요.

소스는 `src/main/kotlin/com/moonkata/flonovel/desktop/` 아래에 있습니다. `reader/`는 UI와 분리된 이동·레이아웃, `ui/`는 화면, `library/`는 서재·설정, `preprocess/`는 파일 처리, `sync/`는 동기화, `platform/`는 OS별 기능입니다.

기술: Kotlin/JVM · Compose Desktop · juniversalchardet · org.json · JNA · jlayer · javasound-aac. 기여 규칙은 [AGENTS.md](../AGENTS.md)를 참고하세요.

## 문제 해결 및 지원 범위

- **빈 목차:** 제목 형식에 맞는 패턴을 설정하세요. 낮은 챕터 표식 밀도는 점검 힌트이며 오류 판정은 아닙니다.
- **로그인 브라우저가 안 열림:** 앱이 보여 주는 URL을 복사해 브라우저에서 여세요.
- **서재 폴더를 못 찾음:** 홈 폴더를 다시 지정하고 첫 동기화를 시작하세요.
- `.txt` 전용입니다. EPUB·PDF·ZIP 읽기는 지원하지 않습니다.
- 본문은 메모리에 읽어 들이며 큰 파일의 시간·메모리 사용량은 환경에 따라 달라집니다.
- 로컬 독서는 오프라인으로 가능하고, 동기화·글꼴 다운로드·라디오는 네트워크가 필요합니다.

## 라이선스

[Apache License 2.0](../LICENSE). 글꼴에는 각 글꼴의 라이선스가 적용됩니다.
