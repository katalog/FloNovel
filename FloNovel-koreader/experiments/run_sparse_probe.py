"""Measure a prepared sparse TXT index with synthetic inputs and the pinned Linux engine."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import uuid


def describe(name, text, unsupported=False):
    source = text.removeprefix('\ufeff')
    cursor, units, byte, pre, empty = 0, 0, len(text.encode()) - len(source.encode()), [], []
    delimiters = re.compile(r'\r\n|\n\r|[\r\n]')
    while cursor < len(source):
        match = delimiters.search(source, cursor)
        ending = match.start() if match else len(source)
        line = source[cursor:ending]
        if line.strip(' \t'):
            pre.append((units, line, byte))
        else:
            empty.append((units, byte))
        units += len(line.encode('utf-16-le')) // 2
        byte += len(line.encode())
        if not match:
            break
        units += len(match[0])
        byte += len(match[0])
        cursor = ending + len(match[0])
    samples = []
    for i in sorted({int((len(pre) - 1) * n / 23) for n in range(24)}):
        start, line, raw = pre[i]
        for point in sorted({0, len(line) // 2, len(line)}):
            samples.append(dict(xp=f'/FictionBook/body/pre[{i + 1}]/text().{point}',
                                offset=start + len(line[:point].encode('utf-16-le')) // 2,
                                byte=raw + len(line[:point].encode())))
    for i in sorted({int((len(empty) - 1) * n / 11) for n in range(12)}) if empty else []:
        samples.append(dict(xp=f'/FictionBook/body/empty-line[{i + 1}].0', offset=empty[i][0], byte=empty[i][1]))
    return dict(name=name, bytes=len(text.encode()), length=units, samples=samples, unsupported=unsupported)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cpu-percent', type=int, default=5)
    args = parser.parse_args()
    if not 0 <= args.cpu_percent <= 100:
        parser.error('CPU percentage must be 0-100')
    project = Path(__file__).resolve().parents[1]
    runtime = project / '.work/runtime/lib/koreader'
    if (runtime / 'git-rev').read_text().strip() != 'v2026.07.1':
        parser.error('Expected the pinned official Linux runtime')
    directory = project / '.work/results/sparse-probe'
    directory.mkdir(parents=True, exist_ok=True)
    home = Path(tempfile.mkdtemp(prefix='run-', dir=directory))
    small = '\n\n## 제1장\n\n' + ('Repeated paragraph. 한글 😀 exact source.\n \t\n' * 80) + '## 제2장\n끝\n\n'
    texts = [('lf', small, False), ('crlf-bom', '\ufeff' + small.replace('\n', '\r\n'), False)]
    for size in [2, 8]:
        lines, count = ['## Synthetic sparse index\n\n'], 0
        pattern = ['Repeated paragraph. 한글 😀 exact source.\n\n',
                   'Variable text ' + '한글 ' * 24 + '\n \t\n', 'Short\n\n']
        while count < size * 1024**2:
            line = pattern[len(lines) % len(pattern)]
            lines.append(line)
            count += len(line.encode())
        texts.append((f'size-{size}mib', ''.join(lines), False))
    texts.extend([('long-line', '## A\n' + 'a' * 2100 + '\nEnd', True),
                  ('transformed', '## A\n\x14Hidden title\x15\nEnd', True)])
    cases = []
    for name, text, unsupported in texts:
        (home / (name + '.txt')).write_bytes(text.encode())
        cases.append(describe(name, text, unsupported))
    (home / 'manifest.json').write_text(json.dumps(cases, ensure_ascii=False))
    metadata = dict(runtime='v2026.07.1', cpu_percent=args.cpu_percent,
                    code_sha256={name: hashlib.sha256((project / 'experiments' / name).read_bytes()).hexdigest()
                                 for name in ['sparse_position_map.lua', 'check_sparse.lua', 'run_sparse_probe.py']},
                    input_sha256={name: hashlib.sha256(text.encode()).hexdigest() for name, text, _ in texts})
    (home / 'run-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
    command = [str(runtime / 'luajit'), '-joff', str(project / 'experiments/check_sparse.lua')]
    if args.cpu_percent:
        command = ['systemd-run', '--user', '--scope', '--quiet',
                   '--unit=flonovel-sparse-' + uuid.uuid4().hex + '.scope',
                   f'--property=CPUQuota={args.cpu_percent}%', '--property=CPUQuotaPeriodSec=100ms', '--', *command]
    env = dict(os.environ, KO_HOME=str(home), FLONOVEL_PROJECT=str(project),
               FLONOVEL_SPARSE_QUOTA=str(args.cpu_percent), SDL_VIDEODRIVER='dummy', SDL_AUDIODRIVER='dummy')
    print('Results:', home, flush=True)
    with (home / 'process.log').open('w') as log:
        process = subprocess.Popen(command, cwd=runtime, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        for line in process.stdout:
            log.write(line)
            log.flush()
            if line.startswith(('SPARSE_', 'FAIL ')):
                print(line.rstrip(), flush=True)
        code = process.wait()
    if code:
        print((home / 'process.log').read_text()[-1800:])
    raise SystemExit(code)


if __name__ == '__main__':
    main()
