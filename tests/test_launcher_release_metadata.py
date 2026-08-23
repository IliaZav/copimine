from __future__ import annotations

import hashlib
import json
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def run_powershell(script: Path, *args: str) -> subprocess.CompletedProcess[str]:
    command = [
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        str(script),
        *args,
    ]
    return subprocess.run(command, text=True, capture_output=True, check=False)


def test_metadata_generator_matches_real_pe_bytes(tmp_path: Path) -> None:
    # A minimal valid PE header is enough for the generator's input-boundary test.
    payload = bytearray(256)
    payload[0:2] = b"MZ"
    payload[0x3C:0x40] = (128).to_bytes(4, "little")
    payload[128:132] = b"PE\0\0"
    installer = tmp_path / "CopiMineLauncherSetup-1.0.0.exe"
    installer.write_bytes(payload)
    metadata = tmp_path / "latest.json"

    result = run_powershell(
        ROOT / "scripts/build_launcher_public_metadata.ps1",
        "-InstallerPath",
        str(installer),
        "-Version",
        "1.0.0",
        "-OutputPath",
        str(metadata),
        "-PublishedAtUtc",
        "2026-08-15T10:00:00Z",
    )

    assert result.returncode == 0, result.stdout + result.stderr
    document = json.loads(metadata.read_text(encoding="utf-8"))
    assert document["filename"] == installer.name
    assert document["sizeBytes"] == len(payload)
    assert document["sha256"] == hashlib.sha256(payload).hexdigest()
    assert document["downloadUrl"] == "/downloads/launcher/CopiMineLauncherSetup-1.0.0.exe"
    assert document["releaseNotesUrl"] == "/news/copimine-launcher-1-0-0.html"


def test_metadata_generator_publishes_optional_folder_selecting_msi(tmp_path: Path) -> None:
    payload = bytearray(256)
    payload[0:2] = b"MZ"
    payload[0x3C:0x40] = (128).to_bytes(4, "little")
    payload[128:132] = b"PE\0\0"
    installer = tmp_path / "CopiMineLauncherSetup-1.0.0.exe"
    installer.write_bytes(payload)
    msi = tmp_path / "CopiMineLauncherSetup-1.0.0.msi"
    msi_payload = b"MSI test payload for metadata boundary"
    msi.write_bytes(msi_payload)
    metadata = tmp_path / "latest.json"

    result = run_powershell(
        ROOT / "scripts/build_launcher_public_metadata.ps1",
        "-InstallerPath",
        str(installer),
        "-MsiPath",
        str(msi),
        "-Version",
        "1.0.0",
        "-OutputPath",
        str(metadata),
        "-PublishedAtUtc",
        "2026-08-15T10:00:00Z",
    )

    assert result.returncode == 0, result.stdout + result.stderr
    document = json.loads(metadata.read_text(encoding="utf-8"))
    assert document["msiFilename"] == msi.name
    assert document["msiDownloadUrl"] == "/downloads/launcher/CopiMineLauncherSetup-1.0.0.msi"
    assert document["msiSizeBytes"] == len(msi_payload)
    assert document["msiSha256"] == hashlib.sha256(msi_payload).hexdigest()
    assert document["msiInstallLocation"] == "choose"


def test_release_verifier_rejects_metadata_hash_mismatch(tmp_path: Path) -> None:
    installer = tmp_path / "CopiMineLauncherSetup-1.0.0.exe"
    payload = bytearray(256)
    payload[0:2] = b"MZ"
    payload[0x3C:0x40] = (128).to_bytes(4, "little")
    payload[128:132] = b"PE\0\0"
    installer.write_bytes(payload)
    metadata = tmp_path / "latest.json"
    metadata.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "channel": "stable",
                "version": "1.0.0",
                "architecture": "x64",
                "filename": installer.name,
                "downloadUrl": "/downloads/launcher/CopiMineLauncherSetup-1.0.0.exe",
                "sizeBytes": len(payload),
                "sha256": "0" * 64,
                "releaseNotesUrl": "/news/copimine-launcher-1-0-0.html",
            }
        ),
        encoding="utf-8",
    )

    result = run_powershell(
        ROOT / "scripts/verify_copimine_launcher_release.ps1",
        "-ArtifactRoot",
        str(tmp_path),
        "-Version",
        "1.0.0",
    )

    assert result.returncode != 0
    assert "SHA-256 mismatch" in (result.stdout + result.stderr)


