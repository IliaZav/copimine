from __future__ import annotations

import json
import os
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
STAGE_SCRIPT = ROOT / "scripts" / "stage_copimine_launcher_site.ps1"
RELEASE_ROOT = ROOT / "artifacts" / "launcher" / "Release"
PACKAGE_ROOT = RELEASE_ROOT / "packages"
INSTALLER = PACKAGE_ROOT / "CopiMineLauncherSetup-1.0.1.exe"
MSI = PACKAGE_ROOT / "CopiMineLauncherSetup-1.0.1.msi"
METADATA = RELEASE_ROOT / "metadata" / "latest.json"
INSTANCE = RELEASE_ROOT / "instance-current"


def run_stage(metadata: Path, output: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(STAGE_SCRIPT),
            "-InstallerPath",
            str(INSTALLER),
            "-MsiPath",
            str(MSI),
            "-MetadataPath",
            str(metadata),
            "-OutputRoot",
            str(output),
            "-InstanceReleaseRoot",
            str(INSTANCE),
            "-ServerHostedRuntimeOnly",
        ],
        cwd=ROOT,
        text=True,
        capture_output=True,
        timeout=180,
        env=os.environ.copy(),
    )


def test_staging_copies_verified_installer_metadata_and_native_release(tmp_path: Path) -> None:
    if not all(path.is_file() for path in (INSTALLER, MSI, METADATA)):
        raise AssertionError("launcher release fixture is missing; build/package the local release first")
    output = ROOT / "artifacts" / "launcher" / f"staging-contract-{os.getpid()}"
    result = run_stage(METADATA, output)
    try:
        assert result.returncode == 0, result.stdout + result.stderr
        staged_metadata = json.loads((output / "assets/public-data/launcher/latest.json").read_text(encoding="utf-8"))
        source_metadata = json.loads(METADATA.read_text(encoding="utf-8"))
        assert staged_metadata["sha256"] == source_metadata["sha256"]
        assert staged_metadata["sizeBytes"] == INSTALLER.stat().st_size
        assert staged_metadata["msiSha256"] == source_metadata["msiSha256"]
        assert staged_metadata["msiSizeBytes"] == MSI.stat().st_size

        manifest_path = output / "launcher/stable/instance-manifest.json"
        signature_path = output / "launcher/stable/instance-manifest.sig"
        assert manifest_path.is_file()
        assert signature_path.is_file()
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        artifacts = list(manifest["files"]) + [manifest["javaRuntime"], manifest["minecraftRuntime"]]
        for artifact in artifacts:
            staged_file = output / "launcher/files" / artifact["sha256"]
            assert staged_file.is_file(), artifact
            expected_size = artifact.get("size", artifact.get("sizeBytes"))
            assert staged_file.stat().st_size == expected_size, artifact
        runtime = manifest["minecraftRuntime"]
        runtime_file = output / "launcher/files" / runtime["sha256"]
        assert runtime_file.is_file()
        assert runtime_file.stat().st_size == runtime["sizeBytes"]
        assert (output / "Assets/WebView2/MicrosoftEdgeWebView2RuntimeInstallerX64.exe").stat().st_size > 100_000_000
    finally:
        if output.exists():
            import shutil

            shutil.rmtree(output)


def test_staging_rejects_metadata_hash_mismatch_before_publish(tmp_path: Path) -> None:
    source = json.loads(METADATA.read_text(encoding="utf-8"))
    source["sha256"] = "0" * 64
    bad_metadata = tmp_path / "bad-metadata.json"
    bad_metadata.write_text(json.dumps(source), encoding="utf-8")
    output = ROOT / "artifacts" / "launcher" / f"staging-contract-invalid-{os.getpid()}"
    result = run_stage(bad_metadata, output)
    try:
        assert result.returncode != 0
        assert "metadata" in (result.stdout + result.stderr).lower()
        assert not output.exists()
    finally:
        if output.exists():
            import shutil

            shutil.rmtree(output)
