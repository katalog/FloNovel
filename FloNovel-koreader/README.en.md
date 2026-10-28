[한국어](README.md) | [English](README.en.md)

# FloNovel for KOReader

A single plugin for KOReader on a jailbroken Kindle. This is a development version tested with
KOReader v2026.07.1 on a PC. Kindle execution and live Dropbox/Supabase integration require separate validation.

The installation unit is `flonovel.koplugin`. Chapter navigation, Dropbox files, and reading position
sharing have independent switches. Only chapter navigation is enabled by default.

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

Changing account or library blocks state reuse. Verify the new connection, then explicitly reset state
from the menu. Reset preserves books and clears previously unsent position requests.

## Current limits and removal

- Mapping and downloads are capped at 16 MiB per file. Larger books remain readable through KOReader itself.
- Only UTF-8 TXT and the verified preformatted DOM grammar are supported. Other encodings or engine transformations are not guessed.
- Keys use NFC and a JDK 21 non-contextual lowercase table. Context-sensitive capital Greek Sigma and
  Unicode characters undefined in that JDK are deferred. NFKC casefold is never used.
- Live three-client interoperability and Kindle memory, CA, and suspend behavior still need integration validation.

Disable FloNovel through KOReader's plugin manager and restart. To uninstall, exit KOReader and remove
only `koreader/plugins/flonovel.koplugin/`. Preserve library books and recovery files. To remove the
connection too, separately remove credentials under `koreader/settings/flonovel/` and revoke the relevant
Dropbox authorization. Do not run KOReader while USB storage is connected.

## Development verification

Use an extracted official Linux KOReader v2026.07.1 runtime, Python 3, and OpenSSL on Linux/WSL.

```text
python3 tests/run.py --runtime /path/to/koreader --results /private/path/results
python3 tools/package.py --results /private/path/results/SESSION/summary.json --output dist/flonovel-koreader-dev.zip
```

Tests use synthetic books, actual ReaderUI/crengine, captured Kotlin oracles, and a local HTTPS server.
They do not write to a Kindle or live account. Packaging verifies the passing source hashes and an
explicit 13-file product allowlist. Research, personal books, tokens, runtimes, and work.md are excluded.
