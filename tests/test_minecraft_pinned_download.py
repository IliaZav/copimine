from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
from threading import Thread
import zipfile

import pytest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts/minecraft/DownloadPinnedArtifact.ps1'


class Handler(BaseHTTPRequestHandler):
    payload = b''
    include_length = True

    def do_GET(self):
        self.send_response(200)
        if self.include_length:
            self.send_header('Content-Length', str(len(self.payload)))
        self.end_headers()
        self.wfile.write(self.payload)

    def log_message(self, *_args):
        pass


@pytest.fixture
def download_server():
    servers = []

    def start(payload, include_length=True):
        handler = type('BoundHandler', (Handler,), {'payload': payload, 'include_length': include_length})
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        servers.append((server, thread))
        return f'http://127.0.0.1:{server.server_port}/artifact'

    yield start
    for server, thread in servers:
        server.shutdown()
        thread.join(timeout=2)
        server.server_close()


def run_download(url, destination, payload, max_bytes, expected_size=None, wrapper_directory=None):
    shell = shutil.which('powershell') or shutil.which('pwsh')
    if not shell:
        pytest.skip('Pinned-download verification requires a Windows PowerShell host')
    quote = lambda value: "'" + str(value).replace("'", "''") + "'"
    wrapper = Path(wrapper_directory or destination.parent) / 'run-download.ps1'
    wrapper.write_text(
        "$ErrorActionPreference = 'Stop'\n"
        f". {quote(SCRIPT)}\n"
        "Save-PinnedArtifact "
        f"-Uri {quote(url)} "
        f"-Destination {quote(destination)} "
        "-Algorithm SHA512 "
        f"-ExpectedHash {quote(hashlib.sha512(payload).hexdigest())} "
        f"-ExpectedSize {len(payload) if expected_size is None else expected_size} "
        f"-MaximumBytes {max_bytes}\n",
        encoding='utf-8',
    )
    return subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(wrapper),
    ], capture_output=True, text=True, timeout=30)


def run_directory_assertion(path, stop_at, wrapper_directory):
    shell = shutil.which('powershell') or shutil.which('pwsh')
    if not shell:
        pytest.skip('Pinned-path verification requires a Windows PowerShell host')
    quote = lambda value: "'" + str(value).replace("'", "''") + "'"
    wrapper = Path(wrapper_directory) / 'assert-directory-path.ps1'
    wrapper.write_text(
        "$ErrorActionPreference = 'Stop'\n"
        f". {quote(SCRIPT)}\n"
        f"Assert-DirectoryPathNoReparse -Path {quote(path)} -StopAt {quote(stop_at)}\n",
        encoding='utf-8',
    )
    return subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(wrapper),
    ], capture_output=True, text=True, timeout=30)


def run_directory_tree_assertion(path, stop_at, wrapper_directory):
    shell = shutil.which('powershell') or shutil.which('pwsh')
    if not shell:
        pytest.skip('Pinned-path verification requires a Windows PowerShell host')
    quote = lambda value: "'" + str(value).replace("'", "''") + "'"
    wrapper = Path(wrapper_directory) / 'assert-directory-tree.ps1'
    wrapper.write_text(
        "$ErrorActionPreference = 'Stop'\n"
        f". {quote(SCRIPT)}\n"
        f"Assert-DirectoryTreeNoReparse -Path {quote(path)} -Description 'test output' -StopAt {quote(stop_at)}\n",
        encoding='utf-8',
    )
    return subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(wrapper),
    ], capture_output=True, text=True, timeout=30)


def run_reset_generated_directory(path, stop_at, wrapper_directory):
    shell = shutil.which('powershell') or shutil.which('pwsh')
    if not shell:
        pytest.skip('Generated-output verification requires a Windows PowerShell host')
    quote = lambda value: "'" + str(value).replace("'", "''") + "'"
    wrapper = Path(wrapper_directory) / 'reset-generated-directory.ps1'
    wrapper.write_text(
        "$ErrorActionPreference = 'Stop'\n"
        f". {quote(SCRIPT)}\n"
        f"Reset-GeneratedDirectory -Path {quote(path)} -Description 'test output' -StopAt {quote(stop_at)}\n",
        encoding='utf-8',
    )
    return subprocess.run([
        shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(wrapper),
    ], capture_output=True, text=True, timeout=30)


def test_directory_path_validation_stops_at_root_and_rejects_escape(tmp_path):
    root = tmp_path / 'trusted-root'
    nested = root / 'cache'
    nested.mkdir(parents=True)
    outside = tmp_path / 'trusted-root-sibling'
    outside.mkdir()

    valid = run_directory_assertion(nested, root, tmp_path)
    escaped = run_directory_assertion(outside, root, tmp_path)

    assert valid.returncode == 0, valid.stdout + valid.stderr
    assert escaped.returncode != 0, escaped.stdout + escaped.stderr
    assert 'outside its allowed root' in escaped.stdout + escaped.stderr


