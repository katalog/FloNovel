[한국어](README.md) | [English](README.en.md)

# FloNovel for KOReader

A single plugin for KOReader on a jailbroken Kindle. This is a development version tested with
KOReader v2026.07.1 on a PC. Kindle execution and live Dropbox/Supabase integration require separate validation.

The installation unit is `flonovel.koplugin`. Chapter navigation, Dropbox files, and reading position
sharing have independent switches. Only chapter navigation is enabled by default.
FloNovel is the first item directly in the Tools tab, in both the reader and file manager.

## Installation and reading

1. Exit KOReader and connect USB. Copy `flonovel.koplugin` from the development package into
   the Kindle's `koreader/plugins/` directory.
2. Disconnect USB and launch KOReader through its usual book-list entry.
3. Open a UTF-8 TXT book and use **FloNovel** for the chapter list, previous/next chapter, and
   subdivision jumps. TXT layout must be preformatted (as-is); the plugin does not change global layout settings.
4. Choose 1/2/4/8/16/32 subdivisions per chapter. Assign `FloNovel:` actions in KOReader's gesture settings.

Chapters are detected from the original `##` prefix on opening, using the confirmed `^##` format.
Trusted headings have no 60-character limit. Unicode trimming, UTF-16 subdivision arithmetic, and the
short-chapter exception of at most 20 LF characters match the existing apps. An unrepresentable target,
including hidden whitespace or a surrogate interior, is deferred with a reason. Ordinary reading continues.
Native page anchors on blank lines map to their exact source-line starts after full DOM order validation.
LF, CRLF, a UTF-8 BOM, and leading/trailing blank lines are covered by ReaderUI regression tests;
a verified terminal empty element maps to logical EOF.

## Dropbox connection

Run `tools/connect.py --help` on a PC with Python 3.9 or later. Use the **same Dropbox public app key**
as the existing FloNovel apps. Do not register another app or import an existing app's token.

```text
python tools/connect.py --app-key PUBLIC_APP_KEY --library-root /mnt/us/documents/FloNovel --supabase-url https://PROJECT.supabase.co --publishable-key PUBLIC_API_KEY --output PRIVATE_DIRECTORY/config.json
```

To reuse the existing Desktop public connection values, replace the three connection options with
`--desktop-properties ../FloNovel-desktop/local.properties`. This reads only the app key, Supabase URL,
and public API key; it does not import tokens or an app secret.

Approve access in the browser and enter its one-time code at the PC prompt. Keep the output outside
the repository. It contains a refresh token and must be protected like a password; never paste it into
chat or commit it. No app secret is required. Use `--dev` for the existing development secret partition.

Create the selected Kindle library directory and copy the file to `koreader/settings/flonovel/config.json`.
For a custom `KO_HOME`, use its settings directory. Run **Check connection and load shared secret** from
the FloNovel menu. This reads the existing `/.flonovel/secret.json`. If Supabase is configured, it uses
`__connection_test__` to verify acceptance of `source=koreader` and server max-wins. Automatic position
transmission remains gated until verification succeeds. Missing secrets are never regenerated.

## File and position synchronization

Use **Synchronize Dropbox files now** to transfer files. This version fetches a complete listing;
it does not watch files continuously or persist an incremental cursor. It downloads preprocessed TXT
files under `/books` and propagates deletion of previously tracked individual local TXT files using
`parent_rev`. Existing identical local files are adopted after content-hash verification. A missing
parent folder is never inferred to mean individual deletion of all its books.

Local edits, concurrent edits, and automatic moves are preserved and deferred. Hash-identified moves
do not count as deletions. New/modified text uploads and folder-deletion journals are unsupported;
this is not complete bidirectional file synchronization. Remote edits are downloaded only when local
content still matches its base. Replaced originals and remotely deleted local originals are retained
under `.flonovel/recovery/` inside the library and never automatically purged. Open books are protected
from downloads, deletions, and conflict handling. Mass deletion or an empty remote library requires
confirmation followed by revalidation of the exact plan. Inspect **Synchronization status** for deferrals.

Position sharing works independently of file transfers. Opening/resuming a book fetches its remote
position; a remote lead exceeding 500 characters offers a jump. After 10 seconds without a page update,
the plugin queues the position derived from ReaderRolling's canonical anchor and sends it when online.
Closing/suspending also saves requests. Network work runs in a cancellable subprocess; offline work is
retried on subsequent sends. Requests with a changed body hash, account, app, library, or secret partition
are withheld. Supabase requests omit Authorization and user_key. Push never performs a preceding GET
for conflict resolution; server max-wins remains authoritative.

Opening a book checks the remote row before building a source map, so absent or offline lookups do
not trigger full-book mapping. Chapter analysis uses one Unicode pass, and content hashing uses
the bundled native crypto library when available, with a Lua fallback. Source-hash checks remain
mandatory; PC timing improvements still require separate validation on the Kindle.
Preparation phase timings and the hash backend are logged without book paths or credentials.

