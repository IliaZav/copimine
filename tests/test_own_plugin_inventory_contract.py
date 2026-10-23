import hashlib
import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / "docs" / "end-rift" / "final-26.3" / "OWN_PLUGIN_INVENTORY.json"
RECEIPT = ROOT / "local-runtime" / "end-rift-server-26.3" / "migration-plugin-receipt.json"
PLUGIN_LOCK = ROOT / "tools" / "minecraft-26.3" / "server-plugins.lock.json"
EXPECTED_DIRECTORIES = {
    "copimine-admin-plugin",
    "copimine-artifacts",
    "copimine-economy-core",
    "copimine-election-core",
    "copimine-end-event",
    "copimine-narcotics",
    "copimine-world-core",
    "minecraft/server/plugins/AuthEffects",
}


def load_inventory() -> dict:
    return json.loads(INVENTORY.read_text(encoding="utf-8"))


def manifest_for(entry: dict) -> dict:
    path = ROOT / entry["directory"] / "plugin.yml"
    if not path.is_file():
        path = ROOT / entry["directory"] / "src" / "main" / "resources" / "plugin.yml"
    return yaml.safe_load(path.read_text(encoding="utf-8-sig"))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def test_inventory_covers_every_first_party_plugin() -> None:
    plugins = load_inventory()["plugins"]
    assert len(plugins) == 8
    assert {entry["directory"] for entry in plugins} == EXPECTED_DIRECTORIES
    assert len({entry["name"] for entry in plugins}) == 8


def test_inventory_matches_manifests_commands_and_permissions() -> None:
    for entry in load_inventory()["plugins"]:
        manifest = manifest_for(entry)
        assert entry["name"] == manifest["name"], entry["directory"]
        assert entry["version"] == str(manifest["version"]), entry["name"]
        assert entry["main"] == manifest["main"], entry["name"]
        assert set(entry["dependencies"]) == set(manifest.get("depend") or []), entry["name"]
        assert set(entry["softDependencies"]) == set(manifest.get("softdepend") or []), entry["name"]
        assert set(entry["commands"]) == set((manifest.get("commands") or {}).keys()), entry["name"]
        assert entry["permissions"] == len(manifest.get("permissions") or {}), entry["name"]


def test_inventory_tests_and_installed_hashes_are_real() -> None:
    inventory = load_inventory()
    receipt = (
        json.loads(RECEIPT.read_text(encoding="utf-8-sig"))
        if RECEIPT.is_file()
        else None
    )
    installed = (
        {item["pluginName"]: item for item in receipt["installed"]}
        if receipt is not None
        else None
    )
    lock = json.loads(PLUGIN_LOCK.read_text(encoding="utf-8-sig"))
    candidates = {
        item["pluginName"]: item
        for item in (lock.get("modules", []) + lock.get("unchangedBaseline", []))
    }

    for entry in inventory["plugins"]:
        for test_path in entry["tests"]:
            assert (ROOT / test_path).is_file(), (entry["name"], test_path)

        candidate = candidates[entry["name"]]
        expected_candidate = entry.get("candidateSha256") or entry["sha256"]
        assert candidate["candidate"]["sha256"] == expected_candidate, entry["name"]

        # The migration receipt only exists after a local official install; CI checks source candidates.
        if installed is None:
            continue
        if entry["installedJar"] is None:
            assert entry["name"] not in installed
            continue
        jar = ROOT / entry["installedJar"]
        assert jar.is_file(), entry["name"]
        assert sha256(jar) == entry["sha256"], entry["name"]
        assert installed[entry["name"]]["sha256"] == entry["sha256"], entry["name"]


def test_no_live_or_native_success_is_claimed() -> None:
    inventory = load_inventory()
    assert inventory["scope"].startswith("First-party plugins only")
    for entry in inventory["plugins"]:
        assert entry["sourceBuild"] == "PASS", entry["name"]
        assert entry["unitContract"] == "PASS", entry["name"]
        assert entry["realServerEnable"] == "BLOCKED", entry["name"]
        assert entry["negativeCoverage"] == "PARTIAL", entry["name"]
        assert entry["blockedReason"].strip(), entry["name"]
        assert entry["knownGaps"], entry["name"]