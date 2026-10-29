"""Run the real Linux KOReader GUI with synthetic books and an isolated local sync profile."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


def prepare(project, profile):
    if not profile or any(c not in 'abcdefghijklmnopqrstuvwxyz0123456789-' for c in profile):
        raise ValueError('Profile names may contain lowercase letters, digits and hyphens only')
    home = project / '.work/emulator' / profile
    for component in (home, *home.parents):
        if component == project.parent:
            break
        if component.is_symlink():
            raise RuntimeError('Linked emulator directories are unsupported')
    home.mkdir(parents=True, exist_ok=True)
    if any(p.is_symlink() for p in home.rglob('*')):
        raise RuntimeError('Linked emulator profile contents are unsupported')
    spec = importlib.util.spec_from_file_location('flonovel_package', project / 'tools/package.py')
    packaging = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(packaging)
    for name in packaging.FILES:
        source = project / 'flonovel.koplugin' / name
        destination = home / 'plugins/flonovel.koplugin' / name
        if source.is_symlink() or destination.is_symlink():
            raise RuntimeError('Linked plugin files are unsupported')
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
    library = home / 'library'
    library.mkdir(exist_ok=True)
    paragraph = 'Synthetic reading performance sample. 정확한 원문 위치와 챕터를 검증합니다. 😀\n\n'
    for label, size in [('128KiB', 128 * 1024), ('2MiB', 2 * 1024**2), ('8MiB', 8 * 1024**2)]:
        target = library / ('synthetic-' + label + '.txt')
        if target.exists():
            continue
        chapters = []
        count = max(3, size // (32 * 1024))
        repetitions = max(1, size // count // len(paragraph.encode('utf-8')))
        for index in range(count):
            chapters.append(f'## Synthetic chapter {index + 1}\n\n' + paragraph * repetitions)
        target.write_text(''.join(chapters), encoding='utf-8', newline='')
    settings = home / 'settings.reader.lua'
    if not settings.exists():
        settings.write_text('return {\n'
                            ' ["language"] = "en", ["debug"] = true,\n'
                            ' ["document_metadata_folder"] = "doc",\n'
                            ' ["font_face"] = "FreeSans", ["followed_link_marker"] = false,\n'
                            ' ["home_dir"] = ' + json.dumps(str(library), ensure_ascii=False) + ',\n'
                            ' ["flonovel"] = { chapters=true, position=true, files=false, divisions=4 },\n'
                            '}\n', encoding='utf-8')
    connection = home / 'settings/flonovel/config.json'
    connection.parent.mkdir(parents=True, exist_ok=True)
    config = dict(version=1, account_id='synthetic-emulator', app_key='synthetic-app',
                  library_root=str(library), position_verified=True,
                  supabase_url='https://emulator.invalid', publishable_key='synthetic-key',
                  shared_secret='synthetic-emulator-secret', dev=True)
    if connection.exists() and json.loads(connection.read_text()) != config:
        raise RuntimeError('This profile contains an unexpected connection; choose a new profile')
    if not connection.exists():
        connection.write_text(json.dumps(config, indent=2) + '\n')
    bootstrap = home / 'emulator.lua'
    bootstrap.write_text('''require("setupkoenv")
-- All service calls in this GUI profile stay in a synthetic local transport.
package.preload["flonovel.http"] = function()
    local path = assert(os.getenv("KO_HOME")) .. "/synthetic-server.json"
    return {
        request = function() return nil, "emulator_dropbox_not_configured" end,
        json = function(method, url, _, body)
            local base = "https://emulator.invalid/rest/v1/flonovel_sync"
            if url ~= base and url:sub(1, #base + 1) ~= base .. "?" then
                return nil, "emulator_external_service_disabled"
            end
            local State = require("flonovel.state")
            local rows, reason = State.read(path)
            if not rows then
                if reason ~= "missing" then return nil, reason end
                rows = {}
            end
            if method == "GET" then
                local encoded = url:match("relative_path=eq%.(.*)$")
                local key = encoded and require("socket.url").unescape(encoded)
                -- A zero row exercises initial mapping even before a first push.
                return {{ char_offset = rows[key] or 0, source = "emulator", encoding = "UTF-8" }}
            elseif method == "POST" and type(body) == "table" then
                rows[body.relative_path] = math.max(rows[body.relative_path] or 0, body.char_offset)
                local ok, failure = State.write(path, rows)
                if not ok then return nil, failure end
                return {}
            end
            return nil, "emulator_unsupported_request"
        end,
    }
end
dofile("reader.lua")
''', encoding='utf-8')
    return home, library, bootstrap


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path)
    parser.add_argument('--profile', default='desktop')
    parser.add_argument('--jit', action='store_true', help='Enable JIT instead of the slower interpreter stress mode')
    parser.add_argument('--width', type=int, default=600)
    parser.add_argument('--height', type=int, default=800)
    parser.add_argument('--prepare-only', action='store_true')
    parser.add_argument('--cpu-percent', type=int, default=5,
                        help='CPU quota as a percentage of one core (1-100); 0 disables the limit')
    args = parser.parse_args()
    if sys.platform != 'linux':
        parser.error('Run through WSL/Linux; Windows users can use start_emulator.ps1')
    project = Path(__file__).resolve().parents[1]
    runtime = (args.runtime or project / '.work/runtime/lib/koreader').resolve()
    if (runtime / 'git-rev').read_text().strip() != 'v2026.07.1':
        parser.error('Use the official Linux KOReader v2026.07.1 runtime')
    if not (320 <= args.width <= 2400 and 480 <= args.height <= 3200):
        parser.error('Invalid emulator dimensions')
    if not 0 <= args.cpu_percent <= 100:
        parser.error('CPU percentage must be between 0 and 100')
    if args.cpu_percent and not shutil.which('systemd-run'):
        parser.error('CPU limiting requires a running systemd user manager; use --cpu-percent 0 to disable it')
    import fcntl
    profile_root = project / '.work/emulator'
    profile_root.mkdir(parents=True, exist_ok=True)
    # Lock before copying code or touching an existing GUI profile.
    if not args.profile or any(c not in 'abcdefghijklmnopqrstuvwxyz0123456789-' for c in args.profile):
        parser.error('Invalid profile name')
    with (profile_root / (args.profile + '.lock')).open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            parser.error('This emulator profile is already running')
        home, library, bootstrap = prepare(project, args.profile)
        print('Profile:', home, flush=True)
        print('Transport: local synthetic Supabase; Dropbox covered by the offline product gate', flush=True)
        print('Runtime: real Linux KOReader v2026.07.1; Kindle hardware still requires device validation', flush=True)
        if args.prepare_only:
            return
        env = dict(os.environ, KO_HOME=str(home), EMULATE_READER_W=str(args.width),
                   EMULATE_READER_H=str(args.height))
        env.pop('KO_MULTIUSER', None)
        command = [str(runtime / 'luajit')]
        if not args.jit:
            command.append('-joff')
        command.extend([str(bootstrap), str(library)])
        if args.cpu_percent:
            # A kernel quota includes forked workers without blocking idle input
            # handling with an artificial stop/resume timer.
            command = ['systemd-run', '--user', '--scope', '--quiet',
                       '--unit=flonovel-emulator-' + args.profile + '.scope',
                       '--property=CPUQuota=' + str(args.cpu_percent) + '%',
                       '--property=CPUQuotaPeriodSec=100ms', '--', *command]
        print('CPU quota:', str(args.cpu_percent) + '% of one core' if args.cpu_percent else 'unlimited', flush=True)
        log = home / 'emulator.log'
        print('GUI log:', log, flush=True)
        with log.open('a') as output:
            result = subprocess.run(command, cwd=runtime, env=env, stdout=output, stderr=subprocess.STDOUT)
        raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