def test_directory_tree_validation_rejects_nested_junction_before_build_writes(tmp_path):
    shell = shutil.which('powershell') or shutil.which('pwsh')
    if not shell or os.name != 'nt':
        pytest.skip('Build-tree reparse validation requires Windows PowerShell')
    root = tmp_path / 'build-output'
    root.mkdir()
    external = tmp_path / 'external-output'
    external.mkdir()
    sentinel = external / 'keep.txt'
    sentinel.write_text('preserve build output target', encoding='utf-8')
    link = root / 'classes'
    junction = subprocess.run(['cmd', '/c', 'mklink', '/J', str(link), str(external)],
                              capture_output=True, text=True, timeout=10)
    if junction.returncode != 0:
        pytest.skip(f'Creating a directory junction is unavailable: {junction.stderr}')

    result = run_directory_tree_assertion(root, root, tmp_path)

    assert result.returncode != 0, result.stdout + result.stderr
    assert 'reparse point' in result.stdout + result.stderr
    assert sentinel.read_text(encoding='utf-8') == 'preserve build output target'


def test_reset_generated_directory_unlinks_hardlinks_without_changing_external_file(tmp_path):
    shell = shutil.which('powershell') or shutil.which('pwsh')
    if not shell or os.name != 'nt':
        pytest.skip('Hard-link build-output validation requires Windows PowerShell')
    root = tmp_path / 'build-output'
    root.mkdir()
    sentinel = tmp_path / 'external-sentinel.txt'
    sentinel.write_text('preserve hard-link target', encoding='utf-8')
    linked_output = root / 'linked-output.class'
    try:
        os.link(sentinel, linked_output)
    except OSError as error:
        pytest.skip(f'Creating a file hard link is unavailable: {error}')

    result = run_reset_generated_directory(root, tmp_path, tmp_path)

    assert result.returncode == 0, result.stdout + result.stderr
    assert sentinel.read_text(encoding='utf-8') == 'preserve hard-link target'
    assert list(root.iterdir()) == []


def test_verified_download_replaces_corrupt_cache_only_after_hash_check(tmp_path, download_server):
    payload = b'pinned-minecraft-test-artifact'
    url = download_server(payload)
    destination = tmp_path / 'candidate.jar'
    destination.write_bytes(b'corrupt cached bytes')

    result = run_download(url, destination, payload, len(payload))

    assert result.returncode == 0, result.stdout + result.stderr
    assert destination.read_bytes() == payload
    assert not (tmp_path / 'candidate.jar.download').exists()


def test_oversized_response_preserves_existing_candidate_and_cleans_temp(tmp_path, download_server):
    payload = b'X' * 4097
    url = download_server(payload, include_length=False)
    destination = tmp_path / 'candidate.jar'
    original = b'previous candidate'
    destination.write_bytes(original)

    result = run_download(url, destination, payload, 1024, expected_size=0)

    assert result.returncode != 0, result.stdout + result.stderr
    assert destination.read_bytes() == original
    assert not (tmp_path / 'candidate.jar.download').exists()


def test_pinned_download_rejects_unsafe_destination_before_replacing_it(tmp_path, download_server):
    payload = b'pinned-minecraft-test-artifact'
    url = download_server(payload)
    destination = tmp_path / 'candidate.jar'
    destination.mkdir()
    sentinel = destination / 'keep.txt'
    sentinel.write_text('preserve destination', encoding='utf-8')

    result = run_download(url, destination, payload, len(payload))

    assert result.returncode != 0, result.stdout + result.stderr
    assert sentinel.read_text(encoding='utf-8') == 'preserve destination'
    assert not (tmp_path / 'candidate.jar.download').exists()


def test_pinned_download_refuses_a_junction_at_its_temporary_path(tmp_path, download_server):
    shell = shutil.which('powershell') or shutil.which('pwsh')
    if not shell or os.name != 'nt':
        pytest.skip('Download reparse-point validation requires Windows PowerShell')
    payload = b'pinned-minecraft-test-artifact'
    url = download_server(payload)
    destination = tmp_path / 'candidate.jar'
    external = tmp_path / 'external'
    external.mkdir()
    sentinel = external / 'keep.txt'
    sentinel.write_text('preserve junction target', encoding='utf-8')
    link = tmp_path / 'candidate.jar.download'
    junction = subprocess.run(['cmd', '/c', 'mklink', '/J', str(link), str(external)],
                              capture_output=True, text=True, timeout=10)
    if junction.returncode != 0:
        pytest.skip(f'Creating a directory junction is unavailable: {junction.stderr}')

    result = run_download(url, destination, payload, len(payload))

    assert result.returncode != 0, result.stdout + result.stderr
    assert not destination.exists()
    assert sentinel.read_text(encoding='utf-8') == 'preserve junction target'
    assert link.is_dir()


