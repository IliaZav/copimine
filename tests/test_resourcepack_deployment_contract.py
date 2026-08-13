from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepacks" / "build" / "CopiMineResourcePack.zip"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_built_pack_contains_models_and_textures_for_recent_artifacts():
    expected = {
        "repair_kit",
        "return_stone",
        "infinite_torch",
        "gravedigger_contract",
        "signal_bell",
        "berserker_heart",
        "zhilorez_pickaxe",
        "night_cloak",
    }

    with ZipFile(PACK) as archive:
        names = set(archive.namelist())
        assert archive.testzip() is None
        for item_id in expected:
            assert f"assets/copimine/models/item/artifacts/{item_id}.json" in names
            assert f"assets/copimine/textures/item/artifacts/{item_id}.png" in names


def test_resourcepack_installer_is_scoped_and_verifies_the_published_pack():
    installer = read("deploy/ubuntu/install_resourcepack_patch.sh")

    assert "resourcepacks/build" in installer
    assert "sha1sum" in installer
    assert "sha256sum" in installer
    assert "unzip -tq" in installer
    assert "server.properties" in installer
    assert "resource-pack-sha1" in installer
    assert "systemctl restart copimine-minecraft" in installer
    assert "/opt/copimine-backups" in installer
    assert "world" not in installer.lower()
    assert "postgres" not in installer.lower()


def test_required_entry_check_is_safe_with_bash_pipefail():
    installer = read("deploy/ubuntu/install_resourcepack_patch.sh")

    # grep -q exits as soon as it finds a match. With pipefail enabled that
    # turns unzip's resulting SIGPIPE into a false negative for an entry that
    # is actually present in the archive.
    assert 'grep -Fx -- "$entry" >/dev/null' in installer
    assert 'grep -Fqx -- "$entry"' not in installer
