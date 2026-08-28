from __future__ import annotations

import json
import os
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
STAGE_SCRIPT = ROOT / "scripts" / "stage_copimine_launcher_site.ps1"
RELEASE_ROOT = ROOT / "artifacts" / "launcher" / "Release"
PACKAGE_ROOT = RELEASE_ROOT / "packages"
INSTALLER = PACKAGE_ROOT / "CopiMineLauncherSetup-1.0.3.exe"
CUSTOM_INSTALLER = PACKAGE_ROOT / "CopiMineLauncherFolderSetup-1.0.3.exe"
MSI = PACKAGE_ROOT / "CopiMineLauncherSetup-1.0.3.msi"
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
            "-CustomInstallerPath",
            str(CUSTOM_INSTALLER),
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
        # The server-hosted release copy includes the native installer and the
        # managed Minecraft runtime; on a Windows checkout this can exceed
        # three minutes even when every hash check is local and healthy.
        timeout=600,
        env=os.environ.copy(),
    )


def test_staging_copies_verified_installer_metadata_and_native_release(tmp_path: Path) -> None:
    if not all(path.is_file() for path in (INSTALLER, CUSTOM_INSTALLER, MSI, METADATA)):
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
        assert staged_metadata["customInstallerSha256"] == source_metadata["customInstallerSha256"]
        assert staged_metadata["customInstallerSizeBytes"] == CUSTOM_INSTALLER.stat().st_size

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

        assets_feed = json.loads((PACKAGE_ROOT / "assets.win.json").read_text(encoding="utf-8"))
        assert (output / "downloads/launcher/releases.stable.json").read_bytes() == (
            PACKAGE_ROOT / "releases.win.json"
        ).read_bytes()
        staged_assets_feed = json.loads((output / "downloads/launcher/assets.win.json").read_text(encoding="utf-8"))
        assert any(item["RelativeFileName"] == "releases.stable.json" for item in staged_assets_feed)
        for asset in assets_feed:
            filename = asset["RelativeFileName"]
            assert (output / "downloads/launcher" / filename).is_file(), filename
        custom_filename = source_metadata["customInstallerFilename"]
        assert (output / "downloads/launcher" / custom_filename).is_file()
    finally:
        if output.exists():
            import shutil

            shutil.rmtree(output)


def test_staging_does_not_copy_stale_source_downloads_or_launcher_tree() -> None:
    stage = STAGE_SCRIPT.read_text(encoding="utf-8")

    assert "Where-Object { $_.Name -notin @('downloads', 'launcher') }" in stage


def test_launcher_build_publishes_metadata_from_the_artifacts_it_just_built() -> None:
    build = (ROOT / "scripts" / "build_copimine_launcher.ps1").read_text(encoding="utf-8")

    assert "build_launcher_public_metadata.ps1" in build
    assert "-InstallerPath $installerPath" in build
    assert "-CustomInstallerPath $folderInstallerPath" in build
    assert "-MsiPath $msiPath" in build
    assert "-OutputPath $metadataPath" in build


def test_launcher_build_isolates_velopack_temp_files_from_a_small_system_temp() -> None:
    build = (ROOT / "scripts" / "build_copimine_launcher.ps1").read_text(encoding="utf-8")

    assert "PackagingTempRoot" in build
    assert "$env:TEMP" in build
    assert "$env:TMP" in build
    assert "finally" in build


def test_launcher_staging_can_wire_the_disposable_binding_backend() -> None:
    script = (ROOT / "scripts" / "run_copimine_launcher_staging.ps1").read_text(encoding="utf-8")

    assert "[string] $LocalBindingBaseUrl" in script
    assert "COPIMINE_LAUNCHER_LOCAL_BASE_URL" in script
    assert "LocalBindingBaseUrl" in script


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
