"""Chaquopy entry point: runs the Flasher Builder backend inside the Android app.

The desktop code launches helper processes via ``sys.executable`` and downloads with aria2c/curl,
none of which exist on Android. This module adapts it without modifying the upstream sources:

* native tools (otaripper, lpmake) come from the APK's native library directory;
* ``--helper`` subprocesses (bloat download, avbtool) run in-process;
* downloads use urllib (resumable, cancellable) instead of aria2c/curl;
* stdout/stderr lines are forwarded to a Kotlin sink object.
"""
import os
import sys
import threading
import time
import traceback
from pathlib import Path

INPROC = '@@inproc'
INBOX_DIR = 'ota-inbox'      # LocalInbox'ın seçilen OTA dosyalarını koyduğu klasör adı


class _Sink:
    """File-like object that forwards complete lines to ``sink.onLine(str)``."""

    encoding = 'utf-8'
    errors = 'replace'

    def __init__(self, sink):
        self._sink = sink
        self._buf = ''
        self._lock = threading.Lock()

    def write(self, text):
        if not text:
            return 0
        with self._lock:
            self._buf += text.replace('\r\n', '\n').replace('\r', '\n')
            *lines, self._buf = self._buf.split('\n')
        for line in lines:
            self._emit(line)
        return len(text)

    def _emit(self, line):
        try:
            self._sink.onLine(line)
        except Exception:
            pass

    def flush(self):
        pass

    def finish(self):
        with self._lock:
            rest, self._buf = self._buf, ''
        if rest:
            self._emit(rest)

    def isatty(self):
        return False

    def writable(self):
        return True

    def reconfigure(self, **kwargs):
        pass


# ───────────────────────── downloads ─────────────────────────

def android_download(url, destination, *, root=None, headers=(), check=lambda: None, resolve=None,
                     checksum=None, expected_bytes=None, verify=None):
    """urllib replacement for scripts.http_download.download_url (same contract)."""
    import hashlib
    from urllib.error import HTTPError, URLError
    from urllib.parse import urlsplit
    from urllib.request import Request, urlopen
    from scripts.http_download import DownloadError

    destination = Path(destination).resolve()
    if destination.exists():
        raise DownloadError('Download temporary file already exists.')
    if urlsplit(url).scheme not in ('https', 'http') or any(c in url for c in '\r\n'):
        raise DownloadError('Only HTTP and HTTPS downloads are supported.')
    request_headers = {'User-Agent': 'FlasherBuilder-Android'}
    for header in headers:
        if '\r' in header or '\n' in header or ':' not in header:
            raise DownloadError('Invalid download header.')
        key, value = header.split(':', 1)
        request_headers[key.strip()] = value.strip()
    destination.parent.mkdir(parents=True, exist_ok=True)

    complete = False
    try:
        check()
        address = resolve(url) if resolve else url
        if urlsplit(address).scheme not in ('http', 'https') or any(c in address for c in '\r\n'):
            raise DownloadError('The download service returned an invalid URL.')
        have, total, attempt = 0, 0, 0
        while True:
            check()
            attempt += 1
            headers_now = dict(request_headers)
            if have:
                headers_now['Range'] = f'bytes={have}-'
            try:
                with urlopen(Request(address, headers=headers_now), timeout=30) as response:
                    if have and response.status != 206:
                        have = 0                      # server ignored Range: start over
                    length = int(response.headers.get('Content-Length') or 0)
                    total = have + length if length else 0
                    last = 0.0
                    with destination.open('ab' if have else 'wb') as out:
                        while True:
                            check()
                            chunk = response.read(1 << 20)
                            if not chunk:
                                break
                            out.write(chunk)
                            have += len(chunk)
                            now = time.monotonic()
                            if now - last >= 1.0:
                                last = now
                                print(f'@@DL {have} {total} {destination.name}', flush=True)
                if total and have < total:
                    raise DownloadError('Connection closed early.')
                break
            except HTTPError as error:
                code = error.code
                error.close()
                if code == 416 and have:
                    break                              # already complete
                if code in (401, 403, 404, 410):
                    raise DownloadError(f'The server refused the download (HTTP {code}). The link may have expired.') from None
                if attempt >= 5:
                    raise DownloadError(f'Download failed (HTTP {code}).') from None
            except InterruptedError:
                raise                                   # cancellation from helper code
            except (URLError, OSError, DownloadError) as error:
                if attempt >= 5:
                    raise DownloadError(f'Download failed: {error}') from None
            have = destination.stat().st_size if destination.exists() else 0
            print(f'Connection problem; retrying ({attempt}/5)…', flush=True)
            for _ in range(10):
                check()
                time.sleep(.2)
        print(f'@@DL {have} {have} {destination.name}', flush=True)
        if not destination.is_file():
            raise DownloadError('The downloader did not create its output file.')
        if expected_bytes is not None and destination.stat().st_size != expected_bytes:
            raise DownloadError('Downloaded file size mismatch.')
        if checksum:
            digest = hashlib.sha256()
            with destination.open('rb') as source:
                while block := source.read(4 * 1024 ** 2):
                    check()
                    digest.update(block)
            if digest.hexdigest() != checksum:
                raise DownloadError('Downloaded file SHA-256 mismatch.')
        if verify and not verify(destination):
            raise DownloadError('Downloaded content failed validation.')
        complete = True
        return 'urllib'
    finally:
        if not complete:
            destination.unlink(missing_ok=True)


