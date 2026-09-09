<div align="center">

# FloNovel for Android

**Read your own `.txt` novels on your phone — correct encodings, real chapters, read-aloud, and your
place kept in sync with your desktop.**

![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

[Download](#install) · [Getting started](#getting-started) · [Settings](#settings-reference) · [FAQ](#faq)

</div>

---

The Android half of **[FloNovel](../README.md)**. Point it at a folder of text files — or let the
desktop app fill one for you over Dropbox — and read.

<!-- Screenshots go here. Recommended: library with breadcrumbs, the reader mid-page, the
     quick-settings sheet, and the table of contents. -->

---

## Features

### 📖 Handles books that break other readers

Ten-million-character novels open instantly. Jump to any chapter, search the whole book, or drag
straight to 73% without a loading screen.

### 🔤 No encoding menu, ever

UTF-8, EUC-KR, and CP949 are detected automatically — including the extended Korean characters that
usually come out as boxes. It just opens correctly.

### 🗂️ Reads your folders as they are

Pick a folder once and browse it with breadcrumbs, subfolders and all. `.zip` archives open like
folders, and the text files inside them are read **without unpacking anything** to your storage.
Sort by name, date, or size, and see how far into each book you are.

### 👆 Page turns that work the way you want

Two reading modes — page flip or continuous scroll — and six gestures you assign individually:
tap left, tap right, and swipe in each of the four directions. Any of them can turn a page, jump a
chapter, or do nothing at all. Prefer buttons? Turn on volume-key paging.

### 🔊 Read aloud

Hands-free narration through your phone's text-to-speech voice, with adjustable speed and pitch. The
page turns when the sentence actually finishes, not on a guess. There's a plain timer mode too, if you
just want pages to advance while you eat.

### 📑 Chapters on unstructured files

Chapter headings are detected fresh every time you open a book, so the table of contents works even on
files that were never formatted for it — and it scrolls straight to where you are. Long chapters can
be subdivided so a 40-minute chapter is still navigable. Add your own patterns if your books use a
different style.

### 🎨 Comfortable for long sessions

Light, dark, and sepia themes, or set your own background and text colors. Adjust font size, line
height, letter spacing, and each margin independently. Download extra fonts from inside the app.
Keep the screen on, override system brightness, and lock the orientation.

### ☁️ Picks up where your PC left off

Your desktop library arrives over Dropbox automatically. When the desktop is further along in a book,
the reader offers to jump there — showing both positions — instead of moving you without asking.

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
Tap **Add folder** and pick the folder holding your `.txt` files. Android will ask you to grant access
— the grant is remembered, so you only do this once.

**2. Open a book.**
Tap a title. Tap the middle of the screen any time to bring the toolbar back.

**3. Make it yours.**
The settings sheet (⚙️ in the reader) covers font, theme, margins, gestures, and read-aloud. Nothing
is buried more than one tap deep.

**4. Connect Dropbox** *(optional).*
In the library, open the Dropbox sheet and sign in with the same account your desktop app uses. Tap
**Sync now** and your books download.

---

## Settings reference

<details>
<summary><b>Font</b></summary>

Size, line height, letter spacing, and a font picker with downloadable fonts. Applying a font
re-lays-out the book in place — you don't lose your position.
</details>

<details>
<summary><b>Margins</b></summary>

Left/right, top, and bottom set independently.
</details>

<details>
<summary><b>Theme</b></summary>

Light, dark, sepia, or fully custom background and text colors.
</details>

<details>
<summary><b>Page-turn mode</b></summary>

**Page turn** (horizontal flip) or **Scroll** (continuous vertical). Transition animation: none,
slide, or cover.
</details>

<details>
<summary><b>Page-turn options</b></summary>

Six gestures — touch left, touch right, swipe left, swipe right, swipe up, swipe down — each assigned
one of: previous page, next page, previous chapter jump, next chapter jump, no action.

Swipe up/down only apply in page-turn mode; in scroll mode a vertical drag scrolls.
</details>

<details>
<summary><b>Chapter jump</b></summary>

How many parts to divide a long chapter into, plus the pattern list used to detect chapter headings.
The built-in `##` preset matches what the desktop app writes; add your own regular expressions for
anything else.
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

Turns on once Dropbox is connected — the shared key comes from your Dropbox app folder, so there's
nothing to type.
</details>

---

## About Dropbox sync

Books sync **one way**: your PC uploads, your phone downloads. The phone never uploads and never
deletes anything remotely.

That means **deleting a book on your phone brings it back on the next sync.** It looks like a bug and
isn't — delete it on the PC instead, and the phone will follow. The app says so on the sync screen for
this exact reason.

If a sync ever finds your remote library completely empty, it **keeps your books instead of deleting
them** and tells you how many it held onto. That almost always means the wrong Dropbox account is
connected, or the desktop app hasn't run yet.

Setting up sync from scratch is covered in the [main README](../README.md#setting-up-sync-optional).

---

## FAQ

<details>
<summary><b>Does it read EPUB, PDF, or MOBI?</b></summary>

No. FloNovel reads `.txt` files, and `.txt` files inside `.zip` archives. That's the whole format
list, on purpose.
</details>

<details>
<summary><b>My table of contents is empty.</b></summary>

Chapter headings are matched by pattern. Books processed by the desktop app carry `##` markers and
work out of the box; other files may need a custom pattern, which you can add under
**Chapter detection patterns** in settings.
</details>

<details>
<summary><b>Text-to-speech doesn't work.</b></summary>

FloNovel uses your phone's own TTS engine, so the voice for your language has to be installed. Check
Android's **Settings → Accessibility → Text-to-speech output**.
</details>

<details>
<summary><b>"Can no longer access your home folder."</b></summary>

Android revoked the folder permission — usually because the folder moved, the SD card was remounted,
or the app was reinstalled. Pick the folder again and your books and reading positions come back.
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
`DROPBOX_APP_KEY`, `SUPABASE_URL`, and `SUPABASE_PUBLISHABLE_KEY`. **All of them are optional** — the
build succeeds without any credentials and the affected features simply report themselves as
unconfigured.

Release signing reads `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and
`RELEASE_KEY_PASSWORD` from `local.properties` or environment variables. If the keystore path is
missing, `assembleRelease` **silently falls back to debug signing and still succeeds** — check the
certificate on the artifact, not the build result.

### Tests

| Suite | What it covers | Needs |
|---|---|---|
| `app/src/test` | pure logic — encoding, chapters, pagination math, the sync protocol | nothing — runs on the JVM |
| `app/src/androidTest` | the real Compose UI, Room, and DataStore | a device or emulator (two tests download fonts) |

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
