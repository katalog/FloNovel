**English** · [한국어](README.md)

<div align="center">

# FloNovel

**An open-source reader for your own text novels, across Android and desktop.**

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Desktop](https://img.shields.io/badge/Desktop-Windows%20%C2%B7%20macOS%20%C2%B7%20Linux-0078D6)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)

[Android guide](FloNovel-android/README.en.md) · [Desktop guide](FloNovel-desktop/README.en.md) · [Issues](https://github.com/katalog/FloNovel/issues)

</div>

FloNovel is a pair of independent apps for `.txt` novels. Browse existing folders, clean up text, and discover chapters for a table of contents. Positions use **character offsets in decoded text**, rather than page numbers, so layout changes use the same saved position when you change fonts, window size, or reading mode.

Local reading works without sync services. Optionally sync **book files through Dropbox** and **reading positions through Supabase**. No separate FloNovel account is required.

## Features

### Shared by both apps

- **Text reading:** automatic UTF-8 and EUC-KR/CP949-family encoding detection.
- **Navigation:** chapter detection, pattern presets/custom regular expressions, chapter jump points, and full-text search.
- **Reading positions:** per-book progress and character offsets, with an offer to jump to another device's more advanced position.
- **Appearance:** six themes; fonts, size, line height, letter spacing, margins, and font downloads.
- **Preprocessing:** normalize line endings/indentation/blank lines, remove adjacent duplicate content lines, add chapter markers, clean filenames, and back up originals.
- **Optional two-way sync:** additions, edits, deletions, moves, and renames; preserved conflict copies and mass-deletion confirmation.
- **Korean/English UI:** Android follows the system language; Desktop also offers manual selection.

### Android

- Page turns or continuous vertical scrolling.
- Standard three-column tap zones, configurable 3×3 grid, directional swipes, and volume-key paging.
- Timed auto-advance page turns.
- Browse SAF-selected folders; read ZIP-contained `.txt` without extracting to disk.
- Custom background/text colors, brightness override, orientation lock, and keep-screen-on.

[Android features, usage, and builds →](FloNovel-android/README.en.md)

### Desktop

- One-pane or two-pane reading. Default navigation advances by half the visible content; the right pane moves left in two-pane mode.
- Remappable shortcuts, timed advance, and a one-pane turn animation.
- Watched home folder with preprocessing; recent/name/date/size sorting and low chapter-marker indicators.
- Font weight, maximum text width, pane gap/ratio, UI scale, and system/custom fonts.
- A 20-20-20 eye-rest reminder and MP3/AAC internet radio with a sleep timer.
- Open in the file manager/default app; send files to the trash or a chosen folder.

[Desktop features, shortcuts, and builds →](FloNovel-desktop/README.en.md)

## Getting started

1. Build an app using the instructions below.
2. Choose **Add folder** on Android or **Set home folder** on Desktop.
3. Open a `.txt` book. Android's default zones are left previous, center menu, right next. Desktop uses `,` / `.` to navigate and `F4` for settings.
4. Configure optional sync below to continue across devices.

> **Preprocessing changes actual files.** Originals are backed up under `.flonovel/original/` in the library before first processing. Processing changes indentation/blank lines, adds heading markers, and may rename files. Use a library copy if you need its original formatting.

## Installation and source builds

There are currently no published binaries on [GitHub Releases](https://github.com/katalog/FloNovel/releases). Both apps can be built from source; the repository includes an Android APK release workflow.

Use **JDK 17 or newer**. Android additionally needs **Android SDK Platform 36** and build tools. Configure the SDK in Android Studio or set `sdk.dir` in `local.properties`.

There is no root Gradle project. Run commands inside each app directory.

```bash
git clone https://github.com/katalog/FloNovel.git
cd FloNovel
```

Android:

```bash
cd FloNovel-android
./gradlew testDebugUnitTest assembleDebug
```

APK: `FloNovel-android/app/build/outputs/apk/debug/app-debug.apk`. Requires Android 7.0 (API 24) or newer.

Desktop, from the repository root in a separate terminal:

```bash
cd FloNovel-desktop
./gradlew test build
./gradlew run
```

On Windows, replace `./gradlew` with `.\gradlew.bat`. See the [Desktop guide](FloNovel-desktop/README.en.md#packaging) for installers. Builds need no sync configuration; without the corresponding keys, those sync features are unavailable.

## Setting up sync (optional)

### Book files: Dropbox

Both apps must use the **same Dropbox app key and Dropbox account**.

1. Create an **App folder** app in the Dropbox developer console.
2. Enable `account_info.read`, `files.metadata.read`, `files.metadata.write`, `files.content.read`, and `files.content.write`.
3. Register Desktop's redirect URI: `http://localhost:52475/oauth/callback`. Android derives its `db-<app key>://1/connect` scheme from the key.
4. Copy the [Android example](FloNovel-android/local.properties.example) / [Desktop example](FloNovel-desktop/local.properties.example) to `local.properties` in each app directory, fill in `DROPBOX_APP_KEY`, and build. Preserve Android SDK settings.
5. Link Dropbox in each app and explicitly start the first sync.

The shared library is **`/books` inside the Dropbox app folder**. Your PC library need not be in a folder managed by the Dropbox desktop client.

- Android syncs when the library comes to the foreground, at most once per minute, or manually.
- Desktop syncs on Dropbox notifications, window focus return at most once per minute, or manually.
- Simultaneous edits preserve the remote file and a local conflict copy.
- Remote deletions applied on PC move to the designated folder if configured, or fall back to the trash.
- Confirmation is required for 20 or more deletions, or at least five covering 30% of tracked files, or an empty remote library.
- Remote changes to the open book wait until it closes.
- Android ZIP entries are excluded from file sync.

Reconnect Dropbox for write permissions if Android was linked using an older download-only version.

### Reading positions: Supabase

Dropbox alone supports file sync. Position sharing also needs matching `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` in both builds.

The server must implement `flonovel_sync`, a shared-secret user trigger and RLS policies, and a max-wins rule preserving the furthest position. **Server SQL and deployment scripts are not included.** Adding a fresh project's URL and key does not complete setup. See [AGENTS.md §1](AGENTS.md#1-계약--하나라도-어기면-반대편-앱이-조용히-깨진다) for the required contract.

**Link Desktop first.** Desktop creates `/.flonovel/secret.json` in Dropbox; Android reads it. Normal uploads cannot move the remote position backward. Desktop's explicit force upload replaces an incorrect remote position with the current local one.

> **Development isolation:** Android debug and Desktop's `FLONOVEL_DEV=true` use `secret-dev.json` for a separate position partition. Book files still use the same `/books`. Use a separate Dropbox account/app when experimenting with file sync.

## Supported scope

- Desktop reads `.txt`; Android also reads `.txt` inside ZIP archives. EPUB/PDF/MOBI are unsupported.
- Content is loaded into memory. Large-file time and memory usage depend on the device and file.
- Local reading works offline. Sync, font downloads, and radio need a network.
- TTS requires an Android speech engine and voice data for the desired language.

## Development and contributing

```text
FloNovel-android/   Kotlin · Jetpack Compose · Room · DataStore
FloNovel-desktop/   Kotlin/JVM · Compose Desktop · JSON persistence
.github/workflows/  Android APK release workflow
```

The apps share no code. Contracts cover character offsets, path normalization, preprocessing, and sync.

- Read [AGENTS.md](AGENTS.md) before changes. Test new behavior.
- Update both preprocessors and parity fixtures together. Regenerate with Desktop's `./gradlew test -PupdateGolden`, then copy expectations to Android's `app/src/test/resources/fixtures/parity/`.
- Run Android's `testDebugUnitTest` and Desktop's `test`. Android instrumented tests need a device/emulator and are not the default gate.
- Count executed tests from result XML; `BUILD SUCCESSFUL` alone does not establish a passing suite.
- Update both README languages together. Write code/comments/commit messages in English.
- Bug reports should include app/OS versions, reproduction steps, and expected/actual results. Exclude private books, tokens, and shared keys.

## License

[Apache License 2.0](LICENSE). Downloaded fonts retain their respective licenses.