# ───────────────────────── patches ─────────────────────────

_patched = False


def _patch(native_dir):
    global _patched
    from flasher_builder import host, backend
    from scripts import http_download, materialize_bloat

    def helper_command(root, helper, *args):
        return [INPROC, helper] + [str(a) for a in args]

    host.helper_command = helper_command
    backend.helper_command = helper_command
    http_download.download_url = android_download
    backend.download_url = android_download
    materialize_bloat.download_url = android_download

    if native_dir:
        def tool(root, name):
            return Path(native_dir) / f'lib{name}.so'
        host.tool = tool
        backend.tool = tool

        def check_environment(root=None):
            for name in ('otaripper', 'lpmake'):
                if not tool(None, name).is_file():
                    raise backend.BuildError(f'Native tool missing from the APK: {name}')
            print('Android backend ready (arm64 otaripper + lpmake).', flush=True)
        backend.check_environment = check_environment

    for name in ('flasher_builder.native_pipeline', 'flasher_builder.selftest'):
        module = sys.modules.get(name)
        if module is not None:
            module.helper_command = helper_command
            if native_dir and hasattr(module, 'tool'):
                module.tool = host.tool

    if not _patched:
        original_run = backend.Worker.run

        def run(self, args, cwd=None, env=None):
            if args and str(args[0]) == INPROC:
                return _run_inprocess(backend, self, [str(a) for a in args[1:]])
            return original_run(self, args, cwd=cwd, env=env)

        backend.Worker.run = run

        original_source = backend.Worker.source

        def source(self, source, dest):
            """Cihazdan seçilen OTA (ota-inbox içinde) kopyalanmaz, job klasörüne taşınır: ekstra yer gerekmez."""
            cleaned = backend.clean_source(source)
            if cleaned and not backend.is_url(cleaned):
                path = Path(cleaned).expanduser().resolve()
                if path.parent.name == INBOX_DIR:
                    if not path.is_file():
                        raise backend.BuildError(f'OTA file not found: {path.name}. Pick the file again.')
                    backend.validate_ota(path)
                    print(f'Moving {path.name} into the job folder…', flush=True)
                    self.check()
                    import shutil
                    shutil.move(str(path), str(dest))
                    return backend.validate_ota(dest)
            return original_source(self, source, dest)

        backend.Worker.source = source
        _patched = True


def _run_inprocess(backend, worker, args):
    helper, arguments = args[0], args[1:]
    worker.check()
    saved_argv = sys.argv
    code = 0
    try:
        if helper == 'materialize':
            from scripts import materialize_bloat
            sys.argv = ['materialize_bloat'] + arguments
            try:
                code = materialize_bloat.main()
            except SystemExit as exit_:
                code = exit_.code
        elif helper == 'avbtool':
            from build_tools_assets import avbtool
            try:
                avbtool.AvbTool().run(['avbtool'] + arguments)
            except SystemExit as exit_:
                code = exit_.code
        else:
            raise backend.BuildError('Unknown worker helper: ' + helper)
    finally:
        sys.argv = saved_argv
    worker.check()                                     # a cancelled helper reports as cancellation
    if code:
        raise backend.BuildError(f'{helper} failed (exit {code}). See the log above.')


# ───────────────────────── entry ─────────────────────────

def run(root, native_dir, argv, sink):
    """Run the backend CLI with ``argv`` (list of str). Returns its exit code (0 ok, 130 cancelled)."""
    root = str(root)
    if root not in sys.path:
        sys.path.insert(0, root)
    os.chdir(root)
    out = _Sink(sink)
    saved = sys.stdout, sys.stderr, sys.argv
    sys.stdout = sys.stderr = out
    try:
        _patch(str(native_dir) if native_dir else '')
        from flasher_builder import backend
        sys.argv = ['flasher-worker', '--repo', root] + [str(a) for a in (argv.toArray() if hasattr(argv, 'toArray') else argv)]
        return int(backend.main())
    except SystemExit as exit_:
        return int(exit_.code or 0) if isinstance(exit_.code, int) or exit_.code is None else 1
    except BaseException:
        traceback.print_exc()
        return 1
    finally:
        out.finish()
        sys.stdout, sys.stderr, sys.argv = saved
