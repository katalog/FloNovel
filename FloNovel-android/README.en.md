**English** · [한국어](README.md)

# FloNovel for Android

**Read and listen to text novels in your folders, and continue on desktop.**

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](../LICENSE)

[Overview](../README.en.md) · [Desktop app](../FloNovel-desktop/README.en.md) · [Issues](https://github.com/katalog/FloNovel/issues)

## Features

### Library and files

- Select folders through Storage Access Framework (SAF), browse subfolders with breadcrumbs.
- Read `.txt` and ZIP-contained `.txt` without extracting to disk.
- Sort by name/date/size in either direction, view progress, and get a startup resume prompt.
- Long-press files/folders to delete. Synced-book deletions propagate to other devices.
- Automatic UTF-8 and EUC-KR/CP949-family encoding detection.

### Reading and controls

- **Page turns** or **continuous vertical scrolling**, with none/slide/cover transitions.
- Standard three-column tap zones: left previous, center menu, right next.
- A configurable **3×3 grid** and directional swipe actions.
- Assign page/chapter/chapter-jump navigation, menu toggle, or no action; volume-key paging is available.
- Contents, pattern presets/custom regular expressions, full-text search, and position navigation by progress.
- Character-offset positions used as the basis for relayout after appearance changes.

Default horizontal swipes navigate **chapters**; vertical swipes navigate **chapter jump points**. Vertical swipe assignments apply in page mode; scroll mode uses vertical scrolling.

### Appearance and automatic advance

- Six themes: warm ivory, sepia cream, dark navy, soft gray, cool light, soft dark brown.
- Custom background/text colors, size, line height, letter spacing, horizontal/top/bottom margins.
- Downloads for Nanum Gothic, Nanum Myeongjo, Noto Sans KR, RIDIBatang, and Pretendard.
- Brightness override, automatic/portrait/landscape orientation, keep-screen-on.
- Auto-advance (timer mode) for fixed-interval page turns.
- Korean/English UI following the system language.

## Getting started

1. Install and choose **Add folder**. Preprocessing/sync also need write access.
2. Tap a book. Tap the center in the default layout to show the toolbar.
3. Adjust fonts, themes, margins, gestures, and automatic advance in settings.
4. For Dropbox, link from the library and tap **Sync now**.

### Text preprocessing

Local `.txt` files are cleaned before first opening/uploading. Originals are backed up under `.flonovel/original/` in the library first.

- Normalize line endings, remove leading whitespace/tabs, adjacent duplicate content lines, and excessive blank lines.
- Add `##` to recognized headings and add file start/end markers.
- Shorten filenames to 50 Unicode code points excluding the extension; remove Han characters from mixed Hangul/Han names.
- Write a hidden temporary file before replacement; interrupted processing is recovered during the next sync.
- Already processed books are not processed again; fully downloaded Dropbox copies are already preprocessed.

Output bytes are checked against Desktop's fixtures. Use a library copy to retain original formatting.

## Sync

See the [shared setup guide](../README.en.md#setting-up-sync-optional). File sync needs a Dropbox app key in the build.

- Share `/books` bidirectionally with Desktop on the same Dropbox app/account.
- Propagate additions, edits, deletions, moves, renames; exclude ZIP entries from file sync.
- Preserve simultaneous edits as the remote original and an Android conflict copy; edits win over deletions.
- Confirm mass deletion/empty remote libraries; download interrupted copies again instead of uploading them as edits.
- Sync on library foreground at most once per minute, or manually; no separate background job.
- Defer downloads/deletions/conflicts for the open book until it closes.
- Reset baselines/cursors when changing the home folder, then start the first sync again.

Positions additionally need Supabase configuration and a shared key. **Link Desktop first to create the key.** Android reads it rather than generating it. A more advanced position is offered as a jump.

Reconnect an older download-only Dropbox link to grant write permissions.

## Build and install

There are currently no published APKs on [GitHub Releases](https://github.com/katalog/FloNovel/releases).

Requirements:

- JDK 17 or newer to run Gradle.
- Android SDK Platform 36 and build tools, or Android Studio to configure them.
- Android 7.0 (API 24) or newer. `compileSdk` / `targetSdk` are 36; bytecode targets Java 11.

From the repository root:

```bash
cd FloNovel-android
./gradlew testDebugUnitTest assembleDebug
```

On Windows, use `.\gradlew.bat testDebugUnitTest assembleDebug`.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Install directly or use a connected device:

```bash
./gradlew installDebug
```

Debug ID: `com.moonkata.flonovel.android.dev`, allowing installation alongside release.

### Optional build configuration

Copy [local.properties.example](local.properties.example) to `local.properties`, retaining SDK settings (`sdk.dir`).

- `DROPBOX_APP_KEY`: the same Dropbox app as Desktop.
- `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`: the shared position server.
- `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`: release signing.

Same-name environment variables are supported. Sync values are optional.

```bash
./gradlew assembleRelease
```

Release APK: `app/build/outputs/apk/release/app-release.apk`. **Without a keystore path, builds succeed with debug signing**, so verify the distribution certificate.

An `android-v*` tag triggers the [release workflow](../.github/workflows/android-release.yml). After unit tests, it builds/publishes the APK using the tag for version name and CI run number for version code. Distribution needs repository signing/sync configuration.

> Debug uses `secret-dev.json` for separate positions, but Dropbox `/books` is shared with release. Development file sync can affect real book files.

## Tests and structure

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

- `app/src/test`: JVM tests for encoding, chapters/pagination, preprocessing parity, two-way sync, and related logic.
- `app/src/androidTest`: Compose UI, Room, DataStore, Android checks. A device/emulator is required; some font tests use the network.
- Result XML: `app/build/test-results/testDebugUnitTest/`. Check executed counts and failures.

Sources are under `app/src/main/java/com/moonkata/flonovel/android/`. `ui/` contains screens/ViewModels; `data/` handles files/database/settings/preprocessing/sync; `tts/` handles automatic advance.

Stack: Kotlin · Jetpack Compose/Material 3 · Room · DataStore · Navigation Compose · SAF · juniversalchardet. See [AGENTS.md](../AGENTS.md) for contribution rules.

## Troubleshooting and scope

- **Empty contents:** add a preset/regular expression matching headings. Detection is pattern-based.
- **Folder access/write errors:** choose the folder again for SAF permissions.
- **Missing key:** link Desktop to the same Dropbox app/account first.
- EPUB/PDF/MOBI are unsupported. Content is loaded into memory, so large-book memory usage varies by device.
- The DataStore file containing Dropbox tokens/shared key is excluded from Android cloud backup.

## License

[Apache License 2.0](../LICENSE). Fonts retain their respective licenses.
