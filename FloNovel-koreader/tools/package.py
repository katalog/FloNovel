"""Build a development installation ZIP using an explicit product allowlist."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

FILES = ('main.lua', '_meta.lua', 'flonovel/position_map.lua', 'flonovel/chapters.lua',
         'flonovel/strings.lua', 'flonovel/case_table.lua', 'flonovel/paths.lua',
         'flonovel/state.lua', 'flonovel/content_hash.lua', 'flonovel/http.lua',
         'flonovel/tls.lua', 'flonovel/dropbox.lua', 'flonovel/reading_position.lua',
         'flonovel/fast_position.lua')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--results', type=Path, required=True, help='Successful product summary.json')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.results.read_text())
    if report.get('total', 0) < 1 or report.get('failed') != 0 or report.get('skipped') != 0:
        parser.error('A nonempty, fully passing gate is required')
    project = Path(__file__).resolve().parents[1]
    source = project / 'flonovel.koplugin'
    args.output.parent.mkdir(parents=True, exist_ok=True)
    manifest = {}
    with zipfile.ZipFile(args.output, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name in FILES:
            path = source / name
            if path.is_symlink() or not path.is_file():
                raise RuntimeError('Missing or linked allowlisted product file: ' + name)
            data = path.read_bytes()
            if report.get('product_sha256', {}).get(name) != hashlib.sha256(data).hexdigest():
                raise RuntimeError('Product changed since the last passing gate: ' + name)
            if b'.work/' in data or b'../../../../experiments/' in data:
                raise RuntimeError('Product depends on private research: ' + name)
            manifest['flonovel.koplugin/' + name] = hashlib.sha256(data).hexdigest()
            archive.writestr('flonovel.koplugin/' + name, data)
    with zipfile.ZipFile(args.output) as archive:
        assert set(archive.namelist()) == set(manifest)
        assert archive.testzip() is None
        for name, expected in manifest.items():
            assert hashlib.sha256(archive.read(name)).hexdigest() == expected
    args.output.with_suffix('.manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'Development package verified: {len(manifest)} files; SHA-256 {hashlib.sha256(args.output.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    main()
