[한국어](README.md) | English

# Offline engine experiments

These checks use synthetic text and the official KOReader Linux runtime. They do
not contact Dropbox or Supabase, install anything on a Kindle, or open user books.
The limited experimental scope is documented in the root `AGENTS.md`.

## Runtime

Use the official `koreader-linux-x86_64-v2026.07.1.tar.xz` release, matching the
connected device's KOReader version. Extract it under `.work/runtime/` so that
`.work/runtime/lib/koreader/luajit` exists.

- Source: https://github.com/koreader/koreader/releases/tag/v2026.07.1
- Archive SHA-256: `299aadb28147a25e9432ced1214ea444a4184393b5ae97cf42402c8a61b1a1b0`
- Linux userspace: x86-64; the current host uses Ubuntu 24.04 under WSL.
- The Linux runtime version matches the device version; this is not a Kindle ABI
  or device execution test.

## Run

From this project directory inside Linux/WSL:

```sh
python3 experiments/run_engine_smoke.py
```

The runner verifies the runtime version, creates a synthetic UTF-8 fixture, and
uses KOReader's bundled LuaJIT and actual crengine binding. It writes fixture
metadata and per-check JSON results to `.work/results/engine-smoke/`.

The smoke checks cover font availability, TXT loading in both parsing modes,
XPointer validity, and access to parsed body text. A nonzero failure count returns
a failing exit code. Seven successful checks establish engine availability only.
They do **not** establish raw UTF-16 position mapping, visual accuracy, plugin UI
integration, or device compatibility.

The runtime and results are private working files and must not be distributed
with a plugin package.

## Position and reader experiments

```sh
python3 experiments/run_position_probe.py
```

This generates synthetic TXT cases, inspects their actual DOM, and compares raw
UTF-16 offsets with XPointers and independently extracted engine characters. It
then loads the experimental plugin through the real plugin loader and ReaderUI,
using a temporary `KO_HOME` and SDL dummy screen. It checks menu registration,
dispatcher actions, reader anchors, font changes, and reopening the document.
It does not install on a Kindle or reuse existing reader settings or built-in plugins.

Results are written under `.work/results/position-probe/`. The strict experimental
mapper rejects transformed separator/control-title cases and documents without
visible text. Passing those rejection checks does not establish general TXT
compatibility. EOF and hidden positions are not rounded to a guessed location.

## Kotlin oracle and extended checks

From this project directory on Windows:

```powershell
./experiments/run_position_probe.ps1
```

This uses the existing Desktop build classes and cached Kotlin compiler, without
installing dependencies. Build the Desktop classes first. The wrapper prepares
synthetic inputs, invokes the real TextLoader/ChapterDetector/ChapterJumpNavigator,
binds the oracle to the manifest hash, and runs parity, boundary diagnostics, and
long-document/same-screen subdivision tests through actual ReaderUI events.
The Python command alone does not run the Kotlin-dependent extended reader checks.

The optional diagnostic mapping preserves exact positions after recognized parser
changes, blocks transformed separator interiors, and exposes only node ends whose
reverse mapping exactly matches the source offset. It does not enable approximation
or change the strict mapper's default rejection policy. Reader input copies and
sidecars are isolated under a fresh KO_HOME for every session.

## Public golden fixtures, scale, and cache invalidation

From this project directory in WSL/Linux, run:

```sh
python3 experiments/run_scale_probe.py
```

The runner compares the existing Android/Desktop parity golden outputs byte for
byte, copies them into private results, and verifies the originals remain
unchanged. These are preprocessed UTF-8 outputs; filenames mentioning CP949 or
EUC-KR do not imply that the Lua mapper decodes those encodings. Empty outputs
must be rejected. The runner also checks distributed exact character positions
in synthetic 128 KiB, 1 MiB, and 4 MiB documents.

`scale-results.json` records process CPU timings, Lua heap growth, and process RSS
under `.work/results/scale-probe/`. Native engine caches may be warm; RSS is an
observation, not peak memory or a per-book allocation measurement. These PC
measurements do not establish Kindle performance or interaction latency.

`cache-results.json` covers a single-entry, in-memory mapping cache prototype.
Source hashes, parser/DOM/mapping identities, document objects, generation, book,
library, and account scope participate in validation. Same-size/same-mtime text
changes must not reuse the old map. Close invalidation is explicit in the harness;
actual ReaderUI lifecycle wiring and persisted cache recovery remain unimplemented.
The source hash here is SHA-256, not Dropbox's block content hash. No chapter list
or second reading anchor is persisted.

## Plugin faults, disabling, and reopening

```sh
python3 experiments/run_lifecycle_probe.py
```

This runs synthetic TXT and plugins in a fresh KO_HOME through actual discovery,
loading, and ReaderUI. Syntax, module-load, initialization, and event failures must
leave the healthy plugin, ordinary page navigation, and painting functional. It
also exercises the actual plugin-manager toggle, persisted disable state, fresh
loader discovery, re-enabling, and reader-position retention. Built-in plugins are
disabled in the isolated settings; existing settings and books are not reused.

Results are in `.work/results/a2/lifecycle-results.json`; `process.log` contains
expected synthetic errors. The 24 checks include 23 Lua/ReaderUI checks and one
source-hash preservation check. Loader reset occurs within one process; this does
not test a real process or Kindle restart. Infinite loops, native crashes, and all
callback paths are outside this fault-isolation claim.

## Optional bulk mapping and the requested hash marker

