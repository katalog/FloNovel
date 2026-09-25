<div align="center">

# FloNovel for Android

**Read your own `.txt` novels on your phone: correct encodings, real chapters, read-aloud, and one
library shared with your desktop.**

![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

**English** · [한국어](README.md)

[Download](#install) · [Getting started](#getting-started) · [Settings](#settings-reference) · [Sync](#about-dropbox-sync) · [FAQ](#faq)

</div>

---

The Android half of **[FloNovel](../README.en.md)**. Point it at a folder of text files, or let Dropbox
fill one from your desktop, and read.

<!-- Screenshots go here. Recommended: library with breadcrumbs, the reader mid-page, the
     quick-settings sheet, and the table of contents. -->

---

## Features

### 📖 Handles books that break other readers

Ten-million-character novels open without being split. Jump to any chapter, search the whole book, or
drag straight to 73% without a loading screen.

### 🔤 No encoding menu, ever

UTF-8, EUC-KR and CP949 are detected automatically, including the extended Korean characters that
usually come out as boxes. It just opens correctly.

### 🧹 Books cleaned up before you read them

A book you add on the phone is tidied up before it's first opened: line endings unified, duplicated
lines and runaway blank space removed, chapter headings marked, overlong filenames shortened. It's
the **exact same cleanup** the desktop app does, byte for byte, so a book looks identical wherever it
was added. The original is backed up to `.flonovel/original/` first, and books that arrive from
Dropbox are already clean, so they open straight away.

### 🗂️ Reads your folders as they are

Pick a folder once and browse it with breadcrumbs, subfolders and all. `.zip` archives open like
folders, and the text files inside are read **without unpacking anything** to your storage. Sort by
name, date or size in either direction, see how far into each book you are, and long-press a book or
folder to delete it. On launch, FloNovel offers to take you back to the book you were reading.

### 👆 Page turns that work the way you want

Two reading modes, **page flip** or **continuous scroll**, with none, slide or cover transitions.

- **Tap zones:** the standard left / middle / right layout, or a **3×3 grid** where each of the nine
  cells gets its own action.
- **Swipes:** left, right, up and down, each assigned separately.
- **Volume keys:** turn pages without touching the screen.

Any tap or swipe can go to the previous or next page, chapter, or chapter jump point, open the
menu, or do nothing at all.

### 📑 Chapters on unstructured files

Chapter headings are detected fresh every time you open a book, so the table of contents works even on
files that were never formatted for it, and it scrolls straight to where you are. Long chapters can
be split into evenly spaced jump points so a 40-minute chapter is still navigable. Add your own
patterns if your books use a different style.

### 🔊 Read aloud

Hands-free narration through your phone's text-to-speech voice, with adjustable speed and pitch. The
page turns when the sentence actually finishes, not on a guess. There's a plain timer mode too, if you
just want pages to advance while you eat.

### 🎨 Comfortable for long sessions

Six curated themes (Warm Ivory, Sepia Cream, Dark Navy, Soft Gray, Cool Light, Soft Dark Brown), or
your own background and text colors. Adjust font size, line height, letter spacing and each margin
independently, and download Korean reading fonts (Pretendard, Noto Sans KR, Nanum Gothic, Nanum
Myeongjo, RIDIBatang) from inside the app. Keep the screen on, override the system brightness, and
lock the orientation. The interface is in **English and Korean**, following your system language.

### ☁️ One library with your PC

Connect Dropbox and your library syncs **both ways** with the desktop app: books added, changed,
renamed or deleted on either side reach the other. Syncing happens whenever you come back to the
library, with no background job draining your battery. When the desktop has read further in a book,
the reader offers to jump there, showing both positions, instead of moving you without asking.

---

## Install

Download the latest `.apk` from the **[Releases page](../../../releases)** and open it.

- Requires **Android 7.0 (API 24) or newer**
- Your phone will ask you to allow installs from your browser or file manager the first time
- No Play Store listing, no telemetry, no ads

> **Tip:** [Obtainium](https://github.com/ImranR98/Obtainium) can watch this repository and notify
> you when a new version ships.

---

## Getting started

**1. Add a folder.**
Tap **Add folder** and pick the folder holding your `.txt` files. Android asks you to grant access.
The grant is remembered, so you only do this once.

**2. Open a book.**
Tap a title. Tap the right side of the page to go forward, the left to go back, and the middle to
bring up the toolbar with the table of contents, search and settings.

**3. Make it yours.**
The settings sheet (⚙️ in the reader) covers font, theme, margins, gestures and read-aloud. Nothing
is buried more than one tap deep.

**4. Connect Dropbox** *(optional).*
In the library, open the Dropbox sheet and sign in with the same account your desktop app uses. Tap
**Sync now** and your library arrives. From then on it syncs each time you return to the library.

---

## Settings reference

<details>
<summary><b>Font</b></summary>

Size, line height, letter spacing, and a font picker with downloadable fonts. Applying a font
re-lays-out the book in place; you don't lose your position.
</details>

<details>
<summary><b>Margins</b></summary>

Left/right, top and bottom set independently.
</details>

<details>
<summary><b>Theme</b></summary>

Six curated themes (Warm Ivory, Sepia Cream, Dark Navy, Soft Gray, Cool Light, Soft Dark Brown), or
fully custom background and text colors.
</details>

<details>
<summary><b>Page-turn mode</b></summary>

**Page turn** (horizontal flip) or **Scroll** (continuous vertical). Transition animation: none,
slide or cover.
</details>

<details>
<summary><b>Touch &amp; swipe gestures</b></summary>

**Touch zones:** *Standard 3-column* (left: previous page, middle: menu, right: next page), or a
*3×3 grid* where you tap each cell to set its action.

**Swipes:** left, right, up and down, each set separately. By default, horizontal swipes move a whole
chapter and vertical swipes move between chapter jump points.

**Actions available:** previous/next page, previous/next chapter, previous/next chapter jump point,
toggle menu, no action.

Swipe up/down only apply in page-turn mode; in scroll mode a vertical drag scrolls.
</details>

<details>
<summary><b>Chapter jump</b></summary>

How many jump points to split each chapter into, plus the pattern list used to detect chapter
headings. The built-in `##` preset matches what the preprocessor writes; add your own regular
expressions for anything else.
</details>

<details>
<summary><b>Screen</b></summary>

Keep screen on, page with volume keys, manual brightness override, and orientation lock
(auto / portrait / landscape).
</details>

<details>
<summary><b>Auto-advance / TTS</b></summary>

Off, **Timer** (turn the page every N seconds), or **TTS** (read aloud and turn when the passage
ends). Speech rate and pitch are adjustable.
</details>

<details>
<summary><b>Reading-position sync</b></summary>

Turns on once Dropbox is connected. The shared key comes from your Dropbox app folder, so there's
nothing to type.
</details>

---

## About Dropbox sync

Books sync **both ways**. Your Dropbox app folder is the shared library, and the phone both uploads
and downloads.

- **Add a book on the phone** and it's cleaned up, then uploaded, and appears on your PC.
- **Delete a book on the phone** (long-press it) and it's deleted from Dropbox and the PC too. The PC
  keeps it in its Recycle Bin, and the confirmation tells you so before anything happens.
- **Changed on both devices?** Both versions are kept. The Dropbox one keeps the original name; the
  phone's is saved as `Book (conflicted copy - Android - 2026-09-23).txt`.
- **Renamed or moved on the PC?** The phone moves its copy, and your reading position follows it.
- **The book you have open is left alone.** Changes to it are applied once you close it.
- **Mass deletions ask first.** If a sync would delete an unusual number of books, or finds your
  remote library empty, it stops and asks. That almost always means a different Dropbox account is
  connected.
- **Interrupted downloads are safe.** A half-downloaded book is fetched again rather than mistaken for
  an edit and uploaded.

Sync runs when the library comes to the front (opening the app, coming back from the reader or
another app), at most once a minute, and whenever you tap **Sync now**. There's no background sync.

Only `.txt` files are synced; books inside `.zip` archives stay on the phone.

> **Connected before the phone could upload?** Links made with an older, download-only version have
> no upload permission. The Dropbox sheet says so and offers **Reconnect Dropbox**; do it once and the
> phone's changes start reaching the PC.

Setting up sync from scratch is covered in the [main README](../README.en.md#setting-up-sync-optional).

---

## FAQ

<details>
<summary><b>Does it read EPUB, PDF or MOBI?</b></summary>

No. FloNovel reads `.txt` files, and `.txt` files inside `.zip` archives. That's the whole format
list, on purpose.
</details>

<details>
<summary><b>Why did my book's file name change?</b></summary>

The cleanup shortens names longer than 50 characters and removes Chinese characters from names that
also contain Korean, the same way the desktop app does. The original file, with its original name,
is in `.flonovel/original/` inside your library folder.
</details>

<details>
<summary><b>My table of contents is empty.</b></summary>

Chapter headings are matched by pattern. Cleaned-up books carry `##` markers and work out of the box,
but an unusual heading style may not be recognized. Add a custom pattern under **Chapter detection
patterns** in settings.
</details>

<details>
<summary><b>Text-to-speech doesn't work.</b></summary>

FloNovel uses your phone's own TTS engine, so the voice for your language has to be installed. Check
Android's **Settings → Accessibility → Text-to-speech output**.
</details>

<details>
<summary><b>"Can no longer access your home folder."</b></summary>

Android revoked the folder permission, usually because the folder moved, the SD card was remounted,
or the app was reinstalled. Pick the folder again and your books and reading positions come back.
</details>

<details>
<summary><b>Sync says the folder has no write access.</b></summary>

The folder was chosen before FloNovel asked for write access. Choose your home folder again to fix
syncing. Reading is unaffected either way.
</details>

<details>
<summary><b>Is my data backed up to Google?</b></summary>

Not your credentials. The file holding your Dropbox token and sync key is explicitly excluded from
Android's cloud backup.
</details>

<details>
<summary><b>Can I install this next to a version I'm developing?</b></summary>

Yes. Debug builds install under a separate ID and use a separate sync partition, so they can't touch
your real reading positions.
</details>

---

## Building from source

Requires **JDK 17+** to run Gradle. The app targets Java 11 bytecode, `minSdk` 24, `compileSdk` 36.

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleRelease      # release APK
```

For sync features, copy `local.properties.example` to `local.properties` and fill in
`DROPBOX_APP_KEY`, `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`. **All of them are optional**: the
build succeeds without any credentials and the affected features simply report themselves as
unconfigured.

Release signing reads `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD` from `local.properties` or environment variables. If the keystore path is
missing, `assembleRelease` **silently falls back to debug signing and still succeeds**, so check the
certificate on the artifact, not the build result.

Pushing an `android-v*` tag runs the unit tests in GitHub Actions and, if they pass, publishes a
signed APK as a GitHub Release.

### Tests

| Suite | What it covers | Needs |
|---|---|---|
| `app/src/test` | Pure logic: encoding, chapters, pagination math, the preprocessor, the two-way sync engine (against an in-memory library) | Nothing; runs on the JVM |
| `app/src/androidTest` | The real Compose UI, Room and DataStore | A device or emulator (two tests download fonts) |

The preprocessor is checked byte for byte against the same fixtures and expected output as the
desktop app. Its port avoids `\d`, `\s`, `.` and case-insensitive matching, because Android's ICU
regex engine gives those broader meanings than the JVM does.

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

### Built with

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · Navigation Compose · juniversalchardet ·
Storage Access Framework. No dependency-injection framework.

---

## License

[Apache License 2.0](../LICENSE).
