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
import re

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
native_lines = ['## Native page anchors', '']
for index in range(1, 151):
    native_lines.extend([f'Sample {index:03d}: 정확한 원문 위치 😀 remains stable while turning pages.', ['', ' \t', '  '][index % 3]])
native_source = '\n'.join(native_lines) + '\n'
native_cases = []
for name, source in [('native-lf', native_source),
                     ('native-crlf', '\ufeff' + native_source.replace('\n', '\r\n')),
                     ('native-leading-trailing', '\n\n' + native_source + '\n\n')]:
    (session / (name + '.txt')).write_bytes(source.encode('utf-8'))
    decoded = source.removeprefix('\ufeff')
    cursor, units, pres, empties = 0, 0, [], []
    while cursor < len(decoded):
        delimiter = re.search(r'\r\n|\n\r|[\r\n]', decoded[cursor:])
        ending = cursor + delimiter.start() if delimiter else len(decoded)
        line = decoded[cursor:ending]
        if line.strip(' \t'):
            offsets = [units]
            for char in line:
                offsets.append(offsets[-1] + len(char.encode('utf-16-le')) // 2)
            pres.append(offsets)
        else:
            empties.append(units)
        units += len(line.encode('utf-16-le')) // 2
        if not delimiter:
            break
        units += len(delimiter.group())
        cursor = ending + len(delimiter.group())
    native_cases.append(dict(name=name, pres=pres, empties=empties, length=units))
(session / 'native-anchors.json').write_text(json.dumps(native_cases, ensure_ascii=False), encoding='utf-8')
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