After preparing the position/Kotlin oracle and scale inputs with the commands
above, run this from the project directory in WSL/Linux:

```sh
python3 experiments/run_bulk_probe.py
```

The optional mapper reads the engine's body serialization once and feeds extracted
nodes through the unchanged reference source validation. Only the observed
body/pre/empty-line grammar and basic XML entities are supported; unknown
structures are rejected. This is an experiment, not a change to the default
mapper. It compares node fields and exact character samples, then runs existing
ReaderUI checks in fresh sessions. Bulk UI results use a `bulk-` prefix so the
reference reports are preserved. CPU timing and Lua heap observations do not
establish Kindle wall time or peak memory.

The requested marker is the trusted `##` prefix in preprocessed source. Prepare
its synthetic cases in WSL/Linux, then generate expectations on Windows:

```sh
python3 experiments/prepare_marker_cases.py
```

```powershell
./experiments/run_kotlin_oracle.ps1 -SourceName MarkerOracle.kt -EntryPoint MarkerOracleKt
```

Run the comparison from the runtime directory in WSL/Linux:

```sh
cd .work/runtime/lib/koreader
./luajit ../../../../experiments/check_marker_cases.lua
```

This uses the existing Desktop detector/navigator for titles, UTF-16 offsets, and
subdivision points, including long headings and Unicode trim. Hash headings have
no custom-regex length guard. Results are under `.work/results/focused/`.
The separate ChapterOracle/inspect_chapter_regex diagnostic records native
ECMAScript/Kotlin dialect differences; it is not a compatibility test or product
dependency. Arbitrary regex portability is outside the currently requested marker.

## File hash, PKCE, and path-key characterization

Prepare synthetic vectors in WSL/Linux, then generate existing Desktop expectations
on Windows:

```sh
python3 experiments/prepare_contract_cases.py
```

```powershell
./experiments/run_kotlin_oracle.ps1 -SourceName ContractOracle.kt -EntryPoint ContractOracleKt
```

From the runtime directory in WSL/Linux:

```sh
./luajit ../../../../experiments/check_contract_primitives.lua
```

The hash and PKCE checks compare Lua, Kotlin, and Python results. Path rows are
observations: neither the bundled default casefold nor simple lowercase is assumed
to match JVM full lowercase. This uses no account credentials or remote requests.
Results are under `.work/results/finish/`. The loopback TLS and logical-EOF sketches
in private research results are feasibility probes, not shipped product clients.

## Prepared sparse position index

```sh
python3 experiments/run_sparse_probe.py --cpu-percent 5
```

This separate experiment uses the pinned Linux runtime, synthetic inputs, an interpreter,
and a systemd user CPU quota. It never contacts services or updates the product plugin.
Reports and input/code SHA-256 manifests are written under `.work/results/sparse-probe/`.

The prototype validates the complete source/DOM correspondence once with the current mapper,
then saves source byte/UTF-16/node-count checkpoints roughly every 4 KiB. Reloading checks the
index digest, whole-source content hash, and rendering binding. A prepared conversion reads at
most 6,148 source bytes; reverse conversion may read that window twice. Canonical paths reuse
the validated correspondence instead of traversing all preceding DOM siblings on each query.

In the 5% CPU run, the 8 MiB index was 59,223 bytes. Among 84 distributed source conversions,
the maximum was 94.99 ms; reverse conversion maximum was 91.45 ms. Twelve native page anchors
had a maximum of 97.07 ms. All 746 checks passed with zero UTF-16 or UTF-8-byte error on the
supported inputs. These are measured PC samples with a potentially warm OS cache, not a
Kindle latency guarantee or a proof for every input and scheduler condition.

Preparation still took 43.32 seconds, and index reload plus full source verification took
0.40 seconds for 8 MiB. Preparation includes the old full mapper and must not block reading
in any future integration. Source-only indexing during download has not been established as
a replacement for full DOM validation. Lines over 1,024 bytes and transformed/split nodes are
explicitly held. Changed rendering invalidates this conservative prototype. The existing
body-hash guard is still required before enqueue/transmission; bounded queries do not rehash
the whole file each time. No approximate offsets are sent, and no second reading anchor or
chapter list is persisted. The plugin has not been switched to this experiment.

## Interactive no-index position experiment

```powershell
.\FloNovel-koreader\tools\start_emulator.ps1 -NoIndex
```

The launcher patches only its synthetic emulator copy using `no_index_position.lua`.
Open `no-index-8MiB.txt`. Position capture searches a unique native text element in the
source and uses Linux libc iconv to count the preceding UTF-16 units without an index.
Repeated full-line matches and non-text/unsupported anchors are held rather than guessed.
The normal 10-second idle checkpoint performs search, body verification, enqueue and flush
in the existing cancellable subprocess. Reader close can still perform a synchronous checkpoint.
The initial remote fetch does no full mapping; nonzero targets are resolved in a worker and
checked against the native text before a jump is offered. Chapter navigation still uses the
original full mapper on demand. This overlay is restricted to the synthetic emulator account
and library, and is not loaded by the Kindle plugin or deployment allowlist.

To reproduce the actual ReaderUI test with a 5% CPU quota:

```sh
python3 experiments/run_no_index_ui.py
```

The test replaces `ensureMap` with a failure, checks native capture and both position directions,
uses the local mock server, and measures event-loop heartbeats during a background checkpoint.
Reports are stored in `.work/emulator/no-index-smoke/`. It does not establish Kindle iconv
availability, a universal one-second bound, or support for ambiguous and blank-line anchors.
