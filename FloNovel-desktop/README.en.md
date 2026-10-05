**English** · [한국어](README.md)

<div align="center">

<img src="src/main/resources/icon.png" width="84" height="84" alt="FloNovel Desktop Icon" />

# FloNovel for Desktop

**A keyboard-driven text novel reader · 1-pane/2-pane spread views and seamless mobile synchronization**

[![Desktop](https://img.shields.io/badge/Desktop-Windows%20%7C%20macOS%20%7C%20Linux-0078D6?logo=windows&logoColor=white)](https://github.com/katalog/FloNovel)
[![Compose Desktop](https://img.shields.io/badge/Compose%20Desktop-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Kotlin JVM](https://img.shields.io/badge/Kotlin-JVM-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](../LICENSE)

[🌐 Project Overview](../README.en.md) · [📱 Android App](../FloNovel-android/README.en.md) · [💬 Issues](https://github.com/katalog/FloNovel/issues)

</div>

---

## ✨ Features

### 📖 Desktop Viewer (1-Pane / 2-Pane Spread)
- 📖 **1-Pane & 2-Pane Views:** Tailored for widescreen displays with book-like side-by-side two-pane spreads.
  - **Natural Eye Tracking:** In 2-pane mode, advancing moves forward by half the visible content (1 pane), **seamlessly shifting the right pane to the left** so reading flow is never interrupted.
- 🎬 **Smooth Page Turns:** Animated page turn transitions in 1-pane view with configurable speed.
- 🎯 **Character Offset Anchoring:** Resizing windows, altering fonts, or toggling between 1-pane/2-pane never loses your place.
- 📑 **Smart Table of Contents:** Automatic regex chapter detection, 4-way sub-chapter jump points, and high-speed search (`F2`).

```text
┌───────────────────────────┬───────────────────────────┐
│        [Left Pane]        │        [Right Pane]       │
│                           │                           │
│  "He slowly drew his      │  "The wind began to howl  │
│   sword."                 │   in the dark."           │
│                           │                           │
└───────────────────────────┴───────────────────────────┘
               ▼ [Press Next Page (.)]
┌───────────────────────────┬───────────────────────────┐
│        [Left Pane]        │        [Right Pane]       │
│                           │                           │
│  "The wind began to howl  │  (Next fresh text content │
│   in the dark."           │   continues here...)      │
│  (Right pane moved left)  │                           │
└───────────────────────────┴───────────────────────────┘
```

---

### 🗂️ Library & Automated File Management
- 📁 **Watched Home Folder:** Automatically detects new `.txt` files added to your novel directory, running preprocessing and adding them to the library.
- 📊 **Reading Status & Diagnostics:** Unread / Reading / Completed indicators, plus warning flags for books with low chapter density.
- 🔤 **Smart Encoding Conversion:** Seamlessly detects UTF-8 and legacy encodings (EUC-KR, CP949, MS949) and converts to clean UTF-8.
- 🗑️ **Safe File Deletions:** Pressing `Delete` sends files to a **designated backup folder** or the **PC Recycle Bin / Trash**, never permanently deleting without recovery options.

---

### 🎨 Reading Customization & Utilities
- 🌈 **6 Visual Themes:** Warm Ivory, Sepia Cream, Dark Navy, Soft Gray, Cool Light, and Soft Dark Brown.
- 🎛️ **Granular Typography:** Font weight slider, text size, line height, letter spacing, column max-width, and spread pane gap/ratios.
- 🔍 **Global UI Scaling:** Full UI scale slider optimized for 4K and high-DPI displays.
- 🔤 **Flexible Font Choices:** System fonts, built-in free fonts, and custom `.ttf` / `.otf` loaded directly from `fonts/`.
- ☕ **20-20-20 Eye Rest Reminder:** Built-in wellness timer suggesting 20-second distant focus every 20 minutes.
- 📻 **Integrated Internet Radio:** Stream MP3/AAC internet radio stations with an automatic sleep timer while reading.

---

## ⌨️ Default Keyboard Shortcuts

All shortcuts can be remapped anytime in Settings (`F4`).

| Key | Action | Description |
|---|---|---|
| `,` / `.` | Previous / Next Page | Page turn (advances 1 pane in 2-pane spread) |
| `PgUp` / `PgDn` | Previous / Next Jump | 4-part sub-chapter jump points |
| `[` / `]` | Previous / Next Chapter | Jump to adjacent chapter headers |
| `P` | Toggle Auto-Advance | Start/stop timed hands-free reading |
| `F1` | Go to Home | Return to the library screen |
| `F2` | Text Search | In-book search dialog |
| `F3` | Table of Contents | Chapter index list |
| `F4` | Settings | Open configuration dialog |
| `F7` / `F8` | Open Externally | Open file location in file manager / default app |
| `Delete` | Move to Trash / Backup | Safe deletion to Recycle Bin or backup folder |
| `Esc` | Close / Back | Close current dialog or return to library |

---

## ⚡ Text Preprocessing

Raw novel files are preprocessed before first read. **Originals are safely backed up to `.flonovel/original/` inside your library directory.**

- Saved as clean UTF-8.
- Normalizes line endings (`\n`), trims indentations/tabs, collapses redundant blank lines, removes adjacent duplicate lines.
- Adds markdown `##` chapter markers and book bounds.
- Normalizes filenames (up to 50 Unicode code points).
- *Guarantees 100% byte-for-byte parity with Android's preprocessing engine via shared fixtures.*

---

## ☁️ Two-Way Synchronization

See the [Project Synchronization Guide](../README.en.md#optional-synchronization-setup) for setup instructions.

- 📦 **Dropbox File Sync:** Bi-directional sync between your Desktop home folder and mobile `/books`.
- ⚡ **Supabase Position Sync:** **Connect Desktop first** to generate `secret.json` for reading offset sync with Android.
- 🛡️ **Trash Safety:** Remote deletions on PC are routed to the Recycle Bin or a designated backup folder.

---

## 🖥️ Build & Run Guide

### Prerequisites
- **JDK 17+**
- Supported Platforms: **Windows 10+**, **macOS 11+**, **Linux (x64 / arm64)**

### Commands
From the repository root:

```bash
cd FloNovel-desktop

# Run tests and launch debug desktop app
./gradlew test run
```
*(On Windows PowerShell, use `.\gradlew.bat`)*

### Packaging Installers
Generate native installer bundles for your host OS:

```bash
./gradlew packageDistributionForCurrentOS
```
- Output location: `build/compose/binaries/main/`
- Windows: `.msi` / `.exe`
- macOS: `.dmg`
- Linux: `.deb`

Portable distribution directory:
```bash
./gradlew createDistributable
```

---

## 📂 Configuration & Data Paths

- **Windows:** `%APPDATA%/FloNovel/`
- **macOS:** `~/Library/Application Support/FloNovel/`
- **Linux:** `$XDG_CONFIG_HOME/FloNovel/` (defaults to `~/.config/FloNovel/`)

Data files: `settings.json`, `books.json`, `credentials.json`, `sync-state.json`, `radio_streams.json`, `fonts/`.

---

## 📄 License

Distributed under the [Apache License 2.0](../LICENSE).
