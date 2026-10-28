"""Exercise plugin failure isolation with synthetic inputs and a fresh KO_HOME."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile


def main() -> None:
    project = Path(__file__).resolve().parents[1]
    runtime = project / ".work/runtime/lib/koreader"
    if (runtime / "git-rev").read_text().strip() != "v2026.07.1":
        raise SystemExit("Unexpected runtime version")
    results = project / ".work/results/a2"
    results.mkdir(parents=True, exist_ok=True)
    session = Path(tempfile.mkdtemp(prefix="session-", dir=results))
    inputs, plugins = session / "inputs", session / "plugins"
    inputs.mkdir()
    plugins.mkdir()
    text = "## Synthetic lifecycle fixture\n" + "".join(
        f"Row {index:03}: Reader startup and ordinary navigation must survive plugin failures.\n"
        for index in range(140)
    )
    fixture = inputs / "lifecycle.txt"
    fixture.write_text(text, encoding="utf-8")
    original_hash = hashlib.sha256(fixture.read_bytes()).hexdigest()
    base = 'local W = require("ui/widget/container/widgetcontainer"); local P = W:extend{is_doc_only=true}\n'
    sources = {
        "a2_healthy.koplugin": base + '''
function P:init() self.calls = 0 end
function P:onReaderReady() self.ready = true end
function P:onA2Healthy() self.calls = self.calls + 1; return true end
return P
''',
        "a2_handler_error.koplugin": base + '''
function P:onReaderReady() error("A2_SYNTHETIC_READY_FAILURE") end
function P:onA2InjectedFault() error("A2_SYNTHETIC_HANDLER_FAILURE") end
return P
''',
        "a2_init_error.koplugin": base + 'function P:init() error("A2_SYNTHETIC_INIT_FAILURE") end\nreturn P\n',
        "a2_load_error.koplugin": 'error("A2_SYNTHETIC_LOAD_FAILURE")\n',
        "a2_syntax_error.koplugin": 'return { deliberately broken syntax\n',
        "a2_disabled.koplugin": '_G.a2_disabled_executed = true; error("A2_DISABLED_MAIN_MUST_NOT_RUN")\n',
        "a2_removed.koplugin.disabled": 'error("A2_RENAMED_PLUGIN_MUST_NOT_LOAD")\n',
    }
    for directory, source in sources.items():
        folder = plugins / directory
        folder.mkdir()
        (folder / "main.lua").write_text(source, encoding="utf-8")
        (folder / "_meta.lua").write_text(
            'return {fullname="' + directory + '", description="Synthetic offline lifecycle check"}\n',
            encoding="utf-8",
        )
    environment = os.environ.copy()
    environment.update(KO_HOME=str(session), SDL_VIDEODRIVER="dummy", SDL_AUDIODRIVER="dummy")
    log = results / "process.log"
    with log.open("w", encoding="utf-8") as output:
        run = subprocess.run(
            [str(runtime / "luajit"), str(project / "experiments/check_lifecycle.lua")],
            cwd=runtime, env=environment, stdout=output, stderr=subprocess.STDOUT,
        )
    if run.returncode:
        raise SystemExit("Lifecycle probe failed; inspect .work/results/a2/process.log")
    report = json.loads((results / "lifecycle-results.json").read_text())
    unchanged = hashlib.sha256(fixture.read_bytes()).hexdigest() == original_hash
    report["tests"].append({"name": "synthetic_source_bytes_unchanged", "passed": unchanged})
    report["total"] += 1
    report["passed"] += int(unchanged)
    report["failed"] += int(not unchanged)
    report["session"] = session.name
    report["runtime"] = "v2026.07.1"
    report["source_sha256"] = original_hash
    report["skip"] = 0
    (results / "lifecycle-results.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"A2_LIFECYCLE_CHECKS total={report['total']} passed={report['passed']} failed={report['failed']}")
    if report["failed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
