"""Compare the optional bulk mapper and exercise it through existing ReaderUI checks."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

project = Path(__file__).resolve().parents[1]
runtime = project / ".work/runtime/lib/koreader"
positions = project / ".work/results/position-probe"
focused = project / ".work/results/focused"
focused.mkdir(parents=True, exist_ok=True)
if (runtime / "git-rev").read_text().strip() != "v2026.07.1":
    raise SystemExit("Unexpected runtime version")
manifest_raw = (positions / "manifest.json").read_bytes()
oracle = json.loads((positions / "kotlin-oracle.json").read_text())
if oracle["manifest_sha256"] != hashlib.sha256(manifest_raw).hexdigest():
    raise SystemExit("Prepare the position fixtures and Kotlin oracle first")
for root, keys in ((positions, ("fixtures",)), (project / ".work/results/scale-probe", ("goldens", "large"))):
    manifest = json.loads((root / "manifest.json").read_text())
    for key in keys:
        for row in manifest[key]:
            name = row["name"] + (".txt" if key == "fixtures" else "")
            if hashlib.sha256((root / name).read_bytes()).hexdigest() != row["sha256"]:
                raise SystemExit("Prepared input hash mismatch: " + name)
subprocess.run([str(runtime / "luajit"), str(project / "experiments/check_bulk_map.lua")], cwd=runtime, check=True)
for script, names in (
    ("check_reader_ui.lua", ["unicode_lf.txt"]),
    ("check_long_reader.lua", ["reader_long.txt", "reader_tiny.txt"]),
):
    session = Path(tempfile.mkdtemp(prefix="bulk-ui-", dir=focused))
    (session / "inputs").mkdir()
    for name in names:
        (session / "inputs" / name).write_bytes((positions / name).read_bytes())
    env = os.environ.copy()
    env.update(KO_HOME=str(session), SDL_VIDEODRIVER="dummy", SDL_AUDIODRIVER="dummy", FLONOVEL_PROBE_BULK="1")
    subprocess.run([str(runtime / "luajit"), str(project / "experiments" / script)], cwd=runtime, env=env, check=True)
paths = [focused / "bulk-results.json", positions / "bulk-reader-ui-results.json", positions / "bulk-long-reader-results.json"]
reports = [json.loads(path.read_text()) for path in paths]
summary = {"scope": "PC_optional_bulk_mapper_and_ReaderUI_not_product_or_Kindle",
           "total": sum(row["total"] for row in reports), "failed": sum(row["failed"] for row in reports),
           "skip": 0, "results": [str(path.relative_to(project)) for path in paths]}
summary["passed"] = summary["total"] - summary["failed"]
(focused / "bulk-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary))
