"""Copy public parity goldens and generate large synthetic text; never use user books."""
import hashlib
import json
import os
from pathlib import Path
import subprocess

from run_position_probe import reference_samples


def main() -> None:
    project = Path(__file__).resolve().parents[1]
    repository = project.parent
    desktop = repository / "FloNovel-desktop/src/test/resources/fixtures/parity"
    android = repository / "FloNovel-android/app/src/test/resources/fixtures/parity"
    runtime = project / ".work/runtime/lib/koreader"
    root = project / ".work/results/scale-probe"
    (root / "cache").mkdir(parents=True, exist_ok=True)
    fixtures, checks = [], []
    sources = sorted(desktop.glob("*.expected.txt"))
    if not sources:
        raise SystemExit("No existing golden fixtures found.")
    for path in sources:
        raw = path.read_bytes()
        equal = raw == (android / path.name).read_bytes()
        checks.append({"name": path.name + "/cross_app_bytes", "passed": equal})
        if not equal:
            raise SystemExit("Existing golden copies disagree: " + path.name)
        text = raw.decode("utf-8").removeprefix("\ufeff")
        samples, _, hidden = reference_samples(text)
        (root / path.name).write_bytes(raw)
        fixtures.append({"name": path.name, "sha256": hashlib.sha256(raw).hexdigest(),
            "text": text, "utf16_units": len(text.encode("utf-16-le")) // 2,
            "samples": samples, "hidden_offsets": hidden})

    large = []
    for target in (128 * 1024, 1024 * 1024, 4 * 1024 * 1024):
        # Every line has the same byte/unit lengths despite its varying row number.
        line = "Row 000000: Source mapping 가😀나 must preserve the original position. " * 2 + "\n"
        header = "## Synthetic scale fixture\n"
        rows = (target - len(header.encode("utf-8"))) // len(line.encode("utf-8")) + 1
        parts = [header]
        sample_rows = {0, rows // 4, rows // 2, rows * 3 // 4, rows - 1}
        units, samples = len(header), []
        for index in range(rows):
            current = line.replace("000000", f"{index:06d}")
            if index in sample_rows:
                for at in (0, current.index("가"), current.index("😀"), len(current) - 2):
                    samples.append({"offset": units + len(current[:at].encode("utf-16-le")) // 2,
                        "character": current[at]})
            units += len(current.encode("utf-16-le")) // 2
            parts.append(current)
        text = "".join(parts)
        name = f"synthetic_{target}.txt"
        raw = text.encode("utf-8")
        (root / name).write_bytes(raw)
        large.append({"name": name, "bytes": len(raw), "utf16_units": units,
            "sha256": hashlib.sha256(raw).hexdigest(), "samples": samples})

    cache_text = "## Cache fixture\nBefore 가😀나\nTail"
    (root / "cache_a.txt").write_bytes(cache_text.encode("utf-8"))
    (root / "cache_b.txt").write_bytes(cache_text.replace("Before", "Xefore").encode("utf-8"))
    # DrvFS can round subsecond values differently on separate writes.
    stamp = 1_700_000_000_000_000_000
    for name in ("cache_a.txt", "cache_b.txt"):
        os.utime(root / name, ns=(stamp, stamp))
    cache_stat = (root / "cache_a.txt").stat()
    checks.append({"name": "cache_fixture_same_size_mtime", "passed":
        cache_stat.st_size == (root / "cache_b.txt").stat().st_size and
        cache_stat.st_mtime_ns == (root / "cache_b.txt").stat().st_mtime_ns})
    manifest = {"runtime": (runtime / "git-rev").read_text().strip(), "goldens": fixtures,
        "large": large, "cache_text": cache_text, "source_checks": checks}
    if manifest["runtime"] != "v2026.07.1":
        raise SystemExit("Unexpected runtime version.")
    (root / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    subprocess.run([str(runtime / "luajit"), str(project / "experiments/check_scale.lua")], cwd=runtime, check=True)
    subprocess.run([str(runtime / "luajit"), str(project / "experiments/check_map_cache.lua")], cwd=runtime, check=True)
    # Detect accidental mutations in the shared golden inputs after the engine run.
    for fixture in fixtures:
        for directory in (desktop, android):
            if hashlib.sha256((directory / fixture["name"]).read_bytes()).hexdigest() != fixture["sha256"]:
                raise SystemExit("Original golden fixture changed: " + fixture["name"])


if __name__ == "__main__":
    main()
