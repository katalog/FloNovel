"""Run the synthetic engine smoke checks inside WSL/Linux using KOReader's LuaJIT."""

import json
from pathlib import Path
import subprocess


def main() -> None:
    project = Path(__file__).resolve().parents[1]
    runtime = project / ".work" / "runtime" / "lib" / "koreader"
    results = project / ".work" / "results" / "engine-smoke"
    if not (runtime / "luajit").is_file():
        raise SystemExit("Extract the verified official Linux runtime under .work/runtime first.")
    revision = (runtime / "git-rev").read_text(encoding="utf-8").strip()
    if revision != "v2026.07.1":
        raise SystemExit(f"Unexpected runtime: {revision}")
    (results / "cache").mkdir(parents=True, exist_ok=True)
    text = "## Synthetic chapter\nAlpha 가😀나\n\nBeta\tGamma\n"
    (results / "synthetic.txt").write_bytes(text.encode("utf-8"))
    metadata = {
        "runtime": revision,
        "utf8_bytes": len(text.encode("utf-8")),
        "utf16_units": len(text.encode("utf-16-le")) // 2,
        "codepoints": len(text),
        "scope": "engine_smoke_only_not_position_mapping_or_device_validation",
    }
    (results / "fixture.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    subprocess.run(
        [str(runtime / "luajit"), str(project / "experiments" / "engine_smoke.lua")],
        cwd=runtime,
        check=True,
    )
    report = json.loads((results / "results.json").read_text(encoding="utf-8"))
    if report["total"] == 0 or report["failed"] != 0:
        raise SystemExit("The engine checks did not complete successfully.")


if __name__ == "__main__":
    main()
