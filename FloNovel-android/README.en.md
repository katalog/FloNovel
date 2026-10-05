**English** · [한국어](README.md)

<div align="center">

<img src="../FloNovel-desktop/src/main/resources/icon.png" width="84" height="84" alt="FloNovel Android Icon" />

# FloNovel for Android

**A mobile text novel reader to browse local folders and seamlessly sync reading positions with PC**

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Storage-Room%20DB-orange)](https://developer.android.com/training/data-storage/room)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](../LICENSE)

[🌐 Project Overview](../README.en.md) · [🖥️ Desktop App](../FloNovel-desktop/README.en.md) · [💬 Issues](https://github.com/katalog/FloNovel/issues)

</div>

---

## ✨ Features

### 🗂️ Library & Smart File Browsing
- 📁 **Seamless SAF Integration:** Select any device folder through Storage Access Framework (SAF) and navigate subdirectories with breadcrumb navigation.
- 📦 **Direct ZIP Reading:** Read `.txt` directly inside `.zip` archives **without uncompressing to disk**.
- 🗃️ **Library Organization:** Sort by name, date, or size; visual reading progress percentages; auto-resume prompt on app launch.
- 🗑️ **Safe File Management:** Long-press to delete files and folders with full cloud sync trash safeguards.
- 🔤 **Smart Encoding Detection:** Auto-identifies UTF-8 and legacy Korean encodings (EUC-KR / CP949 / MS949).

---

### 📖 Reader & Gesture Controls
- 📄 **Viewer Modes:** Horizontal page turns or smooth continuous vertical scrolling (Transitions: None / Slide / Cover).
- 🎮 **Configurable 3×3 Grid & Gestures:**
  - **Standard 3-Column:** Left (previous page) / Center (toggle toolbar) / Right (next page).
  - **3×3 Touch Grid:** Divides screen into 9 cells with customizable actions (page turn, chapter skip, menu toggle, etc.).
  - **Swipes & Volume Keys:** Directional swipe gestures and physical volume key page turns.
- 📑 **Precision Navigation:** Automatically detected chapters, preset/custom regex patterns, and instant full-text search.
- 🎯 **Character Offset Anchoring:** Position is anchored by decoded character offsets so font or layout changes never shift your current sentence.

```text
┌────────────────────────────────────────┐
│            3×3 Touch Grid Zone         │
├──────────────┬──────────────┬──────────┤
│  Prev Page   │ Toggle Menu  │ Next Page│
├──────────────┼──────────────┼──────────┤
│ Prev Chapter │  Next Page   │ Next Page│
├──────────────┼──────────────┼──────────┤
│  Prev Jump   │  Next Page   │Next Chap │
└──────────────┴──────────────┴──────────┘
```

---

### 🎨 Reading Environment Customization
- 🌈 **6 Color Themes:** Warm Ivory, Sepia Cream, Dark Navy, Soft Gray, Cool Light, and Soft Dark Brown.
- 🎛️ **Granular Typography:** Fine-tune font size (sp), line height multiplier, letter spacing, and 4-way padding margins.
- 🔤 **Free Korean Font Downloader:** One-click downloader for Nanum Gothic, Nanum Myeongjo, Noto Sans KR, RIDIBatang, and Pretendard.
- ⏱️ **Timed Auto-Advance:** Hands-free reading with adjustable second-by-second auto page turns.
- ☀️ **Display Controls:** In-app brightness slider, orientation lock (portrait/landscape), and keep-screen-on toggle.

---

## ⚡ Text Preprocessing

Raw `.txt` novels are automatically formatted for reading comfort before first opening or cloud upload. **Originals are safely backed up to `.flonovel/original/` inside your library directory.**

- Unifies line breaks (`\n`), trims irregular indents and tabs, collapses redundant blank lines.
- Strips spam/repeated delimiter lines.
- Adds markdown-style `##` chapter headers and start/end book markers.
- Shortens long filenames (up to 50 Unicode code points) and cleans irregular characters.
- *Preprocessing is completely idempotent and guarantees 100% byte-for-byte parity with the Desktop version.*

---

## ☁️ Two-Way Synchronization

See the [Project Synchronization Guide](../README.en.md#optional-synchronization-setup) for setup instructions.

- 📦 **Dropbox File Sync:** Bi-directional sync with Desktop under `/books` (adds, edits, deletes, and renames).
- ⚡ **Supabase Position Sync:** Prompts to jump forward when a further reading position is detected on your PC.
- 🛡️ **Conflict Safeguards:** Concurrent edits fork into `(conflicted copy - Android - Date)` to prevent data loss.

---

## 📱 Build & Installation Guide

### Prerequisites
- **JDK 17+**
- **Android SDK Platform 36** (Build Tools 36)
- Target Device: **Android 7.0 (API level 24) or higher**

### Build Commands
From the repository root:

```bash
cd FloNovel-android

# Run unit tests and assemble Debug APK
./gradlew testDebugUnitTest assembleDebug

# Install directly to a connected Android device or emulator
./gradlew installDebug
```
*(On Windows PowerShell, use `.\gradlew.bat`)*

- The debug build uses application ID `com.moonkata.flonovel.android.dev` and installs side-by-side with production releases.

---

## 🧪 Testing & Architecture

```bash
cd FloNovel-android
./gradlew testDebugUnitTest          # JVM unit tests (Fast gate)
./gradlew compileDebugAndroidTestKotlin # Instrumentation test compilation check
```

- **Clean Architecture:**
  - `ui/`: Jetpack Compose screens (Library, Reader) with MVI ViewModel.
  - `data/`: Room Database, DataStore Preferences, SAF file engine, charset detector, preprocessor.
  - `tts/`: Timed auto page turn controller (`AutoPageTurnController`).

---

## 📄 License

Distributed under the [Apache License 2.0](../LICENSE).
