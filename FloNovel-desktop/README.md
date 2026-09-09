<div align="center">

# FloNovel for Desktop

**A keyboard-driven reader for your own `.txt` novels — with a two-page spread, automatic cleanup, and
your library on your phone.**

![Windows · macOS · Linux](https://img.shields.io/badge/Windows%20%C2%B7%20macOS%20%C2%B7%20Linux-0078D6)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Compose Desktop](https://img.shields.io/badge/Compose%20Desktop-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

[Install](#install) · [Getting started](#getting-started) · [Shortcuts](#keyboard-shortcuts) · [FAQ](#faq)

</div>

---

The desktop half of **[FloNovel](../README.md)**. This is the app that owns your library: give it a
folder, and it keeps every text file in it clean, catalogued, and — if you want — mirrored to your
phone.

<!-- Screenshots go here. Recommended: the 2-pane reader in dark theme, the library, and the settings
     dialog on the Shortcuts tab. -->

---

## Features

### 📖 One page or two

Read a single column, or a full two-page spread like an open book. Both modes advance the same way —
by half of what you can currently see — so in two-page mode the right page slides over to become the
left, exactly as turning a paper page would.

### ⌨️ Built for the keyboard

Every action is a key, and every key can be reassigned by clicking a row in settings and pressing what
you'd rather use. Controls auto-hide seamlessly so you can focus entirely on reading.

### 🧹 Your library, cleaned up automatically

Drop a text file into your folder and FloNovel picks it up on its own: line endings unified,
duplicated lines and runaway blank space removed, chapter headings marked, overlong filenames
shortened. Your original is backed up first. It only ever happens once per file.

### 🔤 Encodings sorted out for you

UTF-8, EUC-KR, and CP949 are detected automatically, including the extended Korean characters that
usually render as boxes. There is no encoding menu.

### 📑 Chapters, search, and a table of contents

Chapter headings are found every time you open a book — nothing is cached, so changing a pattern
re-detects the whole book instantly. `F3` lists them, `F2` searches the full text and jumps straight
to a hit.

### 🎨 Tuned for reading, not for demos

Light, dark, and sepia themes. Font, size, line height, letter spacing, margins, gutter width, and the
left/right pane ratio are all adjustable — and a separate UI scale means you can enlarge the text
without inflating the buttons. Cap the line width so a paragraph doesn't stretch across an ultrawide
monitor.

**Blank lines get their own spacing control.** Preprocessed novels alternate one line of text with one
blank line, so the blanks end up eating as much of the page as the prose. Tighten them on their own,
from 100% down to 50% in 10% steps, without touching the line height of the text itself.

The interface speaks **English and Korean**, following your system by default or set explicitly in
settings.

### 🎧 Background audio with a sleep timer

Play an internet radio stream while you read — three stations are built in (The Lounge Hour,
RelaxingJazz.com, COTN Radio). Set how long it should run (60 minutes by default, with 30/90/120
presets and minute-level adjustment) and it stops on its own, so it winds down when you do instead of
playing all night. The remaining time sits next to the progress percentage in the footer, and one
click on the reader's media button stops everything immediately.

### 🔠 Fonts, handled honestly

A built-in catalog shows what's already installed, what can be downloaded directly, and what has to
come from the vendor's own page for licensing reasons — with a link instead of a button that
wouldn't work. Downloads are verified before they're used.

### ☁️ Sends your library to your phone

Connect Dropbox and your books upload in the background, resumable and pausable, with per-file
progress. Reading position syncs both ways with the Android app.

---

## Install

Packaged installers aren't published yet. Building one takes a single command and **JDK 17 or newer**:

```bash
cd FloNovel-desktop
./gradlew packageDistributionForCurrentOS
```

| Platform | You get |
|---|---|
| Windows | `.msi` installer |
| macOS | `.dmg` |
| Linux | `.deb` |

On Windows there are two more options: `./gradlew packageExe` builds a `.exe` installer, and
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
Click a book. `.` and `,` turn pages, `PgUp`/`PgDn` move chapter to chapter, `Esc` goes back to the
listing.

**3. Adjust.**
`F4` opens settings — three tabs: **Reading View**, **Shortcuts**, and **Cloud Sync**.

**4. Connect Dropbox** *(optional).*
On the Cloud Sync tab, click **Log in**. A browser window opens; approving it returns you to the app.
Then start the first full upload from the banner in the library.

---

## Keyboard shortcuts

| Action | Default |
|---|---|
| Next page | `.` |
| Previous page | `,` |
| Next chapter | `Page Down` |
| Previous chapter | `Page Up` |
| Back to the folder listing | `Esc` |
| Library home | `F1` |
| Search | `F2` |
| Table of contents | `F3` |
| Settings | `F4` |

All of these are remappable under **Settings → Shortcuts**: click the row, press the
key you want. **Reset to Defaults** puts the table above back.

---

## What FloNovel does to your files

The desktop app **rewrites text files in your home folder**. That's the point of it, but you should
know exactly what happens:

**Every file is backed up first**, unmodified, into `.flonovel/original/` inside your home folder.
Each file is processed exactly once — a book already handled is never touched again. The rewrite goes
through a temporary file and replaces the original atomically, so a crash or power loss mid-write
leaves the original file intact. If there isn't enough free disk space, it stops before starting.

What the cleanup changes:

- `\r\n` and `\r` line endings become `\n`
- Leading whitespace is stripped from each line
- Adjacent duplicate lines are removed
- Blank-line spacing is regularized; runs of three or more newlines collapse to one blank line
- Lines that look like chapter headings get a `##` prefix, which is what makes the table of contents
  work in both apps
- Filenames containing both Korean and Chinese characters have the Chinese removed, and very long
  names are shortened

Don't want any of this? Keep your originals elsewhere and point FloNovel at a copy.

---

## Where things are stored

```text
<OS config dir>/FloNovel/          settings.json · books.json · credentials.json
<OS config dir>/FloNovel/fonts/    fonts downloaded from within the app
<your home folder>/.flonovel/original/    backups of every file processed
```

`<OS config dir>` is `%APPDATA%` on Windows, `~/Library/Application Support` on macOS, and
`$XDG_CONFIG_HOME` (or `~/.config`) on Linux.

---

## About sync

Book files go **one way**: this app uploads to Dropbox, and your phone downloads. The phone never
uploads and never deletes remotely — so this app is the single source of truth for what's in your
library.

Reading position syncs **both ways**. When your phone has read further than you have here, FloNovel
tells you how much further and offers to jump, rather than moving you on its own.

Connecting Dropbox is the entire pairing step. This app generates a random key on first connect and
stores it in your Dropbox app folder; your phone finds it there. Nothing is typed or scanned. You can
regenerate that key from settings, but it abandons every reading position already stored remotely, so
it asks first.

Full setup instructions are in the [main README](../README.md#setting-up-sync-optional).

---

## FAQ

<details>
<summary><b>Does it read EPUB or PDF?</b></summary>

No — FloNovel is a plain-text reader by design.
</details>

<details>
<summary><b>Can I stop it from modifying my files?</b></summary>

Not from within the app. Point it at a copy of your library instead — originals are also backed up
into `.flonovel/original/` before anything is changed.
</details>

<details>
<summary><b>My table of contents is empty.</b></summary>

Chapter headings are matched by pattern. Files processed by FloNovel carry `##` markers automatically;
files added another way may not match. You can add your own patterns in settings. Zero chapters is a
normal result, not an error — chapter keys then move by a fixed amount instead.
</details>

<details>
<summary><b>The browser didn't open when I tried to connect Dropbox.</b></summary>

The app falls back to showing you the URL with a copy button — paste it into any browser and the
login completes normally.
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

### Built with

Kotlin/JVM · Compose Desktop · juniversalchardet (encoding detection) · org.json · jlayer and
javasound-aac (radio streaming). No dependency-injection framework, no ORM, no reactive framework —
the whole test suite runs headless without a window, against a fake text measurer.

---

## License

[Apache License 2.0](../LICENSE).