def test_pinned_download_refuses_a_junction_in_its_destination_parent(tmp_path, download_server):
    shell = shutil.which('powershell') or shutil.which('pwsh')
    if not shell or os.name != 'nt':
        pytest.skip('Download reparse-point validation requires Windows PowerShell')
    payload = b'pinned-minecraft-test-artifact'
    url = download_server(payload)
    external = tmp_path / 'external'
    external.mkdir()
    sentinel = external / 'keep.txt'
    sentinel.write_text('preserve parent target', encoding='utf-8')
    link = tmp_path / 'artifact-parent'
    junction = subprocess.run(['cmd', '/c', 'mklink', '/J', str(link), str(external)],
                              capture_output=True, text=True, timeout=10)
    if junction.returncode != 0:
        pytest.skip(f'Creating a directory junction is unavailable: {junction.stderr}')

    destination = link / 'candidate.jar'
    result = run_download(url, destination, payload, len(payload), wrapper_directory=tmp_path)

    assert result.returncode != 0, result.stdout + result.stderr
    assert not (external / 'candidate.jar').exists()
    assert sentinel.read_text(encoding='utf-8') == 'preserve parent target'
    assert {item.name for item in external.iterdir()} == {'keep.txt'}


def run_install_tool(archive, destination, executable, expected_hash, expected_size):
    shell = shutil.which('powershell') or shutil.which('pwsh')
    if not shell:
        pytest.skip('Pinned tool extraction verification requires a Windows PowerShell host')
    quote = lambda value: "'" + str(value).replace("'", "''") + "'"
    wrapper = archive.parent / 'install-tool.ps1'
    wrapper.write_text(
        "$ErrorActionPreference = 'Stop'\n"
        f". {quote(SCRIPT)}\n"
        "Install-PinnedToolArchive "
        f"-Archive {quote(archive)} -Algorithm SHA256 "
        f"-ExpectedHash {quote(expected_hash)} -ExpectedSize {expected_size} "
        f"-DestinationDirectory {quote(destination)} "
        f"-ExecutableRelativePath {quote(executable)}\n",
        encoding='utf-8',
    )
    return subprocess.run([shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(wrapper)],
                          capture_output=True, text=True, timeout=30)


def test_pinned_tool_archive_replaces_existing_extracted_cache(tmp_path):
    archive = tmp_path / 'gradle.zip'
    content = b'@echo pinned gradle\r\n'
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as zipped:
        zipped.writestr('gradle-9.8.0/bin/gradle.bat', content)
    destination = tmp_path / 'gradle-9.8.0'
    executable = destination / 'bin/gradle.bat'
    executable.parent.mkdir(parents=True)
    executable.write_bytes(b'@echo mutated command\r\n')

    result = run_install_tool(archive, destination, 'bin/gradle.bat',
                              hashlib.sha256(archive.read_bytes()).hexdigest(), archive.stat().st_size)

    assert result.returncode == 0, result.stdout + result.stderr
    assert executable.read_bytes() == content
    assert not list(tmp_path.glob('.gradle-9.8.0.extract-*'))
    assert not list(tmp_path.glob('.gradle-9.8.0.previous-*'))


def test_invalid_pinned_tool_archive_preserves_existing_extraction(tmp_path):
    archive = tmp_path / 'gradle.zip'
    archive.write_bytes(b'not the locked archive')
    destination = tmp_path / 'gradle-9.8.0'
    executable = destination / 'bin/gradle.bat'
    executable.parent.mkdir(parents=True)
    previous = b'previous extracted command'
    executable.write_bytes(previous)

    result = run_install_tool(archive, destination, 'bin/gradle.bat', '0' * 64, archive.stat().st_size)

    assert result.returncode != 0, result.stdout + result.stderr
    assert executable.read_bytes() == previous


def test_pinned_tool_install_refuses_a_junction_in_its_destination_parent(tmp_path):
    shell = shutil.which('powershell') or shutil.which('pwsh')
    if not shell or os.name != 'nt':
        pytest.skip('Pinned tool extraction validation requires Windows PowerShell')
    archive = tmp_path / 'gradle.zip'
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as zipped:
        zipped.writestr('gradle-9.8.0/bin/gradle.bat', b'@echo pinned gradle\r\n')
    external = tmp_path / 'external'
    external.mkdir()
    sentinel = external / 'keep.txt'
    sentinel.write_text('preserve tool parent target', encoding='utf-8')
    link = tmp_path / 'tool-parent'
    junction = subprocess.run(['cmd', '/c', 'mklink', '/J', str(link), str(external)],
                              capture_output=True, text=True, timeout=10)
    if junction.returncode != 0:
        pytest.skip(f'Creating a directory junction is unavailable: {junction.stderr}')

    destination = link / 'gradle-9.8.0'
    result = run_install_tool(archive, destination, 'bin/gradle.bat',
                              hashlib.sha256(archive.read_bytes()).hexdigest(), archive.stat().st_size)

    assert result.returncode != 0, result.stdout + result.stderr
    assert sentinel.read_text(encoding='utf-8') == 'preserve tool parent target'
    assert {item.name for item in external.iterdir()} == {'keep.txt'}
