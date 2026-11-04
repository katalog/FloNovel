"""Authorize this public Dropbox client and create a private KOReader configuration."""
import argparse
import base64
import getpass
import hashlib
import json
import os
from pathlib import Path
import secrets
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import webbrowser

SCOPES = 'account_info.read files.metadata.read files.content.read files.content.write'


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def pkce(verifier):
    return base64.urlsafe_b64encode(hashlib.sha256(verifier.encode('ascii')).digest()).rstrip(b'=').decode('ascii')


def public_properties(path):
    allowed = {'DROPBOX_APP_KEY', 'SUPABASE_URL', 'SUPABASE_PUBLISHABLE_KEY'}
    values = {}
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        key, separator, value = line.partition('=')
        if separator and key.strip() in allowed:
            values[key.strip()] = value.strip().replace('\\:', ':').replace('\\=', '=')
    return values


def request(url, data, headers):
    req = urllib.request.Request(url, data=data, headers=headers, method='POST')
    # urllib's default HTTPS context validates both CA and hostname.
    try:
        with urllib.request.build_opener(NoRedirect()).open(req, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f'HTTP {error.code}; response body hidden to protect credentials') from None
    except (urllib.error.URLError, ValueError):
        raise RuntimeError('Connection, certificate, or response validation failed') from None


def write_private(path, data):
    path = path.expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raise RuntimeError('Output already exists; choose a new private output file')
    descriptor, temporary = tempfile.mkstemp(prefix='.flonovel-', dir=path.parent)
    try:
        with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
            json.dump(data, stream, ensure_ascii=False, indent=2)
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app-key', help='Existing FloNovel Dropbox public app key')
    parser.add_argument('--desktop-properties', type=Path, help='Read only the three public connection values from an existing local.properties; no tokens or app secret')
    parser.add_argument('--library-root', required=True, help='Absolute Kindle path, e.g. /mnt/us/documents/FloNovel')
    parser.add_argument('--supabase-url', help='Existing project HTTPS origin')
    parser.add_argument('--publishable-key', help='Existing project public API key; never a service-role key')
    parser.add_argument('--output', type=Path, required=True, help='Private config.json outside the repository')
    parser.add_argument('--dev', action='store_true', help='Use the existing secret-dev.json partition')
    args = parser.parse_args()
    if args.desktop_properties:
        public = public_properties(args.desktop_properties)
        args.app_key = args.app_key or public.get('DROPBOX_APP_KEY')
        args.supabase_url = args.supabase_url or public.get('SUPABASE_URL')
        args.publishable_key = args.publishable_key or public.get('SUPABASE_PUBLISHABLE_KEY')
    if not args.app_key:
        parser.error('Provide --app-key or an existing public configuration with --desktop-properties')
    if not args.library_root.startswith('/mnt/us/') or '..' in args.library_root.split('/'):
        parser.error('Library root must be an explicit /mnt/us/ directory without traversal')
    repository = Path(__file__).resolve().parents[2]
    target = args.output.expanduser().resolve()
    if target.is_relative_to(repository):
        parser.error('Write credentials outside the repository')
    if target.exists():
        parser.error('Output already exists; choose another path')
    if bool(args.supabase_url) != bool(args.publishable_key):
        parser.error('Provide both Supabase URL and public API key, or neither')
    if args.supabase_url:
        parsed = urllib.parse.urlsplit(args.supabase_url)
        if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.port or parsed.path not in ('', '/') or parsed.query or parsed.fragment:
            parser.error('Supabase URL must be an HTTPS origin')
    verifier = secrets.token_urlsafe(64)
    authorize = 'https://www.dropbox.com/oauth2/authorize?' + urllib.parse.urlencode({
        'client_id': args.app_key, 'response_type': 'code', 'token_access_type': 'offline',
        'code_challenge': pkce(verifier), 'code_challenge_method': 'S256', 'scope': SCOPES,
    })
    print('Authorize FloNovel in the browser. No app secret or existing app token is imported.')
    print(authorize)
    webbrowser.open(authorize)
    code = getpass.getpass('Paste the one-time Dropbox authorization code (hidden): ').strip()
    if not code:
        raise RuntimeError('Authorization code is empty')
    token = request('https://api.dropboxapi.com/oauth2/token', urllib.parse.urlencode({
        'grant_type': 'authorization_code', 'client_id': args.app_key, 'code': code, 'code_verifier': verifier,
    }).encode('ascii'), {'Content-Type': 'application/x-www-form-urlencoded'})
    if not isinstance(token.get('refresh_token'), str) or not isinstance(token.get('access_token'), str):
        raise RuntimeError('Dropbox did not grant an offline refresh token')
    if not set(SCOPES.split()).issubset(set(token.get('scope', '').split())):
        raise RuntimeError('Required scopes were not granted; check the existing Dropbox app console')
    account = request('https://api.dropboxapi.com/2/users/get_current_account', b'null', {
        'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token['access_token'],
    })
    if not isinstance(account.get('account_id'), str):
        raise RuntimeError('Dropbox did not return an account identity')
    config = dict(version=1, app_key=args.app_key, refresh_token=token['refresh_token'],
                  account_id=account['account_id'], library_root=args.library_root.rstrip('/'),
                  dev=args.dev, position_verified=False)
    if args.supabase_url:
        config.update(supabase_url=args.supabase_url.rstrip('/'), publishable_key=args.publishable_key)
    write_private(target, config)
    print('Private configuration saved. Create the selected library directory on the Kindle, copy the')
    print('configuration to koreader/settings/flonovel/config.json, then use FloNovel > Check connection.')
    print('Treat the configuration as a password. Do not attach it to chat or commit it.')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError) as error:
        raise SystemExit(str(error)) from None
