"""Offline behavior checks for the one-time authorization helper."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('flonovel_connect', Path(__file__).resolve().parents[1] / 'tools/connect.py')
connect = importlib.util.module_from_spec(spec)
spec.loader.exec_module(connect)


class ConnectTest(unittest.TestCase):
    def test_rfc_pkce_vector(self):
        self.assertEqual(connect.pkce('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'),
                         'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM')

    def test_private_configuration_roundtrip(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'config.json'
            data = dict(refresh_token='synthetic', account_id='synthetic-account', version=1)
            connect.write_private(path, data)
            self.assertEqual(json.loads(path.read_text()), data)
            if os.name == 'posix':
                self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_existing_configuration_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'config.json'
            path.write_text('existing')
            with self.assertRaises(RuntimeError):
                connect.write_private(path, {})
            self.assertEqual(path.read_text(), 'existing')

    def test_required_scopes(self):
        self.assertEqual(set(connect.SCOPES.split()), {
            'account_info.read', 'files.metadata.read', 'files.content.read', 'files.content.write'})

    def test_redirect_handler_denies_forwarding(self):
        self.assertIsNone(connect.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://example.invalid'))

    def test_only_public_configuration_is_imported(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'local.properties'
            path.write_text('DROPBOX_APP_KEY=synthetic-app\nSUPABASE_URL=https\\://synthetic.invalid\n'
                            'SUPABASE_PUBLISHABLE_KEY=synthetic-public\nDROPBOX_APP_SECRET=never-import\n'
                            'REFRESH_TOKEN=never-import\n')
            self.assertEqual(connect.public_properties(path), dict(DROPBOX_APP_KEY='synthetic-app',
                             SUPABASE_URL='https://synthetic.invalid', SUPABASE_PUBLISHABLE_KEY='synthetic-public'))


if __name__ == '__main__':
    unittest.main()
