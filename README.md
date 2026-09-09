<div align="center">

# FloNovel

**A reader for your own `.txt` novels — on your phone and on your desk, always on the same page.**

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Windows · macOS · Linux](https://img.shields.io/badge/Desktop-Windows%20%C2%B7%20macOS%20%C2%B7%20Linux-0078D6)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

[Download](#install) · [Getting started](#getting-started) · [Sync setup](#setting-up-sync-optional) · [FAQ](#faq)

</div>

---

FloNovel is a pair of reading apps for plain-text novels — the kind that arrive as a single 40 MB
`.txt` file with no chapters, no metadata, and an encoding nobody bothered to write down.

The **desktop app** takes care of your library: it watches a folder, cleans up every text file that
lands there, and keeps a copy in the cloud. The **Android app** picks that library up on your phone.
Whichever one you last read on, the other one knows where you stopped.

No accounts. No subscription. No servers of ours — the only login is your own Dropbox, and your books
never leave it.

<!-- Screenshots go here. Recommended: the desktop 2-pane reader, the Android reader, and one library
     view side by side. -->

---

## Two apps, one library

| | 📱 [**Android**](FloNovel-android/) | 🖥️ [**Desktop**](FloNovel-desktop/) |
|---|---|---|
| Reading | Page flip or continuous scroll | One-page or two-page spread |
| Controls | Tap zones, swipes, volume keys | Keyboard, fully remappable |
| Files | Downloads your library | Owns and organizes your library |
| Extras | Read-aloud (TTS), auto-advance | Focus mode, search, chapter list |
| Runs on | Android 7.0 and newer | Windows, macOS, Linux |

Each app has its own guide: **[Android →](FloNovel-android/README.md)** · **[Desktop →](FloNovel-desktop/README.md)**

---

## Features

### 📖 Built for very long books

Web novels routinely run past ten million characters. FloNovel opens them instantly, jumps anywhere
in the text without loading screens, and never asks you to split a file.

### 🔤 It reads Korean text correctly

UTF-8, EUC-KR, CP949 — the encoding is detected automatically, including the extended characters that
make most readers show boxes and question marks. You never pick an encoding from a menu.

### 🔖 Your place survives everything

FloNovel remembers where you are in the *text*, not on page 37. Change the font, resize the window,
switch to two-page mode, move to your phone — you land on the same sentence every time.

### ☁️ Sync without an account

Sign into the same Dropbox on both devices and you're done. There's no pairing code to scan, no
profile to create, and no FloNovel server in the middle. Books flow from your PC to your phone; your
reading position flows both ways.

### 🧹 Automatic cleanup

The desktop app tidies up files as they arrive: fixes line endings, removes duplicated lines and
runaway blank space, marks chapter headings so both apps can build a table of contents, and shortens
unwieldy filenames. Your originals are backed up first, untouched.

### 📚 Chapters, even when the file has none

Chapter headings are found in the text every time you open a book, so a table of contents and
chapter-to-chapter navigation work on files that were never structured for it. You can add your own
patterns if your books use a different convention.

### 🎨 Comfortable to actually read in

Light, dark, and sepia themes. Adjustable font, size, line height, letter spacing, and margins.
Downloadable fonts built in. On Android: brightness override, orientation lock, keep-screen-on, and
read-aloud with the system voice.

---

## Install

### 📱 Android

Grab the latest `.apk` from the **[Releases page](../../releases)** and install it.

Requires **Android 7.0 (API 24) or newer**. You'll need to allow installation from your browser or
file manager the first time — this is a sideloaded app, not a Play Store listing.

> **Tip:** if you use [Obtainium](https://github.com/ImranR98/Obtainium), point it at this
> repository and it will track new releases for you.

### 🖥️ Desktop

Packaged installers aren't published yet — for now the desktop app is built from source, which takes
one command:

```bash
cd FloNovel-desktop
./gradlew packageDistributionForCurrentOS
```

That produces an `.msi` on Windows, a `.dmg` on macOS, or a `.deb` on Linux. Requires **JDK 17 or
newer**. On Windows, `flonovel-desktop-build-exe.bat` also builds a portable version that runs
without installing.

Just want to try it? `./gradlew run` launches the app directly.

---

## Getting started

**1. Point the desktop app at your books.**
Open FloNovel and choose a home folder — wherever your `.txt` files live. It scans the folder, cleans
up what it finds, and lists everything. New files dropped into that folder from then on are picked up
automatically.

**2. Read.**
Click a book. `.` and `,` turn pages, `PgUp`/`PgDn` move by chapter, `F3` opens the table of contents,
`F2` searches, `F11` hides everything but the text. Every key is remappable in settings.

**3. Add your phone** *(optional — see below).*
Connect Dropbox on both devices and your library follows you.

---

## Setting up sync (optional)

Everything above works offline and forever without this section. Sync is opt-in, and it's built so
that **you own every piece of it** — which does mean a one-time setup.

FloNovel uses two services, both on your own accounts:

| | What it carries | Why |
|---|---|---|
| **Dropbox** | Your book files, and one shared key | The app only ever sees its own app folder, never the rest of your Dropbox |
| **Supabase** | Your reading position (a number per book) | Small, frequent updates that Dropbox is a poor fit for |

### What you'll need

1. **A Dropbox app** — create one at [dropbox.com/developers/apps](https://www.dropbox.com/developers/apps)
   with **App folder** access and the scopes `files.metadata.read/write`, `files.content.read/write`,
   `account_info.read`. Add `http://localhost:52475/oauth/callback` as a redirect URI for the desktop
   app. Copy the app key.
2. **A Supabase project** — free tier is plenty. It needs one table, `flonovel_sync`, holding a row
   per book (`relative_path`, `char_offset`, `source`, `encoding`), with a trigger that derives the
   owner from the request's secret header and keeps the highest reading position, plus a row-level
   security policy so each secret only sees its own rows. Copy the project URL and the publishable
   key.
3. **Put both into `local.properties`** in each app folder — copy the `local.properties.example`
   sitting next to it and fill in the blanks.

### Then, on each device

Connect Dropbox. That's the whole pairing step: the desktop app generates a random key, stores it in
your Dropbox app folder, and the phone finds it there on its next sync. Nothing is typed, scanned, or
sent anywhere else.

**Note:** book files sync **one way**, PC → Dropbox → phone. Deleting a book on your phone brings it
back on the next sync; delete it on the PC instead. Reading position syncs **both ways**, and when one
device is further along the other offers to catch up rather than jumping on its own.

---

## FAQ

<details>
<summary><b>Does it read EPUB or PDF?</b></summary>

No — FloNovel is deliberately a plain-text reader. `.txt` files, and `.txt` files inside `.zip`
archives on Android.
</details>

<details>
<summary><b>Will it modify my original files?</b></summary>

Yes, the desktop app rewrites text files in your home folder to normalize them, and may shorten very
long filenames. **Every original is backed up first** into `.flonovel/original/` inside that folder,
and each file is only ever processed once. The rewrite is atomic, so an interruption leaves the
original intact.
</details>

<details>
<summary><b>Do I have to set up Dropbox and Supabase?</b></summary>

No. Both apps are fully usable with no configuration at all — sync simply reports itself as
unconfigured. Set up only Dropbox if you want your books on your phone but don't care about position
sync.
</details>

<details>
<summary><b>Can FloNovel see my books or my reading history?</b></summary>

There is no FloNovel server. Files sit in your Dropbox app folder; reading positions sit in your
Supabase project. Both are accounts you create and control.
</details>

<details>
<summary><b>Why is my reading position a percentage with three decimals?</b></summary>

Because these books are enormous. In a ten-million-character novel, one percent is a hundred thousand
characters — roughly an hour of reading.
</details>

<details>
<summary><b>Nothing appears in my table of contents.</b></summary>

Chapter headings are detected by pattern. Files processed by the desktop app get `##` markers
automatically; files added some other way may not match anything. Both apps let you add your own
patterns in settings.
</details>

---

## Under the hood

Both apps are written in **Kotlin** with **Jetpack Compose** — Compose for Android on the phone,
Compose Desktop on the JVM. The Android app stores its library in **Room** and its settings in
**DataStore**; the desktop app uses atomically written JSON files in your OS config directory. There's
no dependency-injection framework, no ORM beyond Room, and no shared code between the two apps — just
a documented contract that both sides enforce with tests.

Around **360 unit tests** run on the JVM, plus **131 instrumented tests** on a real Android device.

---

## Building from source

There's no Gradle project at the repository root; each app builds independently.

```bash
# Android — needs JDK 17+ to run Gradle
cd FloNovel-android
./gradlew testDebugUnitTest    # 121 tests
./gradlew assembleDebug

# Desktop — needs JDK 17+
cd FloNovel-desktop
./gradlew test                 # 238 tests
./gradlew run
```

Neither build requires any credentials. Missing keys leave the build working and disable the matching
sync feature at runtime.

---

## Contributing

Issues and pull requests are welcome. A few things worth knowing before you start:

- The two apps deliberately **share no code**. They interoperate through a contract covering reading
  position, path normalization, and the sync protocol — [CLAUDE.md](CLAUDE.md) spells it out, and
  breaking one side silently breaks the other.
- New behavior comes with tests. `BUILD SUCCESSFUL` is not a pass; Gradle prints it after running zero
  tests too.
- Code, comments, and commit messages are in English.

---

## License

[Apache License 2.0](LICENSE).