def test_release_verifier_checks_the_folder_selecting_msi(tmp_path: Path) -> None:
    payload = bytearray(256)
    payload[0:2] = b"MZ"
    payload[0x3C:0x40] = (128).to_bytes(4, "little")
    payload[128:132] = b"PE\0\0"
    installer = tmp_path / "CopiMineLauncherSetup-1.0.0.exe"
    installer.write_bytes(payload)
    msi = tmp_path / "CopiMineLauncherSetup-1.0.0.msi"
    msi.write_bytes(b"MSI payload")
    metadata = tmp_path / "latest.json"
    metadata.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "channel": "stable",
                "version": "1.0.0",
                "architecture": "x64",
                "filename": installer.name,
                "downloadUrl": "/downloads/launcher/CopiMineLauncherSetup-1.0.0.exe",
                "sizeBytes": len(payload),
                "sha256": hashlib.sha256(payload).hexdigest(),
                "releaseNotesUrl": "/news/copimine-launcher-1-0-0.html",
                "msiFilename": msi.name,
                "msiDownloadUrl": "/downloads/launcher/CopiMineLauncherSetup-1.0.0.msi",
                "msiSizeBytes": msi.stat().st_size,
                "msiSha256": hashlib.sha256(msi.read_bytes()).hexdigest(),
                "msiInstallLocation": "choose",
            }
        ),
        encoding="utf-8",
    )

    result = run_powershell(
        ROOT / "scripts/verify_copimine_launcher_release.ps1",
        "-ArtifactRoot",
        str(tmp_path),
        "-Version",
        "1.0.0",
    )

    assert result.returncode == 0, result.stdout + result.stderr
    assert "RELEASE_VERIFY=PASS" in result.stdout


def test_release_verifier_rejects_folder_picker_hash_mismatch(tmp_path: Path) -> None:
    payload = bytearray(256)
    payload[0:2] = b"MZ"
    payload[0x3C:0x40] = (128).to_bytes(4, "little")
    payload[128:132] = b"PE\0\0"
    installer = tmp_path / "CopiMineLauncherSetup-1.0.0.exe"
    installer.write_bytes(payload)
    custom = tmp_path / "CopiMineLauncherFolderSetup-1.0.0.exe"
    custom_payload = bytearray(payload)
    custom_payload[-1] = 7
    custom.write_bytes(custom_payload)
    metadata = tmp_path / "latest.json"
    metadata.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "channel": "stable",
                "version": "1.0.0",
                "architecture": "x64",
                "filename": installer.name,
                "downloadUrl": "/downloads/launcher/CopiMineLauncherSetup-1.0.0.exe",
                "sizeBytes": installer.stat().st_size,
                "sha256": hashlib.sha256(installer.read_bytes()).hexdigest(),
                "releaseNotesUrl": "/news/copimine-launcher-1-0-0.html",
                "customInstallerFilename": custom.name,
                "customInstallerDownloadUrl": f"/downloads/launcher/{custom.name}",
                "customInstallerSizeBytes": custom.stat().st_size,
                "customInstallerSha256": "0" * 64,
                "customInstallerMode": "folder-picker",
            }
        ),
        encoding="utf-8",
    )

    result = run_powershell(
        ROOT / "scripts/verify_copimine_launcher_release.ps1",
        "-ArtifactRoot",
        str(tmp_path),
        "-Version",
        "1.0.0",
    )

    assert result.returncode != 0
    assert "custom installer" in (result.stdout + result.stderr).lower()
