from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_full_release_packages_canonical_artifacts_catalog_without_reading_runtime_copy() -> None:
    package = (ROOT / "scripts/package_full_release.ps1").read_text(encoding="utf-8")
    assert "$artifactCatalogSource = Join-Path $ProjectRoot 'copimine-artifacts\\items.yml'" in package
    assert "$artifactCatalogDestination = Join-Path $payloadRoot 'minecraft\\server\\plugins\\CopiMineArtifacts\\items.yml'" in package
    assert "Copy-Item -LiteralPath $artifactCatalogSource -Destination $artifactCatalogDestination -Force" in package
    assert "Get-Sha256Lower -LiteralPath $artifactCatalogSource" in package
    assert "Get-Sha256Lower -LiteralPath $artifactCatalogDestination" in package
    assert "Canonical Artifacts catalog differs from the staged runtime catalog" in package


def test_release_upgrade_preserves_existing_artifacts_runtime_data() -> None:
    replacement = (ROOT / "deploy/ubuntu/copimine_full_replace.sh").read_text(encoding="utf-8")
    runtime = replacement.split("RUNTIME_PATHS=(", 1)[1].split(")", 1)[0]
    assert '"minecraft/server/plugins/CopiMineArtifacts"' in runtime

    restore = replacement.split("restore_runtime_state() {", 1)[1].split("\n}", 1)[0]
    assert 'for runtime_path in "${RUNTIME_PATHS[@]}"; do' in restore
    assert 'cp -a "$PRESERVE_ROOT/$runtime_path" "$target_root/$runtime_path"' in restore


def test_plugin_build_seeds_artifacts_catalog_only_when_runtime_file_is_missing() -> None:
    build = (ROOT / "copimine-artifacts/build-plugin.ps1").read_text(encoding="utf-8")
    assert "$serverCatalog = Join-Path $serverDataDir 'items.yml'" in build
    assert "if (-not (Test-Path -LiteralPath $serverCatalog -PathType Leaf))" in build
    assert "Copy-Item -LiteralPath (Join-Path $pluginDir 'items.yml') -Destination $serverCatalog" in build
