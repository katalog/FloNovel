"""Check isolation and repeatable setup of the synthetic GUI profile."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

PROJECT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('flonovel_emulator', PROJECT / 'tools/run_emulator.py')
emulator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(emulator)
spec = importlib.util.spec_from_file_location('flonovel_package_test', PROJECT / 'tools/package.py')
packaging = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packaging)


class EmulatorTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.project = Path(self.directory.name)
        (self.project / 'tools').mkdir()
        shutil.copyfile(PROJECT / 'tools/package.py', self.project / 'tools/package.py')
        for name in packaging.FILES:
            target = self.project / 'flonovel.koplugin' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text('-- Synthetic product file\n')

    def test_isolated_synthetic_profile(self):
        home, library, bootstrap = emulator.prepare(self.project, 'test')
        config = json.loads((home / 'settings/flonovel/config.json').read_text())
        self.assertEqual(config['library_root'], str(library))
        self.assertEqual(config['supabase_url'], 'https://emulator.invalid')
        self.assertNotIn('refresh_token', config)
        self.assertTrue(bootstrap.is_file())
        installed = home / 'plugins/flonovel.koplugin'
        self.assertEqual({p.relative_to(installed).as_posix() for p in installed.rglob('*.lua')}, set(packaging.FILES))
        text = (library / 'synthetic-2MiB.txt').read_text(encoding='utf-8')
        self.assertGreater(len(text.encode('utf-8')), 2_000_000)
        self.assertIn('## Synthetic chapter 2', text)
        self.assertIn('😀', text)

    def test_relaunch_preserves_settings_books_and_pending_state(self):
        home, library, _ = emulator.prepare(self.project, 'test')
        settings = home / 'settings.reader.lua'
        settings.write_text('return { custom=true }')
        book = library / 'synthetic-2MiB.txt'
        book.write_text('Existing synthetic content')
        pending = home / 'settings/flonovel/positions.json'
        pending.write_text('{"synthetic":"pending"}')
        emulator.prepare(self.project, 'test')
        self.assertEqual(settings.read_text(), 'return { custom=true }')
        self.assertEqual(book.read_text(), 'Existing synthetic content')
        self.assertEqual(pending.read_text(), '{"synthetic":"pending"}')

    def test_unexpected_connection_is_rejected_and_preserved(self):
        home, _, _ = emulator.prepare(self.project, 'test')
        path = home / 'settings/flonovel/config.json'
        path.write_text('{"account_id":"another-synthetic-profile"}')
        with self.assertRaises(RuntimeError):
            emulator.prepare(self.project, 'test')
        self.assertEqual(path.read_text(), '{"account_id":"another-synthetic-profile"}')

    def test_profile_traversal_rejected(self):
        for name in ['../outside', '/outside', 'nested/path', '']:
            with self.assertRaises(ValueError):
                emulator.prepare(self.project, name)
