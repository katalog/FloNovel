[English](README.en.md) · **한국어**

# FloNovel for Android

**폴더에 있는 텍스트 소설을 읽고, 듣고, PC와 이어 읽는 Android 리더.**

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](../LICENSE)

[전체 안내](../README.md) · [Desktop 앱](../FloNovel-desktop/README.md) · [이슈](https://github.com/katalog/FloNovel/issues)

## 기능

### 서재와 파일

- Storage Access Framework(SAF)로 폴더 선택, 경로 표시와 하위 폴더 탐색.
- `.txt` 및 ZIP 내부 `.txt` 읽기. ZIP은 디스크에 풀지 않습니다.
- 이름·날짜·크기 오름차순/내림차순 정렬, 책별 진행률, 시작 시 이어 읽기 제안.
- 파일·폴더 길게 누르기로 삭제. 동기화된 책의 삭제는 다른 기기에도 반영됩니다.
- UTF-8, EUC-KR/CP949 계열 자동 인코딩 판별.

### 읽기와 조작

- **페이지 넘김**과 **세로 연속 스크롤**, 없음·슬라이드·덮기 페이지 전환 효과.
- 표준 3분할 터치 영역: 왼쪽 이전 / 가운데 메뉴 / 오른쪽 다음.
- 칸마다 동작을 지정하는 **3×3 그리드**와 방향별 스와이프 설정.
- 페이지·챕터·챕터 점프 이동, 메뉴 토글, 동작 없음 중 선택. 볼륨 키 넘김도 지원.
- 목차, 패턴 프리셋·사용자 정규식, 본문 검색, 진행률로 위치 이동.
- 문자 오프셋으로 위치를 저장하고 화면 설정 변경 시 그 위치를 기준으로 다시 배치.

기본 가로 스와이프는 이전/다음 **챕터**, 세로 스와이프는 이전/다음 **챕터 점프 지점**입니다. 세로 스와이프 지정은 페이지 모드에 적용되고, 스크롤 모드에서는 세로 스크롤을 사용합니다.

### 독서 환경과 자동 넘김

- 여섯 테마: 웜 아이보리, 세피아 크림, 다크 네이비, 소프트 그레이, 쿨 라이트, 소프트 다크 브라운.
- 사용자 지정 배경·글자색, 글자 크기·줄 간격·자간·좌우/위/아래 여백.
- 나눔고딕, 나눔명조, Noto Sans KR, 리디바탕, Pretendard 다운로드.
- 밝기 직접 조절, 자동/세로/가로 방향, 화면 꺼짐 방지.
- 일정 간격으로 넘기는 타이머 모드.
- Android TTS로 읽고 발화 완료에 맞춰 넘기는 TTS 모드, 속도·음높이 조절(0.5–2.0).
- TTS 초기화 후 재생하며, 한국어 음성 데이터가 없거나 재생 실패 시 안내.
- 시스템 언어를 따르는 한국어·영어 UI.

## 시작하기

1. 앱을 설치하고 **폴더 추가**로 책 폴더를 고릅니다. 전처리·동기화를 위해 쓰기 권한도 필요합니다.
2. 책을 누릅니다. 기본 터치 영역의 가운데를 누르면 도구 모음이 나옵니다.
3. 설정에서 글꼴·테마·여백·제스처·자동 넘김을 조절합니다.
4. Dropbox를 사용할 경우 서재에서 연결하고 **지금 동기화**를 누릅니다.

### 텍스트 전처리

로컬 `.txt`는 처음 열거나 업로드하기 전에 정리됩니다. 먼저 서재의 `.flonovel/original/`에 원본을 백업합니다.

- 줄바꿈 통일, 줄 앞 공백·탭 정리, 인접 중복 내용 줄 제거, 빈 줄 정리.
- 탐지한 챕터에 `##` 표식과 파일 시작/끝 표식 추가.
- 확장자 제외 최대 50개 Unicode 코드 포인트로 파일명을 줄이고, 한글·한자 혼용 이름에서 한자를 제거.
- 숨김 임시 파일로 쓴 뒤 교체하며 중단된 처리는 다음 동기화에서 복구.
- 이미 처리된 책은 재처리하지 않습니다. Dropbox에서 정상 다운로드한 책도 이미 처리된 사본입니다.

Desktop과 같은 픽스처로 출력 바이트 일치를 검사합니다. 원래 서식을 보존해야 한다면 폴더 사본을 사용하세요.

## 동기화

[전체 설정 안내](../README.md#동기화-설정-선택)를 참고하세요. 파일 동기화에는 빌드에 Dropbox 앱 키가 필요합니다.

- 같은 Dropbox 앱·계정의 Desktop과 `/books`를 양방향으로 공유.
- 추가·수정·삭제·이동·이름 변경 반영. ZIP 내부 책은 파일 동기화에서 제외.
- 양쪽 수정은 원격 원본과 Android 충돌 사본으로 보존. 수정과 삭제가 충돌하면 수정 보존.
- 대량 삭제·빈 원격 서재는 확인 요청. 중단된 다운로드는 로컬 수정으로 올리지 않고 다시 받기.
- 서재가 앞으로 올 때 최대 1분에 한 번 자동 동기화, 수동 동기화 지원. 별도 백그라운드 동기화 작업 없음.
- 열린 책의 다운로드·삭제·충돌 처리는 닫을 때까지 보류.
- 홈 폴더를 바꾸면 기준·커서를 초기화하고 첫 동기화를 다시 시작.

위치 공유에는 Supabase 설정과 공유 키가 추가로 필요합니다. **Desktop을 먼저 연결해 키를 생성**하세요. Android는 키를 생성하지 않고 읽습니다. 다른 기기의 위치가 앞서 있으면 이동을 제안합니다.

예전 다운로드 전용 연결은 Dropbox를 다시 연결해 쓰기 권한을 부여해야 합니다.

## 빌드 및 설치

현재 [GitHub Releases](https://github.com/katalog/FloNovel/releases)에 게시된 APK는 없습니다.

필요한 환경:

- JDK 17 이상 — Gradle 실행용.
- Android SDK Platform 36과 빌드 도구, 또는 이를 설정할 Android Studio.
- 실행 기기: Android 7.0(API 24) 이상. `compileSdk` / `targetSdk`는 36, 바이트코드 대상은 Java 11.

저장소 루트에서:

```bash
cd FloNovel-android
./gradlew testDebugUnitTest assembleDebug
```

Windows에서는 `.\gradlew.bat testDebugUnitTest assembleDebug`를 사용합니다.

APK는 `app/build/outputs/apk/debug/app-debug.apk`입니다. 직접 설치하거나 연결한 기기에 다음 명령을 사용합니다.

```bash
./gradlew installDebug
```

디버그 ID는 `com.moonkata.flonovel.android.dev`이며 릴리스 앱과 나란히 설치됩니다.

### 선택적 빌드 설정

[local.properties.example](local.properties.example)을 `local.properties`로 복사하고 SDK 경로(`sdk.dir`)를 유지하세요.

- `DROPBOX_APP_KEY`: Desktop과 같은 Dropbox 앱 키.
- `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`: 같은 위치 서버.
- `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`: 릴리스 서명.

동일한 이름의 환경 변수로도 주입합니다. 동기화 값은 선택 사항입니다.

```bash
./gradlew assembleRelease
```

릴리스 APK: `app/build/outputs/apk/release/app-release.apk`. **키스토어 경로가 없으면 디버그 서명으로도 빌드는 성공**하므로 배포 인증서를 확인하세요.

`android-v*` 태그는 [릴리스 워크플로](../.github/workflows/android-release.yml)를 실행합니다. 단위 테스트 후 APK를 빌드·게시하며 태그에서 버전명을, CI 실행 번호에서 버전 코드를 가져옵니다. 배포에는 저장소의 서명·동기화 설정이 필요합니다.

> debug는 `secret-dev.json`으로 위치를 분리하지만 Dropbox `/books`는 릴리스와 같습니다. 개발 빌드의 파일 동기화는 실제 책 파일에 영향을 줄 수 있습니다.

## 테스트와 구조

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

- `app/src/test`: 인코딩, 챕터·페이지 계산, 전처리 패리티, 양방향 동기화 등 JVM 테스트.
- `app/src/androidTest`: Compose UI, Room, DataStore, Android 환경 검사. 기기/에뮬레이터가 필요하고 일부 글꼴 테스트는 네트워크를 사용합니다.
- 결과 XML: `app/build/test-results/testDebugUnitTest/`. 실제 실행 건수와 실패를 확인하세요.

소스는 `app/src/main/java/com/moonkata/flonovel/android/` 아래에 있습니다. `ui/`는 화면·ViewModel, `data/`는 파일·DB·설정·전처리·동기화, `tts/`는 TTS와 자동 넘김을 담당합니다.

기술: Kotlin · Jetpack Compose/Material 3 · Room · DataStore · Navigation Compose · SAF · juniversalchardet. 기여 규칙은 [AGENTS.md](../AGENTS.md)를 참고하세요.

## 문제 해결 및 지원 범위

- **빈 목차:** 제목 형식에 맞는 프리셋·정규식을 추가하세요. 탐지는 패턴 기반입니다.
- **TTS 실패:** Android 음성 엔진과 해당 언어 음성이 설치되어 있는지 확인하세요.
- **폴더 접근/쓰기 오류:** 폴더를 다시 선택해 SAF 권한을 받으세요.
- **공유 키 없음:** Desktop을 같은 Dropbox 앱·계정에 먼저 연결하세요.
- EPUB·PDF·MOBI는 지원하지 않습니다. 본문은 메모리에 읽어 들이므로 큰 책의 메모리 사용량은 기기에 따라 달라집니다.
- Dropbox 토큰·공유 키가 들어 있는 DataStore 파일은 Android 클라우드 백업에서 제외합니다.

## 라이선스

[Apache License 2.0](../LICENSE). 글꼴에는 각 글꼴의 라이선스가 적용됩니다.