Changing account or library blocks state reuse. Verify the new connection, then explicitly reset state
from the menu. Reset preserves books and clears previously unsent position requests.


## Experimental fast position sharing

Enable **Fast position sharing (experimental)** to match the current native text against the source
instead of building a full DOM position table. The bundled utf8proc counts UTF-16 units and tests
ASCII, Hangul, and emoji vectors on startup. Results are written to settings/flonovel/fast-backend.json.
This option is off by default; fast_position_enabled in the connection configuration preserves it.
An installation can explicitly enable the option.

The source-body hash is bound asynchronously. Native capture, search, enqueue and transmission run
in the worker, preserving body/account/library guards and exact offsets. Blank anchors resolve to exact
source-line starts after checking native parent boundaries and preceding paragraph text. Unverified EOF,
repeated paragraphs, long lines, transformed layouts and invalid Unicode are held. Chapter actions still use the
original mapper on demand, and close checkpoints retain a synchronous path. This does not guarantee
a one-second bound or support for every book. Disable the option to restore the original position path.

## Current limits and removal

- Downloads stream to disk without a 16 MiB cap. Storage/filesystem errors and content-hash verification still apply.
- Mapping for chapter navigation and position sharing is currently capped at 16 MiB per file. Larger books can be downloaded and read with KOReader itself; expanding mapping-memory support is a separate improvement.
- Only UTF-8 TXT and the verified preformatted DOM grammar are supported. Other encodings or engine transformations are not guessed.
- Keys use NFC and a JDK 21 non-contextual lowercase table. Context-sensitive capital Greek Sigma and
  Unicode characters undefined in that JDK are deferred. NFKC casefold is never used.
- Live three-client interoperability and Kindle memory, CA, and suspend behavior still need integration validation.

Disable FloNovel through KOReader's plugin manager and restart. To uninstall, exit KOReader and remove
only `koreader/plugins/flonovel.koplugin/`. Preserve library books and recovery files. To remove the
connection too, separately remove credentials under `koreader/settings/flonovel/` and revoke the relevant
Dropbox authorization. Do not run KOReader while USB storage is connected.

## Development verification

For an interactive Windows/WSL2 session with WSLg, run:

```powershell
.\FloNovel-koreader\tools\start_emulator.ps1
```

The launcher expects the official Linux v2026.07.1 runtime at
`.work/runtime/lib/koreader`. It opens the actual SDL GUI with isolated settings, caches,
and synthetic 128 KiB/2 MiB/8 MiB books under `.work/emulator/desktop`. Each launch refreshes
the 14 plugin files while preserving that profile's settings and reading state. Close the
window before relaunching. Use `-Profile another-name` for an independent profile and `-Jit`
for normal JIT execution; the default interpreter mode stresses Lua work on the host.
The default CPU quota is 5% of one core, enforced by a systemd user scope with a 100 ms
period. It also covers forked workers and leaves other WSL applications unaffected. Use
`-CpuPercent 2` for a slower run or `-CpuPercent 0` for an unlimited run. CPU limiting
requires a working systemd user manager; an unavailable limit is not silently ignored.

Use `-NoIndex` to try the Linux-only, synthetic no-index position overlay. The selected mode
is remembered per profile; `-FullMap` restores the original implementation. Only the emulator
copy is patched. The generated `no-index-8MiB.txt` has distinguishable paragraphs for testing.
Normal position updates avoid full-map/chapter preparation, and search/enqueue/flush run in a
cancellable subprocess. Remote targets are checked against native text before offering a jump.
Chapter actions still prepare the original map on demand. Ambiguous repeated paragraphs and
non-text anchors are held explicitly. This experimental mode is not a Kindle release.

The GUI profile enables chapter and position features. FloNovel position requests use a
local synthetic transport and JSON max-wins state, with no real account or network requests.
Dropbox behavior is covered separately by the offline product gate. The mock does not validate
the live server or TLS. CPU architecture, memory limits, suspend, and e-ink behavior still need
Kindle verification. Host timings must not be reported as Kindle timings.

Use an extracted official Linux KOReader v2026.07.1 runtime, Python 3, and OpenSSL on Linux/WSL.

```text
python3 tests/run.py --runtime /path/to/koreader --results /private/path/results
python3 tools/package.py --results /private/path/results/SESSION/summary.json --output dist/flonovel-koreader-dev.zip
```

Tests use synthetic books, actual ReaderUI/crengine, captured Kotlin oracles, and a local HTTPS server.
They do not write to a Kindle or live account. Packaging verifies the passing source hashes and an
explicit 14-file product allowlist. Research, personal books, tokens, runtimes, and work.md are excluded.
