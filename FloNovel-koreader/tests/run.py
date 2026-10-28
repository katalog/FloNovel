"""Run product tests with an existing Linux KOReader installation (no device writes)."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import json
import https_fixture
import unittest
import hashlib

parser = argparse.ArgumentParser()
parser.add_argument('--runtime', type=Path, required=True)
parser.add_argument('--results', type=Path, required=True)
args = parser.parse_args()
if (args.runtime / 'git-rev').read_text().strip() != 'v2026.07.1':
    parser.error('This gate is pinned to KOReader v2026.07.1')
project = Path(__file__).resolve().parents[1]
args.results.mkdir(parents=True, exist_ok=True)
session = Path(tempfile.mkdtemp(prefix='reader-', dir=args.results.resolve()))
text = '## Chapter A\n' + ('abcdefghijklmnopqrstuvwxyz\n' * 40)
text += '## ' + ('Long chapter title ' * 8) + '\n' + ('More text\n' * 40)
(session / 'book.txt').write_text(text, encoding='utf-8')
(session / 'second.txt').write_text('## Emoji\n😀😀😀😀\nEnd', encoding='utf-8')
env = dict(os.environ, KO_HOME=str(session), FLONOVEL_PROJECT=str(project),
           SDL_VIDEODRIVER='dummy', SDL_AUDIODRIVER='dummy')
subprocess.run([str(args.runtime.resolve() / 'luajit'), str(project / 'tests/check_reader.lua')],
               cwd=args.runtime, env=env, check=True)
subprocess.run([str(args.runtime.resolve() / 'luajit'), str(project / 'tests/check_sync.lua')],
               cwd=args.runtime, env=env, check=True)
subprocess.run([str(args.runtime.resolve() / 'luajit'), str(project / 'tests/check_position.lua')],
               cwd=args.runtime, env=env, check=True)
subprocess.run([str(args.runtime.resolve() / 'luajit'), str(project / 'tests/check_parity.lua')],
               cwd=args.runtime, env=env, check=True)
https_fixture.run(args.runtime, project, session, env)
suite = unittest.defaultTestLoader.discover(str(project / 'tests'), pattern='test_*.py')
helper_result = unittest.TextTestRunner(verbosity=2).run(suite)
if not helper_result.wasSuccessful() or helper_result.testsRun == 0:
    raise SystemExit('Host helper tests failed or did not run')
reports = [json.loads((session / name).read_text()) for name in
           ('results.json', 'sync-results.json', 'position-results.json', 'parity-results.json', 'https-results.json')]
summary = dict(total=sum(r['total'] for r in reports), passed=sum(r['passed'] for r in reports),
               failed=sum(r['failed'] for r in reports), skipped=sum(r['skipped'] for r in reports),
               runtime='v2026.07.1', device_tested=False, live_services_tested=False)
summary['total'] += helper_result.testsRun
summary['passed'] += helper_result.testsRun - len(helper_result.skipped)
summary['skipped'] += len(helper_result.skipped)
summary['host_helper_tests'] = helper_result.testsRun
summary['product_sha256'] = {str(p.relative_to(project / 'flonovel.koplugin')).replace(os.sep, '/'):
                            hashlib.sha256(p.read_bytes()).hexdigest()
                            for p in sorted((project / 'flonovel.koplugin').rglob('*.lua'))}
(session / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print('PRODUCT_TOTAL', summary)
print('Results:', session / 'results.json')
