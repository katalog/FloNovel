"""Compare independently generated expectations with actual Desktop Kotlin output."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / ".work" / "results" / "position-probe"
manifest_bytes = (root / "manifest.json").read_bytes()
manifest = json.loads(manifest_bytes)
oracle = json.loads((root / "kotlin-oracle.json").read_bytes())
tests = [{"name": "oracle_matches_manifest", "passed": oracle["manifest_sha256"] == hashlib.sha256(manifest_bytes).hexdigest()}]
actual_by_name = {fixture["name"]: fixture for fixture in oracle["fixtures"]}
for fixture in manifest["fixtures"]:
    actual = actual_by_name[fixture["name"]]
    for key in ("text", "utf16_units", "samples"):
        tests.append({"name": fixture["name"] + "/" + key, "passed": fixture[key] == actual[key]})
report = {"total": len(tests), "failed": sum(not test["passed"] for test in tests), "tests": tests}
report["passed"] = report["total"] - report["failed"]
(root / "kotlin-parity-results.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(f"KOTLIN_PARITY total={report['total']} passed={report['passed']} failed={report['failed']}")
raise SystemExit(1 if report["failed"] else 0)
