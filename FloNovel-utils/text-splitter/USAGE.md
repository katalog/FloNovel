# Text Splitter for Windows

A standalone Go utility. It has no FloNovel runtime, configuration, database,
Gradle, network, or third-party library dependency. The executable uses native
Windows controls and runs without Go installed.

## Run

Double-click `bin/TextSplitter.exe`. Drag one or more preprocessed UTF-8 TXT files
from Explorer into the window. Optionally customize the chapter recognition pattern
in the **Chapter pattern (regex):** input box (defaults to `^##`; leaving it blank
automatically falls back to the default). Review the queue, and click **Start**. Dropping files
does not start processing. Duplicate paths are queued only once. After a batch,
drop more files to create a new queue. **Clear queue** removes only queued entries.

Close any reader using these files and pause folder synchronization while a batch
is running. This tool does not migrate application reading history or metadata.

## Splitting rules

- One MiB means 1,048,576 bytes. Part count is `ceil(file bytes / (5 MiB))`:
  7 MiB → 2, 11 MiB → 3, 17 MiB → 4, 21 MiB → 5. Files at or below 5 MiB are skipped.
- By default, markers match literal `##` at the beginning of a line (`^##`),
  without indentation or a heading-length limit. A custom regular expression
  (e.g. `^제\s*\d+\s*[화장]`, `^Chapter\s*\d+`) can be configured in the UI.
  At least `30 × file size in MiB` markers are required across the whole file.
  This is a whole-file density check, not a per-window check.
- Choose chapter boundaries to strictly maintain descending part sizes
  (`size(#1) > size(#2) > size(#3) > ... > 0`), ensuring natural reading order
  when sorted descending by file size in Windows Explorer, while minimizing
  size variance among parts. Chapters and original reading order stay intact.
- Output names are `Title#1.txt`, `Title#2.txt`, etc., in the source folder.
- Keep every source byte unchanged, including line endings, preamble, and BOM.
  Input must be valid UTF-8. A BOM before the first `##` is preserved but is not
  itself part of a literal `^##` marker.
- Existing outputs, directories, unsupported encodings, and symbolic links are
  rejected. One failed file does not prevent processing other queued files.
  When any files are skipped or fail, a consolidated summary listing each file name
  and its failure reason is displayed at the bottom of the log window upon batch completion.

## Original files and recovery

Parts are staged and flushed to disk, copied to exclusively created output names,
flushed again, and checked against the SHA-256 of the source in reading order.
The source is checked again before being moved. Only after successful verification
does the original leave its location. The complete original is retained at:

```text
<source folder>/.text-splitter-backup/split-<unique ID>/<original name>.txt
```

Keep the backup until you have checked the results. Rejected files stay in place.
Normal errors trigger output cleanup, with cleanup errors shown in the log. Do not
edit source files during a batch. Linked parent or backup directories are refused.

A forced process termination or power loss can leave partial outputs or staging
files; the original stays at its initial path or in the reported backup folder.
After interruption, locate and compare the original/backup before manually removing
incomplete outputs and retrying. A retry refuses existing numbered output names.
The temporary staging and final output copies require additional free disk space
of about twice the source size. This is not a transaction across cloud sync tools.

## Build and verify

Requires Windows x64, Go 1.23 or newer, and PowerShell. No external Go modules are
downloaded. In this folder, run `build.cmd` or:

```powershell
.\build.ps1
```

The script runs uncached tests, writes JSONL and JUnit-style XML under
`test-results/`, parses the XML to verify actual test counts, runs `go vet`, and
builds `bin/TextSplitter.exe` as a GUI executable without a console window.
Generated executables and reports are ignored by Git.

Win32 reference: [WM_DROPFILES](https://learn.microsoft.com/en-us/windows/win32/shell/wm-dropfiles).
