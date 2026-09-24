<div align="center">

# FloNovel

**A reader for your own `.txt` novels, on your phone and on your desk, always on the same page.**

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Windows · macOS · Linux](https://img.shields.io/badge/Desktop-Windows%20%C2%B7%20macOS%20%C2%B7%20Linux-0078D6)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

[Install](#install) · [Getting started](#getting-started) · [Sync setup](#setting-up-sync-optional) · [FAQ](#faq) · [Contributing](#contributing)

</div>

---

FloNovel is a pair of reading apps for plain-text novels: the kind that arrive as a single 40 MB
`.txt` file with no chapters, no metadata, and an encoding nobody bothered to write down.

Both apps clean up every book they're given, find its chapters, and remember your place down to the
character. Connect them to your own Dropbox and they share one library: add, edit, rename or delete a
book on either device and the other follows. Whichever one you last read on, the other knows where
you stopped.

No FloNovel account. No subscription. No servers of ours. The only sign-in is your own Dropbox, and
your books never leave it.

<!-- Screenshots go here. Recommended: the desktop 2-pane reader, the Android reader, and one library
     view side by side. -->

---

## Two apps, one library

| | 📱 [**Android**](FloNovel-android/) | 🖥️ [**Desktop**](FloNovel-desktop/) |
|---|---|---|
| Reading | Page flip or continuous scroll | One-page or two-page spread |
| Controls | Tap zones (3-column or 3×3 grid), swipes, volume keys | Keyboard, every key remappable |
| Library | Your folders, `.zip` archives read in place | Watched home folder, cleaned up on arrival |
| Hands-free | Read-aloud (TTS), timed page turns | Timed page turns, background radio with sleep timer |
| Extras | Brightness, orientation lock, keep-screen-on | Focus-friendly layout, 20-20-20 eye-rest reminder |
| Runs on | Android 7.0 and newer | Windows, macOS, Linux |

Each app has its own guide: **[Android →](FloNovel-android/README.md)** · **[Desktop →](FloNovel-desktop/README.md)**

---

## Features

### 📖 Built for very long books

Web novels routinely run past ten million characters. FloNovel opens them without splitting them,
jumps anywhere in the text without a loading screen, and searches the whole book at once.

### 🔤 It reads Korean text correctly

UTF-8, EUC-KR and CP949 are detected automatically, including the extended characters that make most
readers show boxes and question marks. There is no encoding menu, because you should never need one.

### 🔖 Your place survives everything

FloNovel remembers where you are in the *text*, not "page 37". Change the font, resize the window,
switch to two-page mode, pick up your phone: you land on the same sentence every time.

### 🧹 Books cleaned up on arrival

Whichever device a book lands on first tidies it up: unified line endings, duplicated lines and
runaway blank space removed, chapter headings marked, unwieldy filenames shortened. Both apps apply
**byte-for-byte the same cleanup**, verified against shared test fixtures, so a book never looks
different depending on where it was added. Your original is backed up first, untouched.

### 📚 Chapters, even when the file has none

Chapter headings are found every time you open a book, so a table of contents and chapter-to-chapter
navigation work on files that were never structured for them. Long chapters can be split into
evenly spaced jump points, and you can add your own heading patterns.

### ☁️ Two-way sync without an account

Sign into the same Dropbox on both devices and you're done. No pairing code, no profile, no FloNovel
server in the middle.

- **Books** sync both ways. Additions, edits, renames, moves and deletions travel in either
  direction.
- **Nothing is silently overwritten.** If the same book changed on both devices, both versions are
  kept, and yours is saved alongside as a clearly named conflicted copy.
- **Mass deletions stop and ask.** If a sync would remove an unusual number of books (the classic
  sign of a wrong Dropbox account), FloNovel asks before touching anything.
- **Reading position** syncs both ways, and when the other device is further along, you're offered
  the jump instead of being moved without asking.

### 🎨 Comfortable to actually read in

Six curated themes, from Warm Ivory to Dark Navy. Adjustable font, size, line height, letter spacing
and margins, plus a catalog of downloadable Korean and Latin fonts. The interface is available in
**English and Korean**.

---

## Install

### 📱 Android

Grab the latest `.apk` from the **[Releases page](../../releases)** and install it.

Requires **Android 7.0 (API 24) or newer**. Your phone will ask you to allow installs from your
browser or file manager the first time: this is a sideloaded app, not a Play Store listing.

> **Tip:** [Obtainium](https://github.com/ImranR98/Obtainium) can watch this repository and install
> new releases for you.

### 🖥️ Desktop

Packaged installers aren't published yet. Building one takes a single command and **JDK 17 or
newer**:

```bash
cd FloNovel-desktop
./gradlew packageDistributionForCurrentOS
```

That produces an `.msi` on Windows, a `.dmg` on macOS, or a `.deb` on Linux. On Windows,
`./gradlew packageExe` builds an `.exe` installer and `./gradlew createDistributable` a portable
build that runs without installing.

Just want to try it? `./gradlew run` launches the app directly.

---

## Getting started

**1. Point an app at your books.**
On the desktop, choose a home folder: wherever your `.txt` files live. On Android, tap **Add
folder**. FloNovel scans it, cleans up what it finds, and lists everything.

**2. Read.**
On the desktop, `.` and `,` turn pages, `PgUp`/`PgDn` jump through a chapter, `F3` opens the table of
contents and `F2` searches. On the phone, tap the right side of the screen to go forward and the
middle to bring up the toolbar.

**3. Connect Dropbox on both** *(optional; see below).*
Your library and your place follow you from then on.

---

## Setting up sync (optional)

Everything above works offline and indefinitely without this section. Sync is opt-in, and it's built
so that **you own every piece of it**, which does mean a one-time setup.

FloNovel uses two services, both on your own accounts:

| | What it carries | Why |
|---|---|---|
| **Dropbox** | Your book files, and one shared key | The app only ever sees its own app folder, never the rest of your Dropbox |
| **Supabase** | Your reading position (a number per book) | Small, frequent updates that Dropbox is a poor fit for |

### What you'll need

1. **A Dropbox app.** Create one at [dropbox.com/developers/apps](https://www.dropbox.com/developers/apps)
   with **App folder** access and the scopes `files.metadata.read/write`, `files.content.read/write`
   and `account_info.read`. Add `http://localhost:52475/oauth/callback` as a redirect URI for the
   desktop app. Copy the app key.
2. **A Supabase project.** The free tier is plenty. It needs one table, `flonovel_sync`, holding a
   row per book (`relative_path`, `char_offset`, `source`, `encoding`), with a trigger that derives
   the owner from the request's secret header and keeps the highest reading position, plus a
   row-level security policy so each secret only sees its own rows. Copy the project URL and the
   publishable key.
3. **Put both into `local.properties`** in each app folder: copy the `local.properties.example`
   sitting next to it and fill in the blanks.

### Then, on each device

Connect Dropbox. That's the whole pairing step: the desktop app generates a random key, stores it in
your Dropbox app folder, and the phone finds it there. Nothing is typed, scanned, or sent anywhere
else.

> **Upgrading from a one-way version?** Earlier Android builds only downloaded, so their Dropbox link
> carries no upload permission. The app tells you when this is the case; reconnect Dropbox once on
> the phone and its changes start reaching the PC. Update the desktop app first: an older desktop
> build deletes books that exist only in Dropbox, which is exactly where the phone puts new ones.

### When things sync

- **Desktop:** immediately when Dropbox reports a change, whenever you return to the window (at most
  once a minute), and on demand.
- **Android:** whenever the library comes to the front (launching the app, returning from the reader
  or another app), at most once a minute, and on demand. There is no background sync draining your
  battery.

A book you currently have open is never swapped out from under you. Changes to it wait until you
close it.

---

## FAQ

<details>
<summary><b>Does it read EPUB or PDF?</b></summary>

No. FloNovel is deliberately a plain-text reader: `.txt` files, plus `.txt` files inside `.zip`
archives on Android (read in place, not synced).
</details>

<details>
<summary><b>Will it modify my original files?</b></summary>

Yes. Both apps rewrite each new text file once to normalize it, and may shorten a very long filename.
**Every original is backed up first** into `.flonovel/original/` inside your library folder, and a
book that has already been processed is never touched again. An interrupted rewrite never leaves you
with a half-written book: the desktop replaces the file atomically, and Android finishes or discards
the pending write on the next run.
</details>

<details>
<summary><b>If I delete a book on my phone, what happens on my PC?</b></summary>

It's deleted there too, and lands in the PC's Recycle Bin, not gone for good. Both apps say so in the
confirmation before you delete.
</details>

<details>
<summary><b>What's a "conflicted copy"?</b></summary>

If the same book was changed on both devices before they could sync, FloNovel keeps both. The Dropbox
version keeps the original name; the other is saved as, for example,
`Book (conflicted copy - PC - 2026-09-23).txt`. Compare them and delete the one you don't want.
</details>

<details>
<summary><b>Do I have to set up Dropbox and Supabase?</b></summary>

No. Both apps are fully usable with no configuration at all; sync simply reports itself as
unconfigured. Set up only Dropbox if you want a shared library but don't care about position sync.
</details>

<details>
<summary><b>Can FloNovel see my books or my reading history?</b></summary>

There is no FloNovel server. Files sit in your Dropbox app folder; reading positions sit in your
Supabase project. Both are accounts you create and control.
</details>

<details>
<summary><b>Why is my reading position a percentage with decimals?</b></summary>

Because these books are enormous. In a ten-million-character novel, one percent is a hundred thousand
characters, roughly an hour of reading.
</details>

<details>
<summary><b>Nothing appears in my table of contents.</b></summary>

Chapter headings are detected by pattern. Cleaned-up files get `##` markers automatically, but a
heading style FloNovel doesn't recognize won't be marked. The desktop library flags books with
suspiciously few chapters, and both apps let you add your own patterns in settings.
</details>

---

## Under the hood

Both apps are written in **Kotlin** with **Jetpack Compose**: Compose for Android on the phone,
Compose Desktop on the JVM. The Android app stores its library in **Room** and its settings in
**DataStore**; the desktop app uses atomically written JSON files in your OS config directory.

The two apps **share no code**. They interoperate through a written contract (reading position as a
character offset, path normalization, the cleanup rules, and a three-way sync protocol built on
Dropbox revisions and content hashes) that each side enforces with its own tests, including
byte-for-byte comparisons of the two text preprocessors against the same fixtures.

No dependency-injection framework, no ORM beyond Room, no reactive framework. Both apps ship with
unit tests that run headless on the JVM, and the Android app adds an instrumented suite that drives
the real Compose UI on a device.

---

## Building from source

There's no Gradle project at the repository root; each app builds independently.

```bash
# Android: needs JDK 17+ to run Gradle
cd FloNovel-android
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleDebug

# Desktop: needs JDK 17+
cd FloNovel-desktop
./gradlew test                 # unit tests
./gradlew run
```

Neither build requires any credentials. Missing keys leave the build working and disable the matching
sync feature at runtime.

---

## Contributing

Issues and pull requests are welcome. A few things worth knowing before you start:

- The two apps deliberately **share no code**. They interoperate through a contract covering reading
  position, path normalization, preprocessing and the sync protocol; [CLAUDE.md](CLAUDE.md) spells it
  out, and breaking one side silently breaks the other.
- Changing the preprocessor means changing it in **both** apps. Regenerate the expected output on
  the desktop with `./gradlew test -PupdateGolden` and copy it to the Android fixtures; each side's
  tests compare against the other's copy.
- New behavior comes with tests. `BUILD SUCCESSFUL` is not a pass: Gradle prints it after running
  zero tests too.
- Code, comments and commit messages are in English.

---

## License

[Apache License 2.0](LICENSE).
