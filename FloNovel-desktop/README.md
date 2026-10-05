[English](README.en.md) · **한국어**

<div align="center">

<img src="src/main/resources/icon.png" width="84" height="84" alt="FloNovel Desktop Icon" />

# FloNovel for Desktop

**키보드로 빠르게 읽는 텍스트 소설 리더 · 1쪽/2쪽 양면 보기와 모바일 동기화**

[![Desktop](https://img.shields.io/badge/Desktop-Windows%20%7C%20macOS%20%7C%20Linux-0078D6?logo=windows&logoColor=white)](https://github.com/katalog/FloNovel)
[![Compose Desktop](https://img.shields.io/badge/Compose%20Desktop-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Kotlin JVM](https://img.shields.io/badge/Kotlin-JVM-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](../LICENSE)

[🌐 전체 프로젝트 안내](../README.md) · [📱 Android 앱](../FloNovel-android/README.md) · [💬 이슈 제보](https://github.com/katalog/FloNovel/issues)

</div>

---

## ✨ 핵심 기능

### 📖 쾌적한 데스크톱 뷰어 (1쪽 / 2쪽 보기)
- 📖 **1-Pane 및 2-Pane(양면) 보기:** 대형 와이드 모니터 환경에 최적화된 두 쪽 나란히 보기 지원.
  - **직관적인 이동 규칙:** 2-Pane 모드에서 다음 페이지로 이동 시 보이는 분량의 절반(1페이지)만큼 전진하여, **우측 내용이 좌측으로 오면서 시선의 연속성을 완벽하게 유지**합니다.
- 🎬 **부드러운 애니메이션:** 1-Pane 보기 시 부드러운 페이지 전환 효과 및 속도 조절.
- 🎯 **문자 오프셋 기반 위치 앵커:** 창 크기 조절, 글자 크기 변경, 1-Pane/2-Pane 전환 시에도 읽던 문장을 정확하게 유지.
- 📑 **스마트 목차 탐색:** 실시간 챕터 패턴 탐지(프리셋 및 사용자 정규식), 챕터 내부 4분할 점프 지점, 본문 고속 검색(`F2`).

```text
┌───────────────────────────┬───────────────────────────┐
│        [좌측 Pane]        │        [우측 Pane]        │
│                           │                           │
│  "그는 천천히 검을 뽑았다."   │  "바람이 차갑게 불어왔다." │
│                           │                           │
└───────────────────────────┴───────────────────────────┘
               ▼ [다음 페이지(.) 이동 시]
┌───────────────────────────┬───────────────────────────┐
│        [좌측 Pane]        │        [우측 Pane]        │
│                           │                           │
│  "바람이 차갑게 불어왔다." │  (다음 새로운 내용 전개)     │
│   (우측 내용이 좌측으로 이동) │                           │
└───────────────────────────┴───────────────────────────┘
```

---

### 🗂️ 서재와 자동화 파일 관리
- 📁 **홈 폴더 실시간 감시:** 홈 폴더에 새로운 `.txt` 소설이 추가되면 자동으로 감지하여 전처리 및 서재에 등록.
- 📊 **읽기 상태 및 챕터 진단:** 미독·읽는 중·완독 진행률 표시, 챕터 표식 밀도가 낮은 소설 별도 표시.
- 🔤 **자동 인코딩 변환:** UTF-8 및 레거시 완성형(EUC-KR, CP949, MS949)을 완벽하게 자동 판별하여 UTF-8로 정규화.
- 🗑️ **안전한 파일 정리:** 리더나 서재에서 `Delete` 키를 누르면 즉시 영구 삭제되지 않고 **지정된 백업 폴더로 이동**하거나 **PC 휴지통**으로 안전하게 전송.

---

### 🎨 독서 환경 및 편의 기능
- 🌈 **6가지 감성 테마:** 웜 아이보리, 세피아 크림, 다크 네이비, 소프트 그레이, 쿨 라이트, 소프트 다크 브라운.
- 🎛️ **레이아웃 세부 튜닝:** 폰트 굵기(Weight), 폰트 크기, 줄 간격, 자간, 본문 최대 폭, 두 쪽 사이 간격 및 비율.
- 🔍 **전체 UI 스케일링:** 4K/고해상도 디스플레이를 위한 UI 배율 슬라이더 (UI와 본문 글자 크기가 조화롭게 스케일).
- 🔤 **자유로운 폰트 적용:** OS 시스템 폰트, 앱 내장 무료 폰트, `fonts/` 폴더에 넣은 사용자 정의 `.ttf` / `.otf` 자동 인식.
- ☕ **20-20-20 눈 휴식 알림:** 20분 독서마다 20초간 먼 곳을 바라보도록 안내하는 건강 타이머.
- 📻 **인터넷 라디오 내장:** 책을 읽으며 들을 수 있는 MP3/AAC 스트리밍 라디오 플레이어 (슬립 타이머 지원).

---

## ⌨️ 기본 단축키 가이드

설정(`F4`)에서 원하는 키로 언제든지 재지정할 수 있습니다.

| 단축키 | 동작 | 설명 |
|---|---|---|
| `,` / `.` | 이전 / 다음 화면 | 페이지 넘김 (2-pane 시 1페이지씩 전진) |
| `PgUp` / `PgDn` | 이전 / 다음 점프 지점 | 챕터 내부 4분할 단위 이동 |
| `[` / `]` | 이전 / 다음 챕터 | 목차 챕터 기준 바로 이동 |
| `P` | 자동 넘김 토글 | 타이머 기반 자동 읽기 시작/정지 |
| `F1` | 홈 화면으로 이동 | 서재 목록으로 복귀 |
| `F2` | 본문 검색 | 단어 및 문장 고속 검색 |
| `F3` | 목차 목록 | 자동 탐지된 챕터 목록 다이얼로그 |
| `F4` | 설정 열기 | 글꼴, 테마, 여백, 동기화 설정 |
| `F7` / `F8` | 외부 탐색 | 탐색기에서 파일 위치 열기 / 기본 연결 프로그램으로 열기 |
| `Delete` | 파일/폴더 삭제 | 휴지통 또는 지정 백업 폴더로 이동 |
| `Esc` | 닫기 / 뒤로가기 | 다이얼로그 닫기 또는 서재로 이동 |

---

## ⚡ 텍스트 전처리 (Text Preprocessing)

로컬의 원시 `.txt` 소설은 처음 열거나 서재에 등록될 때 자동으로 가독성이 개선됩니다. **첫 처리 시 원본은 홈 폴더의 `.flonovel/original/`에 안전하게 백업됩니다.**

- 인코딩 감지 후 깔끔한 UTF-8로 저장.
- 줄바꿈 통일(`\n`), 문단 앞 불필요한 공백·탭 정리, 3연속 이상 빈 줄 축소, 인접 중복 줄 제거.
- 탐지된 챕터 제목에 `##` 마크다운 표식 및 시작/끝 마커 부여.
- 한글·한자 혼용 파일명에서 한자 제거 및 50자 이내 정규화.
- *Android 앱과 100% 동일한 골든 픽스처 테스트를 통과한 멱등성 엔진입니다.*

---

## ☁️ 양방향 동기화

상세 설정법은 [전체 프로젝트 동기화 안내](../README.md#동기화-설정-선택)를 참고하세요.

- 📦 **Dropbox 파일 동기화:** 데스크톱의 소설 홈 폴더와 모바일의 `/books` 폴더를 양방향 동기화.
- ⚡ **Supabase 위치 동기화:** **Desktop을 먼저 연결**하여 `secret.json`을 발급받으면 모바일 앱과 읽던 위치가 실시간 동기화됩니다.
- 🛡️ **안전 장치:** 원격 삭제 시 PC 휴지통 또는 지정 백업 폴더로 이동하여 파일 유실을 원천 방지합니다.

---

## 🖥️ 빌드 및 실행 가이드

### 개발 환경 요구 사항
- **JDK 17 이상**
- 지원 OS: **Windows 10+**, **macOS 11+**, **Linux (x64 / arm64)**

### 실행 및 빌드 명령어
저장소 루트 기준:

```bash
cd FloNovel-desktop

# 테스트 실행 및 디버그 모드 실행
./gradlew test run
```
*(Windows PowerShell 환경에서는 `.\gradlew.bat`를 사용합니다.)*

### 배포용 설치 파일 패키징
현재 OS에 맞는 인스톨러 패키지를 빌드합니다:

```bash
./gradlew packageDistributionForCurrentOS
```
- 결과물 생성 위치: `build/compose/binaries/main/`
- Windows: `.msi` / `.exe`
- macOS: `.dmg`
- Linux: `.deb`

무설치 포터블 디렉터리 생성:
```bash
./gradlew createDistributable
```

---

## 📂 데이터 저장 경로

- **Windows:** `%APPDATA%/FloNovel/`
- **macOS:** `~/Library/Application Support/FloNovel/`
- **Linux:** `$XDG_CONFIG_HOME/FloNovel/` (기본값 `~/.config/FloNovel/`)

설정(`settings.json`), 서재 캐시(`books.json`), 자격 증명(`credentials.json`), 동기화 기준 베이스(`sync-state.json`), 라디오 목록(`radio_streams.json`), 폰트(`fonts/`).

---

## 📄 라이선스

이 프로젝트는 [Apache License 2.0](../LICENSE) 하에 배포됩니다.
