from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_full_plugin_runtime_verifier_is_manifest_driven_and_validation_guarded() -> None:
    verifier = ROOT / "scripts" / "verify_copimine_full_plugin_local_server.ps1"
    assert verifier.exists()
    source = verifier.read_text(encoding="utf-8")

    assert "artifacts\\local-validation" in source
    assert "full-plugin-manifest.json" in source
    assert "plugin.yml" in source
    assert "Bukkit plugins" in source
    assert "inventoryIndex" in source
    assert "inventoryIndex + 1" in source
    assert "FULL_PLUGIN_RUNTIME=PASS" in source
    assert "PRODUCTION_DATA_MODIFIED=NO" in source
    assert "Could not load" in source
    assert "Disabling" in source
