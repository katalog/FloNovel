"""Generate synthetic cases and inspect them using the pinned, actual TXT engine."""

import hashlib
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


CASES = {
    "unicode_lf": "## Chapter\nAlpha 가😀나\n\nBeta\tGamma\n",
    "unicode_crlf": "## Chapter\r\nAlpha 가😀나\r\n\r\nBeta\tGamma\r\n",
    "unicode_bom": "\ufeff## Chapter\nAlpha 가😀나\n",
    "whitespace": "  Heading  \n \t \n\tIndented　text  \n\nTail",
    "repeated": "## Chapter\nRepeated line\nRepeated line\n\nRepeated line\n",
    "separators": "## Chapter\nBefore\n-----\n***\n===\nAfter\n",
    "long_line": "## Chapter\n" + "abcd " * 450 + "End\nTail\n",
    "long_cjk": "## Chapter\n" + "가" * 2200 + "😀End\nTail\n",
    "entities": "## Chapter\n<tag> &amp; <>& text\nTail",
    "no_heading": "Alpha 가😀나\nBeta\n",
    "empty": "",
    "blank": "\n \t\n\n",
    "controls": "## Chapter\nBefore\x14Title\x15\nAfter\n",
    "libru_header": "## Chapter\n\x14Hidden title\x15\nAfter\n",
    "native_separator": "## Chapter\n* * *\nAfter\n",
    "combining": "## Chapter\nCombining e\u0301 가 가\nTail\n",
    "eof_plain": "## Chapter\nLast 가😀나",
    "eof_lf": "## Chapter\nLast 가😀나\n",
    "eof_crlf": "## Chapter\r\nLast 가😀나\r\n",
    "separators_unicode": "## Chapter 😀\nBefore 가\n-----\nAfter\n",
    "libru_header_unicode": "## Chapter 😀\n \t\x14숨긴 제목😀\x15\nAfter\n",
}


def reference_samples(text: str) -> tuple[list[dict], list[int], list[int]]:
    samples, interiors, hidden = [], [], []
    units = 0
    for part in re.split(r"(\r\n|\n\r|\r|\n)", text):
        visible = part not in ("\r\n", "\n\r", "\r", "\n") and bool(part.strip(" \t"))
        for index, character in enumerate(part):
            if visible and (len(part) < 100 or index % 97 == 0 or ord(character) > 0xFFFF or index == len(part) - 1):
                samples.append({"offset": units, "character": character})
            elif not visible:
                hidden.append(units)
            width = len(character.encode("utf-16-le")) // 2
            if width == 2:
                interiors.append(units + 1)
            units += width
    return samples, interiors, hidden


