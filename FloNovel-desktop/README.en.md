**English** · [한국어](README.md)

# FloNovel for Desktop

**A keyboard-driven text novel reader with one- and two-pane views and a library shared with Android.**

![Desktop](https://img.shields.io/badge/Windows%20%C2%B7%20macOS%20%C2%B7%20Linux-0078D6)
![Compose](https://img.shields.io/badge/Compose%20Desktop-4285F4?logo=jetpackcompose&logoColor=white)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](../LICENSE)

[Overview](../README.en.md) · [Android app](../FloNovel-android/README.en.md) · [Issues](https://github.com/katalog/FloNovel/issues)

## Features

### Reader and navigation

- One- or two-pane reading. **Default advance is half the visible content**; the right pane moves left in two-pane mode.
- One-pane turn animation/speed and optional chapter alignment to the left pane.
- Chapter detection, pattern presets/custom regular expressions, contents, full-text search.
- Chapter jump points, four divisions by default; fixed-amount fallback without chapters.
- Character-offset position/progress storage and startup restoration of the last book.
- Remappable shortcuts, timed advance, stopping at the book's end.

### Library and files

- Browse the home folder/subfolders with breadcrumbs; recent/name/date/size sorting.
- Unread/in-progress/completed states and low chapter-marker density indicators.
- Folder watching to preprocess/register new text; hidden files/folders excluded.
- Automatic UTF-8 and EUC-KR/CP949-family encoding detection.
- Open locations in the file manager or files in the default application.
- Send files to trash/chosen folder; delete empty folders.

### Reading environment

- Six themes: warm ivory, sepia cream, dark navy, soft gray, cool light, soft dark brown.
- Font family/weight/size, line height, letter spacing, margins, maximum text width.
- Pane gap/ratio and **UI scale applying to both interface and text**.
- System/downloaded fonts and custom `.ttf` / `.otf` in `fonts/`.
- Catalog distinguishing direct downloads, vendor pages, and system-only fonts.
- Korean/English UI with system/manual language selection; saved window position/size.
- **20-20-20 reminder** prompting a 20-second rest every 20 minutes.
- MP3/AAC internet radio, sleep timer, editable station list.

Radio needs direct streams; webpages/YouTube URLs are unsupported. Bundled station availability depends on external services. Desktop does not have Android's TTS.

## Getting started

1. Run using the build instructions below.
2. Choose **Set home folder** for a folder of `.txt` books.
3. Open a book and use `,` / `.`. `F3` is contents, `F2` search, `F4` settings.
4. Link Dropbox in cloud-sync settings and start the first full sync.

## Default shortcuts

These actions can be remapped in settings.

- `,` / `.`: previous / next screen advance.
- `PgUp` / `PgDn`: previous / next chapter jump point.
- `[` / `]`: previous / next chapter.
- `Esc`: back; `F1`: home folder.
- `F2`: search; `F3`: contents; `F4`: settings.
- `F7`: file manager; `F8`: default application.
- `P`: toggle automatic advance; `Delete`: handle selected file or delete empty folder.

Actions depend on screen/selection. Deletion confirmation explains propagation to other devices.

## File preprocessing

**Preprocessing rewrites actual books and may rename them.** Originals are backed up under `.flonovel/original/` in the home folder first.

- Detect encoding, read, and save as UTF-8.
- Normalize line endings, strip indentation, remove adjacent duplicate content lines, normalize paragraph spacing.
- Add `##` to recognized headings and add file start/end markers.
- Remove Han from mixed Hangul/Han names; shorten to 50 Unicode code points excluding the extension.
- Use temporary files/atomic replacement; do not reprocess books with processing records.

Output bytes are checked against Android's fixtures. There is no UI preprocessing switch; use a library copy to retain original formatting.

## Sync

See the [shared setup guide](../README.en.md#setting-up-sync-optional).

- Bidirectional file sync with `/books` inside the Dropbox app folder.
- Compare local/remote against the last baseline independently; use content hashes/Dropbox revisions.
- Preserve simultaneous edits as remote original and PC conflict copy; edits win over deletions.
- Remote deletions **always go to PC trash**, regardless of the Delete key's move-folder setting.
- Confirm mass deletion/empty remote libraries. Move reading records with moves/renames when one-to-one content-hash matching is possible.
- Defer downloads/deletions/conflicts for the open book until it closes.
- After first sync: Dropbox notifications, focus return at most once per minute, manual requests.
- Progress display, pause/resume.
- Reset baselines/cursors on home-folder change.

Supabase sharing needs server configuration. **Link Desktop first** to generate the shared key. More advanced positions are offered as jumps. Normal uploads cannot move backward; explicit **force upload** replaces incorrect remote positions. Regenerating the key selects a new position partition, so previous remote positions are no longer shared.

## Source builds

There are currently no Desktop binaries on [GitHub Releases](https://github.com/katalog/FloNovel/releases). Use **JDK 17 or newer**.

From the repository root:

```bash
cd FloNovel-desktop
./gradlew test build
./gradlew run
```

On Windows, replace `./gradlew` with `.\gradlew.bat`.

### Packaging

Build on the target OS with its packaging tools, such as WiX for Windows installers.

```bash
./gradlew packageDistributionForCurrentOS
```

Configured formats: Windows `.msi` / `.exe`, macOS `.dmg`, Linux `.deb`. Outputs are under `build/compose/binaries/`.

Build a Windows EXE or application directory without an installer separately:

```bash
./gradlew packageExe
./gradlew createDistributable
```

A Desktop release workflow is not currently included.

### Optional configuration

Copy [local.properties.example](local.properties.example) to `local.properties`.

- `DROPBOX_APP_KEY`: Dropbox App folder app key.
- `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`: position server.
- `FLONOVEL_DEV=true`: separate development settings/position partition.

Same-name environment variables and Gradle properties `-PdropboxAppKey`, `-PsupabaseUrl`, `-PsupabasePublishableKey`, `-PflonovelDev` are supported. Local-reading builds need no sync configuration.

> Development uses `FloNovelDev` for settings and `secret-dev.json` for the key. **Dropbox `/books` is shared**, and fonts/radio lists use the default `FloNovel` directory.

## Data locations

Default configuration directory:

- Windows: `%APPDATA%/FloNovel/`.
- macOS: `~/Library/Application Support/FloNovel/`.
- Linux: `$XDG_CONFIG_HOME/FloNovel/`, or `~/.config/FloNovel/` when unset.

Settings/library/credentials/baselines use `settings.json`, `books.json`, `credentials.json`, `sync-state.json`. Fonts use `fonts/`; radio uses `radio_streams.json`. Originals are backed up in **`.flonovel/original/` under the book home folder**.

## Tests and structure

```bash
./gradlew test
```

JVM tests in `src/test/` cover navigation/layout, encoding/chapters, preprocessing, sync, settings, and related logic. Reader tests use a fake `TextFitter` without UI. Results: `build/test-results/test/`.

Only for intentional preprocessing-output changes:

```bash
./gradlew test -PupdateGolden
```

Copy updated expectations from `src/test/resources/fixtures/parity/` to Android's corresponding folder and run both suites.

Sources are under `src/main/kotlin/com/moonkata/flonovel/desktop/`. `reader/` contains UI-independent navigation/layout; `ui/` screens; `library/` books/settings; `preprocess/` intake; `sync/` synchronization; `platform/` OS-specific behavior.

Stack: Kotlin/JVM · Compose Desktop · juniversalchardet · org.json · JNA · jlayer · javasound-aac. See [AGENTS.md](../AGENTS.md) for contribution rules.

## Troubleshooting and scope

- **Empty contents:** configure a matching heading pattern. Low marker density is an inspection hint, not an error verdict.
- **Login browser fails to open:** copy the app's displayed URL into a browser.
- **Missing library folder:** select the home folder again and start first sync.
- Only `.txt`; EPUB/PDF/ZIP reading unsupported.
- Content is loaded into memory; large-file time/memory usage depends on the environment.
- Local reading works offline; sync, font downloads, and radio need a network.

## License

[Apache License 2.0](../LICENSE). Fonts retain their respective licenses.
