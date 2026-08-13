from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_full_release_carries_the_external_artifact_catalog_used_at_runtime():
    package_script = read("scripts/package_full_release.ps1")
    validator_script = read("scripts/validate_release_bundle.ps1")

    # CopiMineArtifacts reads plugins/CopiMineArtifacts/items.yml when it
    # exists.  A JAR-only patch therefore leaves the shop on the old catalog.
    assert "minecraft\\server\\plugins\\CopiMineArtifacts\\items.yml" in package_script
    assert "minecraft\\server\\plugins\\CopiMineArtifacts\\items.yml" in validator_script


def test_local_runtime_catalog_is_the_source_catalog():
    source = (ROOT / "copimine-artifacts" / "items.yml").read_bytes()
    runtime = (ROOT / "minecraft" / "server" / "plugins" / "CopiMineArtifacts" / "items.yml").read_bytes()

    assert runtime == source


def test_current_runtime_catalog_contains_the_recent_donation_items():
    runtime = (ROOT / "minecraft" / "server" / "plugins" / "CopiMineArtifacts" / "items.yml").read_text(
        encoding="utf-8"
    )

    for item_id in ("berserker_heart", "zhilorez_pickaxe", "night_cloak"):
        assert f"item-id: {item_id}" in runtime