def main() -> None:
    parser = argparse.ArgumentParser()
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--prepare-only", action="store_true")
    modes.add_argument("--checks-only", action="store_true")
    parser.add_argument("--require-kotlin-oracle", action="store_true")
    arguments = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    runtime = project / ".work" / "runtime" / "lib" / "koreader"
    results = project / ".work" / "results" / "position-probe"
    (results / "cache").mkdir(parents=True, exist_ok=True)
    revision = (runtime / "git-rev").read_text(encoding="utf-8").strip()
    if revision != "v2026.07.1":
        raise SystemExit(f"Unexpected runtime: {revision}")
    fixtures = []
    for name, text in CASES.items():
        raw = text.encode("utf-8")
        if not arguments.checks_only:
            (results / f"{name}.txt").write_bytes(raw)
        # Match the Desktop loader's removal of one leading BOM.
        decoded = text.removeprefix("\ufeff")
        samples, interiors, hidden = reference_samples(decoded)
        fixtures.append({
            "name": name, "sha256": hashlib.sha256(raw).hexdigest(),
            "text": decoded, "utf16_units": len(decoded.encode("utf-16-le")) // 2,
            "samples": samples, "surrogate_interiors": interiors, "hidden_offsets": hidden,
            "expected_supported": name not in {"empty", "blank"} and not name.startswith(("separators", "libru_header")),
            "expected_rejection": "no_visible_text" if name in {"empty", "blank"} else "parser_changed_text",
            "after_offset": len(decoded[:decoded.rfind("After")].encode("utf-16-le")) // 2 if "After" in decoded else None,
        })
    reader_text = "".join(
        f"## Chapter {chapter}\n" + "".join(
            f"Paragraph {row:03}: " + "The reader position must follow the source text. " * 2 + "\n"
            for row in range(1, 66)
        )
        for chapter in range(1, 5)
    ).rstrip("\n")
    tiny_text = "## Tiny\n" + "x\n" * 21 + "## Last\nTail"
    jump_cases = {
        "twenty_lines": "## First\n" + "x\n" * 19 + "## Last\nTail",
        "twenty_one_lines": "## First\n" + "x\n" * 20 + "## Last\nTail",
        "no_chapters": "No chapter markers\nPlain text",
        "surrogate_division": "## Emoji\n" + "😀" * 6,
    }
    prepared = {"runtime": revision, "fixtures": fixtures, "reader_text": reader_text,
        "tiny_text": tiny_text, "jump_cases": jump_cases}
    if arguments.checks_only:
        if json.loads((results / "manifest.json").read_text(encoding="utf-8")) != prepared:
            raise SystemExit("Prepared fixture manifest is stale; run preparation and the Kotlin oracle again.")
        for fixture in fixtures:
            raw = (results / (fixture["name"] + ".txt")).read_bytes()
            if hashlib.sha256(raw).hexdigest() != fixture["sha256"]:
                raise SystemExit("Prepared fixture contents changed: " + fixture["name"])
        if (results / "reader_long.txt").read_bytes() != reader_text.encode("utf-8"):
            raise SystemExit("Prepared reader fixture changed.")
        if (results / "reader_tiny.txt").read_bytes() != tiny_text.encode("utf-8"):
            raise SystemExit("Prepared tiny reader fixture changed.")
    else:
        (results / "reader_long.txt").write_bytes(reader_text.encode("utf-8"))
        (results / "reader_tiny.txt").write_bytes(tiny_text.encode("utf-8"))
        (results / "manifest.json").write_text(
            json.dumps(prepared, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
        )
    if arguments.prepare_only:
        print("PREPARED_SYNTHETIC_FIXTURES", len(fixtures))
        return
    if arguments.require_kotlin_oracle:
        subprocess.run(["python3", str(project / "experiments" / "check_kotlin_parity.py")], check=True)
    subprocess.run(
        [str(runtime / "luajit"), str(project / "experiments" / "inspect_txt.lua")],
        cwd=runtime, check=True,
    )
    subprocess.run(
        [str(runtime / "luajit"), str(project / "experiments" / "check_positions.lua")],
        cwd=runtime, check=True,
    )
    subprocess.run(
        [str(runtime / "luajit"), str(project / "experiments" / "check_boundaries.lua")],
        cwd=runtime, check=True,
    )
    def session(prefix: str, names: list[str]) -> dict:
        isolated = os.environ.copy()
        folder = Path(tempfile.mkdtemp(prefix=prefix, dir=results))
        (folder / "inputs").mkdir()
        for name in names:
            (folder / "inputs" / name).write_bytes((results / name).read_bytes())
        isolated["KO_HOME"] = str(folder)
        isolated["SDL_VIDEODRIVER"] = "dummy"
        isolated["SDL_AUDIODRIVER"] = "dummy"
        return isolated

    environment = session("ui-session-", ["unicode_lf.txt"])
    environment["SDL_VIDEODRIVER"] = "dummy"
    environment["SDL_AUDIODRIVER"] = "dummy"
    subprocess.run(
        [str(runtime / "luajit"), str(project / "experiments" / "check_reader_ui.lua")],
        cwd=runtime, env=environment, check=True,
    )
    if arguments.require_kotlin_oracle:
        environment = session("long-reader-session-", ["reader_long.txt", "reader_tiny.txt"])
        subprocess.run(
            [str(runtime / "luajit"), str(project / "experiments" / "check_long_reader.lua")],
            cwd=runtime, env=environment, check=True,
        )
    mapping = json.loads((results / "mapping-results.json").read_text(encoding="utf-8"))
    ui = json.loads((results / "reader-ui-results.json").read_text(encoding="utf-8"))
    print(json.dumps({
        "runtime": revision, "fixtures": len(fixtures),
        "mapped_fixtures": sum(document["supported"] for document in mapping["documents"]),
        "engine_checks": mapping["total"], "engine_failures": mapping["failed"],
        "reader_checks": ui["total"], "reader_failures": ui["failed"],
    }))


if __name__ == "__main__":
    main()
