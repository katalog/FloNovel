"""Local TLS fixture; all keys and traffic are synthetic and session-local."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
import ssl
import subprocess
import threading


def run(runtime, project, session, env):
    def openssl(*args):
        subprocess.run(['openssl', *args], cwd=session, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for name in ('ca', 'unrelated'):
        openssl('req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '2', '-subj', '/CN=' + name,
                '-keyout', name + '.key', '-out', name + '.pem', '-addext', 'basicConstraints=critical,CA:TRUE')
    openssl('req', '-newkey', 'rsa:2048', '-nodes', '-subj', '/CN=localhost', '-keyout', 'server.key', '-out', 'server.csr')
    (session / 'server.ext').write_text('subjectAltName=DNS:localhost\nbasicConstraints=CA:FALSE\nextendedKeyUsage=serverAuth\n')
    openssl('x509', '-req', '-in', 'server.csr', '-CA', 'ca.pem', '-CAkey', 'ca.key', '-CAcreateserial',
            '-out', 'server.pem', '-days', '2', '-extfile', 'server.ext')
    received = []

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            received.append(self.path)
            body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
            body['headers'] = {k.lower(): v for k, v in self.headers.items()}
            data = json.dumps(body).encode()
            self.send_response(200)
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            received.append(self.path)
            data = b'synthetic-secret-must-never-be-in-errors'
            self.send_response(302 if self.path == '/redirect' else 503)
            self.send_header('Location', '/must-not-follow')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, *_args):
            pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(session / 'server.pem', session / 'server.key')
    server.socket = context.wrap_socket(server.socket, server_side=True)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        subprocess.run([str(runtime.resolve() / 'luajit'), str(project / 'tests/check_https.lua')], cwd=runtime,
                       env=dict(env, FLONOVEL_TLS_PORT=str(server.server_port)), check=True, timeout=40)
        assert received == ['/wire', '/redirect', '/error'], 'Invalid TLS peers received application credentials or redirect was followed'
        result_file = session / 'https-results.json'
        report = json.loads(result_file.read_text())
        report['total'] += 1
        report['passed'] += 1
        report['no_http_credentials_before_tls_validation'] = True
        result_file.write_text(json.dumps(report, indent=2) + '\n')
        print('PRODUCT_HTTPS_WIRE no application request before peer verification: passed')
    finally:
        server.shutdown()
        server.server_close()
