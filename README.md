[English](README.en.md) · **한국어**

<div align="center">

# FloNovel

**내가 가진 텍스트 소설을 Android와 PC에서 이어 읽는 오픈소스 리더.**

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Desktop](https://img.shields.io/badge/Desktop-Windows%20%C2%B7%20macOS%20%C2%B7%20Linux-0078D6)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)

[Android 안내](FloNovel-android/README.md) · [Desktop 안내](FloNovel-desktop/README.md) · [이슈](https://github.com/katalog/FloNovel/issues)

</div>

FloNovel은 `.txt` 소설을 위한 두 개의 독립 앱입니다. 폴더 구조를 그대로 탐색하고, 텍스트를 정리하고, 챕터를 찾아 목차를 만듭니다. 읽기 위치를 페이지 번호가 아닌 **본문의 문자 오프셋**으로 저장하므로 글꼴·창 크기·읽기 방식을 바꿔도 읽던 위치를 기준으로 다시 배치합니다.

로컬 독서는 동기화 서비스 없이 사용할 수 있습니다. 선택적으로 **Dropbox에 책 파일**, **Supabase에 읽기 위치**를 동기화합니다. 별도의 FloNovel 계정을 만들지 않습니다.

## 주요 기능

### 두 앱의 공통 기능

- **텍스트 읽기:** UTF-8 및 EUC-KR/CP949 계열 인코딩 자동 판별.
- **목차와 이동:** 챕터 자동 탐지, 패턴 프리셋·사용자 정규식, 챕터 내부 점프 지점, 본문 검색.
- **읽기 위치:** 책별 진행률과 문자 오프셋 저장, 다른 기기의 더 앞선 위치로 이동 제안.
- **독서 화면:** 여섯 테마, 글꼴·크기·줄 간격·자간·여백 조절, 글꼴 다운로드.
- **텍스트 전처리:** 줄바꿈·들여쓰기·빈 줄 정리, 인접 중복 내용 줄 제거, 챕터 표식 추가, 파일명 정리, 처리 전 원본 백업.
- **선택적 양방향 동기화:** 추가·수정·삭제·이동·이름 변경 반영, 충돌 사본 보존, 대량 삭제 확인.
- **한국어·영어 UI:** Android는 시스템 언어를 따르고, Desktop은 언어를 직접 선택할 수도 있습니다.

### Android

- 페이지 넘김 또는 세로 연속 스크롤.
- 표준 3분할 터치 영역, 동작을 지정하는 3×3 그리드, 방향별 스와이프, 볼륨 키 넘김.
- 타이머 모드로 일정 간격 자동 넘김.
- SAF로 선택한 폴더 탐색, ZIP 내부 `.txt`를 디스크에 풀지 않고 읽기.
- 사용자 지정 배경·글자색, 밝기 조절, 화면 방향 고정, 화면 꺼짐 방지.

[Android 기능·사용법·빌드 →](FloNovel-android/README.md)

### Desktop

- 한 쪽 또는 두 쪽 보기. 기본 넘김은 보이는 분량의 절반만큼 전진하며, 두 쪽 보기에서는 오른쪽 내용이 왼쪽으로 이동.
- 단축키 재지정, 자동 넘김, 한 쪽 보기 넘김 애니메이션.
- 홈 폴더 감시와 새 파일 전처리, 최근·이름·날짜·크기 정렬, 챕터 표식이 적은 책 표시.
- 글꼴 굵기·최대 본문 폭·두 쪽 간격과 비율·UI 배율, 시스템·사용자 글꼴 사용.
- 20-20-20 눈 휴식 알림, 슬립 타이머가 있는 MP3/AAC 인터넷 라디오.
- 파일 관리자·기본 앱으로 열기, 휴지통 또는 지정 폴더로 보내기.

[Desktop 기능·단축키·빌드 →](FloNovel-desktop/README.md)

## 시작하기

1. 아래 안내로 앱을 빌드합니다.
2. Android에서는 **폴더 추가**, Desktop에서는 **홈 폴더 설정**으로 책 폴더를 선택합니다.
3. `.txt` 책을 엽니다. Android 기본 터치 영역은 왼쪽 이전·가운데 메뉴·오른쪽 다음입니다. Desktop은 `,` / `.`로 넘기고 `F4`로 설정을 엽니다.
4. 여러 기기에서 이어 읽으려면 아래 동기화 설정을 적용합니다.

> **전처리는 실제 파일을 변경합니다.** 처음 처리할 때 원본을 책 폴더의 `.flonovel/original/`에 백업합니다. 들여쓰기·빈 줄을 정리하고 제목 표식을 추가하며 파일명을 바꿀 수 있습니다. 원래 서식을 유지해야 한다면 책 폴더의 사본을 사용하세요.

## 설치 및 소스 빌드

현재 [GitHub Releases](https://github.com/katalog/FloNovel/releases)에 게시된 배포 파일은 없습니다. 각 앱을 소스에서 빌드할 수 있으며 Android APK 릴리스 워크플로는 저장소에 포함되어 있습니다.

**JDK 17 이상**이 필요합니다. Android는 추가로 **Android SDK Platform 36**과 빌드 도구가 필요합니다. Android Studio에서 SDK를 설정하거나 `local.properties`에 `sdk.dir`을 지정하세요.

루트에는 Gradle 프로젝트가 없습니다. 각 앱 폴더에서 실행합니다.

```bash
git clone https://github.com/katalog/FloNovel.git
cd FloNovel
```

Android:

```bash
cd FloNovel-android
./gradlew testDebugUnitTest assembleDebug
```

APK: `FloNovel-android/app/build/outputs/apk/debug/app-debug.apk`. Android 7.0(API 24) 이상에서 설치할 수 있습니다.

Desktop — 별도 터미널에서 저장소 루트부터:

```bash
cd FloNovel-desktop
./gradlew test build
./gradlew run
```

Windows에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용합니다. 설치 파일 생성은 [Desktop 안내](FloNovel-desktop/README.md#패키징)를 참고하세요. 동기화 설정값 없이도 빌드할 수 있으며, 키가 없으면 해당 동기화 기능을 사용할 수 없습니다.

## 동기화 설정 (선택)

### 책 파일: Dropbox

두 앱은 **같은 Dropbox 앱 키와 같은 Dropbox 계정**을 사용해야 합니다.

1. Dropbox 개발자 콘솔에서 **App folder** 앱을 만듭니다.
2. `account_info.read`, `files.metadata.read`, `files.metadata.write`, `files.content.read`, `files.content.write` 권한을 활성화합니다.
3. Desktop 리디렉트 URI `http://localhost:52475/oauth/callback`을 등록합니다. Android의 `db-<app key>://1/connect` 스킴은 앱 키에서 만들어집니다.
4. 각 앱의 [Android 예제](FloNovel-android/local.properties.example) / [Desktop 예제](FloNovel-desktop/local.properties.example)를 같은 폴더의 `local.properties`로 복사하고 `DROPBOX_APP_KEY`를 입력한 뒤 빌드합니다. Android SDK 설정도 유지하세요.
5. 각 앱에서 Dropbox를 연결하고 첫 동기화를 직접 시작합니다.

공유 서재는 Dropbox **앱 폴더의 `/books`**입니다. PC 책 폴더를 Dropbox 데스크톱 클라이언트의 동기화 폴더 안에 둘 필요는 없습니다.

- Android: 서재가 앞으로 올 때 최대 1분에 한 번, 또는 수동으로 동기화.
- Desktop: Dropbox 변경 알림, 창 포커스 복귀 시 최대 1분에 한 번, 또는 수동으로 동기화.
- 양쪽 수정은 원격 파일과 로컬 충돌 사본으로 모두 보존합니다.
- 원격 삭제를 PC에 반영할 때는 Delete 키 설정이 지정 폴더 이동이면 지정 폴더로 옮기고, 그 외에는 휴지통을 사용합니다.
- 한 회차에 20개 이상, 또는 5개 이상이면서 추적 파일의 30% 이상을 삭제하거나 원격 서재가 비어 있으면 확인을 요청합니다.
- 열린 책의 원격 변경은 닫을 때까지 미룹니다.
- Android ZIP 내부 책은 파일 동기화 대상이 아닙니다.

이전 다운로드 전용 Android 버전에서 연결했다면 쓰기 권한을 받도록 Dropbox를 다시 연결하세요.

### 읽기 위치: Supabase

Dropbox만으로 파일 동기화를 사용할 수 있습니다. 위치 공유에는 두 빌드에 동일한 `SUPABASE_URL`과 `SUPABASE_PUBLISHABLE_KEY`도 필요합니다.

서버는 `flonovel_sync` 테이블, 공유 시크릿으로 사용자를 구분하는 트리거·RLS 정책, 가장 앞선 위치를 유지하는 max-wins 규칙을 구현해야 합니다. **서버 SQL과 배포 스크립트는 이 저장소에 포함되어 있지 않습니다.** 새 프로젝트에 URL과 키만 넣는 것으로 설정이 완료되지는 않습니다. 필요한 계약은 [AGENTS.md §1](AGENTS.md#1-계약--하나라도-어기면-반대편-앱이-조용히-깨진다)에 있습니다.

**Desktop을 먼저 연결하세요.** Desktop이 Dropbox의 `/.flonovel/secret.json`에 공유 키를 생성하고 Android는 이를 읽습니다. 일반 업로드는 원격 위치를 뒤로 되돌릴 수 없습니다. Desktop의 명시적인 강제 업로드는 잘못된 원격 위치를 현재 로컬 위치로 교체할 때 사용합니다.

> **개발 모드의 격리 범위:** Android debug와 Desktop의 `FLONOVEL_DEV=true`는 `secret-dev.json`으로 읽기 위치 파티션을 분리합니다. 책 파일의 `/books`는 동일합니다. 실험용 파일 동기화에는 별도 Dropbox 계정/앱을 사용하세요.

## 지원 범위

- Desktop은 `.txt`, Android는 `.txt`와 ZIP 내부 `.txt`를 읽습니다. EPUB·PDF·MOBI는 지원하지 않습니다.
- 본문은 메모리에 읽어 들입니다. 큰 파일의 시간·메모리 사용량은 기기와 파일에 따라 달라집니다.
- 로컬 독서는 오프라인에서 가능합니다. 동기화·글꼴 다운로드·라디오는 네트워크가 필요합니다.
- TTS는 Android 음성 엔진과 해당 언어의 음성 데이터가 필요합니다.

## 개발 및 기여

```text
FloNovel-android/   Kotlin · Jetpack Compose · Room · DataStore
FloNovel-desktop/   Kotlin/JVM · Compose Desktop · JSON persistence
.github/workflows/  Android APK release workflow
```

두 앱은 코드를 공유하지 않고 문자 오프셋·경로 정규화·전처리·동기화 계약으로 호환됩니다.

- 변경 전 [AGENTS.md](AGENTS.md)를 읽으세요. 새 동작에는 테스트가 필요합니다.
- 전처리 변경은 양쪽 구현과 패리티 픽스처를 함께 수정합니다. Desktop에서 `./gradlew test -PupdateGolden`으로 기대 출력을 갱신하고 Android의 `app/src/test/resources/fixtures/parity/`에도 복사합니다.
- Android 단위 테스트는 `testDebugUnitTest`, Desktop은 `test`로 실행합니다. Android 계측 테스트는 기기/에뮬레이터가 필요하며 기본 게이트가 아닙니다.
- 테스트 결과 XML의 실제 실행 건수를 확인하세요. `BUILD SUCCESSFUL`만으로 통과를 판단하지 않습니다.
- 두 언어 README를 함께 갱신합니다. 코드·주석·커밋 메시지는 영어로 작성합니다.
- 버그 보고에는 앱·OS 버전, 재현 절차, 예상/실제 결과를 포함하고 개인 책·토큰·공유 키는 제외하세요.

## 라이선스

[Apache License 2.0](LICENSE). 다운로드하는 글꼴에는 각 글꼴의 라이선스가 적용됩니다.
