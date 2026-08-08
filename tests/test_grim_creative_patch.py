"""Regression contract for the one-class Creative inventory correction patch."""

from pathlib import Path
import subprocess
import zipfile


ROOT = Path(__file__).resolve().parents[1]
PATCH_ROOT = ROOT / "thirdparty" / "grim-creative-patch"


def test_grim_creative_patch_skips_only_creative_inventory_resync() -> None:
    source = (PATCH_ROOT / "src" / "ac" / "grim" / "grimac" / "utils" / "lists" / "CorrectingPlayerInventoryStorage.java").read_text(encoding="utf-8")
    tick = source[source.index("public void tickWithBukkit()"):source.index("    }\n}", source.index("public void tickWithBukkit()"))]

    assert "getGameMode() == GameMode.CREATIVE" in tick
    assert "pendingFinalizedSlot.clear();" in tick
    assert "serverIsCurrentlyProcessingThesePredictions.clear();" in tick
    assert tick.index("getGameMode() == GameMode.CREATIVE") < tick.index("// Loop all slot changes")
    assert "checkThatBukkitIsSynced" in tick


def test_grim_patch_build_is_pinned_to_the_live_original_jar() -> None:
    build_script = (PATCH_ROOT / "build-grim-creative-patch.ps1").read_text(encoding="utf-8")
    assert "7F1DF9C02B52C1D2F69949A0C465781ED8C53DD0C52234FEEC01785E675FF5D1" in build_script
    assert "CorrectingPlayerInventoryStorage.class" in build_script


def test_built_grim_patch_changes_exactly_one_class_and_keeps_survival_path() -> None:
    original = ROOT / "minecraft" / "server" / "plugins" / "GrimAC.jar"
    patched = PATCH_ROOT / "build" / "GrimAC-2.3.74-40684fb-creative-fix.jar"
    corrected_class = "ac/grim/grimac/utils/lists/CorrectingPlayerInventoryStorage.class"

    with zipfile.ZipFile(original) as original_jar, zipfile.ZipFile(patched) as patched_jar:
        assert set(original_jar.namelist()) == set(patched_jar.namelist())
        changed_entries = sorted(
            name
            for name in original_jar.namelist()
            if original_jar.read(name) != patched_jar.read(name)
        )
    assert changed_entries == [corrected_class]

    disassembly = subprocess.run(
        [
            "javap", "-classpath", str(patched), "-c", "-p",
            "ac.grim.grimac.utils.lists.CorrectingPlayerInventoryStorage",
        ],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    tick = disassembly[disassembly.index("public void tickWithBukkit();"):disassembly.index("private void lambda$tickWithBukkit")]
    assert "GameMode.CREATIVE" in tick
    assert "getGameMode" in tick
    assert tick.index("getGameMode") < tick.index("getTickManager")
    assert "checkThatBukkitIsSynced" in tick
