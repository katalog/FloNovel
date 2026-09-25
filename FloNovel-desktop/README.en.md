<div align="center">

# FloNovel for Desktop

**A keyboard-driven reader for your own `.txt` novels, with a two-page spread, automatic cleanup,
and your library shared with your phone.**

![Windows · macOS · Linux](https://img.shields.io/badge/Windows%20%C2%B7%20macOS%20%C2%B7%20Linux-0078D6)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Compose Desktop](https://img.shields.io/badge/Compose%20Desktop-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

**English** · [한국어](README.md)

[Install](#install) · [Getting started](#getting-started) · [Shortcuts](#keyboard-shortcuts) · [Sync](#about-sync) · [FAQ](#faq)

</div>

---

The desktop half of **[FloNovel](../README.en.md)**. Give it a folder, and it keeps every text file in
it clean, catalogued, and (if you want) in step with your phone.

<!-- Screenshots go here. Recommended: the 2-pane reader in dark theme, the library, and the settings
     dialog on the Shortcuts tab. -->

---

## Features

### 📖 One page or two

Read a single column, or a full two-page spread like an open book. Both modes advance the same way,
by half of what you can currently see, so in two-page mode the right page slides over to become the
left, exactly as turning a paper page would. In one-page mode, a short half-slide animation (fast,
normal, slow, or off) keeps your eye on the line you were reading.

In one-page mode, a chapter jump lands in the middle of the screen. In two-page mode you can have
every new chapter start at the top of the left page.

### ⌨️ Built for the keyboard

Every action is a key, and every key can be reassigned: click a row in settings and press what you'd
rather use. Controls stay out of the way so the page is just text.

### 📑 Chapters, search, and a table of contents

Chapter headings are found every time you open a book. Nothing is cached, so changing a pattern
re-detects the whole book instantly.

- **Next / previous chapter** goes straight to the next heading.
- **Chapter jump** splits each chapter into evenly spaced points (4 by default) so a 40-minute
  chapter is still navigable. Books with no chapters at all move by a fixed amount instead.
- `F3` lists every chapter and scrolls to where you are; `F2` searches the full text and jumps
  straight to a hit.
- The library flags books with **suspiciously few chapter markers** per megabyte, so you notice a
  heading style that needs a custom pattern before you're halfway through.

### 🧹 Your library, cleaned up automatically

Drop a text file into your folder and FloNovel picks it up on its own: line endings unified,
duplicated lines and runaway blank space removed, chapter headings marked, overlong filenames
shortened. Your original is backed up first, and each file is processed only once. See
[what exactly changes](#what-flonovel-does-to-your-files).

### 🔤 Encodings sorted out for you

UTF-8, EUC-KR and CP949 are detected automatically, including the extended Korean characters that
usually render as boxes. There is no encoding menu.

### 🗂️ A library that stays out of your way

Browse your home folder with breadcrumbs, sort by recent, name, date or size, and see how far into
each book you are. Recently opened books sit at the top. On launch, FloNovel reopens the book you were
reading, at the spot you left it.

`F7` reveals a book in your file manager, `F8` opens it in your default app, and `Delete`
sends it to the Recycle Bin, or to a folder of your choosing. Empty folders can be deleted the same
way.

### 🎨 Tuned for reading, not for demos

Six themes: Warm Ivory, Sepia Cream, Dark Navy, Soft Gray, Cool Light and Soft Dark Brown. On
Windows 11 the title bar follows the theme too.

Font, weight, size, line height, letter spacing, margins, gutter width and the left/right pane ratio
are all adjustable, with a live preview. A separate **UI scale** lets you enlarge the text without
inflating the buttons, and a **maximum line width** stops a paragraph from stretching across an
ultrawide monitor.

The interface speaks **English and Korean**, following your system by default or set explicitly in
settings.

### 🔠 Fonts, handled honestly

A built-in catalog of Korean and Latin reading fonts (Pretendard, Noto Sans KR, MaruBuri, RIDIBatang,
EB Garamond and more) shows what's already installed, what can be downloaded directly, and what has
to come from the vendor's own page for licensing reasons, with a link instead of a button that
wouldn't work. Downloads are verified before they're used. Prefer your own? Drop `.ttf` or `.otf`
files into the fonts folder and they show up immediately.

### ⏱️ Hands-free reading

**Auto page-turn** (`P`) turns the page on a fixed interval: 5, 10, 15 or 20 seconds, or your own. It
stops by itself at the end of the book.

### 👀 Eye-rest reminder

Turn on the **20-20-20 reminder** and every 20 minutes the reader pauses for a 20-second break to look
at something far away, then returns you to your page.

### 🎧 Background audio with a sleep timer

Play an internet radio stream while you read. Three stations are built in (The Lounge Hour,
RelaxingJazz.com, COTN Radio), and you can edit the list with any direct MP3/AAC stream. Set how long
it should run (60 minutes by default, with 30/90/120 presets and minute-level adjustment) and it stops
on its own. The remaining time sits next to the progress percentage in the footer, and one click on
the media button stops everything immediately.

### ☁️ Your library on your phone, and back

Connect Dropbox and your library syncs **both ways** with the Android app: books added, edited,
renamed, moved or deleted on either side reach the other. Dropbox changes are picked up the moment
they happen. Reading position syncs both ways too. See [About sync](#about-sync).

---

## Install

Packaged installers aren't published yet. Building one takes a single command and **JDK 17 or
newer**:

```bash
cd FloNovel-desktop
./gradlew packageDistributionForCurrentOS
```

| Platform | You get |
|---|---|
| Windows | `.msi` installer |
| macOS | `.dmg` |
| Linux | `.deb` |

On Windows there are two more options: `./gradlew packageExe` builds an `.exe` installer, and
`./gradlew createDistributable` produces a **portable** build that runs from a folder without
installing anything.

Just want to try it without packaging?

```bash
./gradlew run
```

---

## Getting started

**1. Set a home folder.**
Click **Set home folder** in the top right and choose the directory holding your `.txt` files.
FloNovel scans it, cleans up what it finds, and lists everything. Anything you add to that folder
later is picked up automatically while the app is running.

**2. Read.**
Click a book. `.` and `,` turn pages, `PgUp`/`PgDn` jump through the chapter, `[` and `]` move
chapter to chapter, `Esc` goes back to the listing.

**3. Adjust.**
`F4` opens settings, organized in four tabs: **Reading View**, **Shortcuts**, **Files** and **Cloud
Sync**.

**4. Connect Dropbox** *(optional).*
On the Cloud Sync tab, click **Log in**. A browser window opens; approving it returns you to the app.
Then start the first full sync from the banner in the library. After that, changes sync on their own.

---

## Keyboard shortcuts

| Action | Default |
|---|---|
| Next page | `.` |
| Previous page | `,` |
| Next chapter jump point | `Page Down` |
| Previous chapter jump point | `Page Up` |
| Next chapter | `]` |
| Previous chapter | `[` |
| Back / parent folder | `Esc` |
| Library home | `F1` |
| Search | `F2` |
| Table of contents | `F3` |
| Settings | `F4` |
| Reveal file in file manager | `F7` |
| Open file in default app | `F8` |
| Toggle auto page-turn | `P` |
| Delete / move file | `Delete` |

All of these are remappable under **Settings → Shortcuts**: click the row, press the key you want, or
press `Esc` to clear it. **Reset to Defaults** puts the table above back.

At the top level of the library, `Esc` asks before closing the app.

---

## What FloNovel does to your files

The desktop app **rewrites text files in your home folder**. That's the point of it, but you should
know exactly what happens.

**Every file is backed up first**, unmodified, into `.flonovel/original/` inside your home folder.
Each file is processed exactly once: a book already handled is never touched again. The rewrite goes
through a temporary file and replaces the original atomically, so a crash or power loss mid-write
leaves the original intact. If there isn't enough free disk space, it stops before starting.

What the cleanup changes:

- `\r\n` and `\r` line endings become `\n`
- Leading whitespace (including full-width spaces and tabs) is stripped from each line
- Adjacent duplicate lines are removed
- Blank-line spacing is regularized; runs of three or more newlines collapse to one blank line
- Lines that look like chapter headings (`제45화`, `第184章`, `Chapter 12`, `#42` and similar) get a
  `##` prefix, which is what makes the table of contents work in both apps
- `## 파일 시작` and `## 파일 끝` markers are added at the start and end of the book
- Filenames containing both Korean and Chinese characters have the Chinese removed, and names longer
  than 50 characters are shortened

The Android app applies the same rules, producing the same bytes, to books added on the phone.

Don't want any of this? Keep your originals elsewhere and point FloNovel at a copy.

---

## About sync

Books sync **both ways** through your Dropbox app folder, which is the shared source of truth:

- **Three-way comparison.** For every book, FloNovel remembers the state both sides last agreed on
  and decides from that, never by guessing from what exists where, and never by trusting clocks.
- **No blind overwrites.** Uploads are tied to the Dropbox revision they're based on. If the book
  changed on both sides, both versions are kept: the Dropbox one under the original name, this PC's
  as `Book (conflicted copy - PC - 2026-09-23).txt`. An edit always wins over a deletion.
- **Deletions are recoverable.** A book deleted on your phone goes to this PC's Recycle Bin, whatever
  your Delete key is set to.
- **Mass deletions ask first.** If one sync would delete 20 or more books, a large share of your
  library, or finds your remote library empty, it stops and shows you the list. That's almost always
  a different Dropbox account or a reset app folder.
- **Moves are moves.** Renaming or moving a book is carried out as a move on the other side, with its
  reading history, instead of a re-upload.
- **The open book waits.** Changes to the book you're reading are applied after you close it.

**When it syncs:** as soon as Dropbox reports a change, when you return to the window (at most once a
minute), and whenever you click **File sync**. Uploads show per-file progress and can be paused and
resumed.

Reading position syncs **both ways** as well. When your phone has read further, FloNovel shows how far
and offers to jump (`Enter`) rather than moving you on its own. If a wrong position ever gets stuck
in the cloud, **Force upload** on the Cloud Sync tab replaces it with this PC's.

Connecting Dropbox is the entire pairing step. This app generates a random key on first connect and
stores it in your Dropbox app folder; your phone finds it there. You can regenerate that key from
settings, but it abandons every reading position already stored remotely, so it asks first.

Full setup instructions are in the [main README](../README.en.md#setting-up-sync-optional).

---

## Where things are stored

```text
<OS config dir>/FloNovel/          settings.json · books.json · credentials.json · sync-state.json
<OS config dir>/FloNovel/fonts/    downloaded fonts, and your own .ttf / .otf files
<your home folder>/.flonovel/original/    backups of every file processed
```

`<OS config dir>` is `%APPDATA%` on Windows, `~/Library/Application Support` on macOS, and
`$XDG_CONFIG_HOME` (or `~/.config`) on Linux. Development builds keep their settings in `FloNovelDev`
instead, so they never touch your real library state.

---

## FAQ

<details>
<summary><b>Does it read EPUB or PDF?</b></summary>

No. FloNovel is a plain-text reader by design.
</details>

<details>
<summary><b>Can I stop it from modifying my files?</b></summary>

Not from within the app. Point it at a copy of your library instead. Originals are also backed up
into `.flonovel/original/` before anything is changed.
</details>

<details>
<summary><b>My table of contents is empty.</b></summary>

Chapter headings are matched by pattern. Files processed by FloNovel carry `##` markers automatically,
but an unusual heading style may not be recognized; the library flags such books as having few
chapter markers. You can add your own patterns in settings. Zero chapters is a normal result, not an
error: chapter keys then move by a fixed amount instead.
</details>

<details>
<summary><b>I deleted a book and it disappeared from my phone too.</b></summary>

That's two-way sync working as intended. The confirmation says so before you delete. The file is in
your Recycle Bin (or your chosen move folder); put it back into the library folder and it syncs back.
</details>

<details>
<summary><b>The browser didn't open when I tried to connect Dropbox.</b></summary>

The app falls back to showing you the URL with a copy button. Paste it into any browser and the login
completes normally.
</details>

<details>
<summary><b>Does it work without Dropbox or Supabase?</b></summary>

Completely. Both are optional; unconfigured builds simply show sync as unavailable and everything else
works.
</details>

<details>
<summary><b>Is the app available in English?</b></summary>

Yes. English and Korean are both included. It follows your system language by default, and you can
pick one explicitly under **Settings → Reading View → Language**.
</details>

---

## Building from source

Requires **JDK 17 or newer**.

```bash
./gradlew run                              # launch
./gradlew test                             # unit tests (headless)
./gradlew packageDistributionForCurrentOS  # msi / dmg / deb
./gradlew packageExe                       # Windows installer
```

For sync features, copy `local.properties.example` to `local.properties`:

| Key | What it's for |
|---|---|
| `DROPBOX_APP_KEY` | Your Dropbox app key. Register with **App folder** access and redirect URI `http://localhost:52475/oauth/callback` |
| `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` | Your Supabase project, for reading-position sync |
| `FLONOVEL_DEV` | `true` keeps a development build's settings and sync data separate from your real ones |

**All optional.** The build succeeds with every field blank, and the matching feature reports itself
as unconfigured at runtime.

### Tests

The whole suite runs headless without a window, against a fake text measurer. Preprocessing and
chapter detection are tested against checked-in fixtures that reproduce real-world files, and the
preprocessor's output is compared byte for byte with the Android app's copy of the expected results.
After an intentional preprocessor change, regenerate them with:

```bash
./gradlew test -PupdateGolden
```

### Built with

Kotlin/JVM · Compose Desktop · juniversalchardet (encoding detection) · org.json · JNA (native title
bar) · jlayer and javasound-aac (radio streaming). No dependency-injection framework, no ORM, no
reactive framework.

---

## License

[Apache License 2.0](../LICENSE).
