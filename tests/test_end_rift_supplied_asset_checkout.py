"""Supplied Chameleon assets must survive Windows Git checkout byte for byte."""

from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ASSETS = Path("CopiMineClient/src/main/resources/assets/copimineclient/models/entity/end_rift_guardian")


def test_supplied_guardian_json_bytes_survive_windows_checkout(tmp_path):
    checkout = tmp_path / "repo"
    checkout.mkdir()
    attributes = (ROOT / ".gitattributes").read_bytes()
    (checkout / ".gitattributes").write_bytes(attributes)
    supplied = {}
    for source in sorted((ROOT / ASSETS).rglob("*.json")):
        relative = source.relative_to(ROOT)
        supplied[relative] = source.read_bytes()
        target = checkout / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(supplied[relative])
    extra = ASSETS / "animations/phase_shift.animation.json"
    assert extra in supplied, "new authored transition must survive checkout too"
    for name in ("cocoon_fall", "cocoon_land", "cast_charge", "cast_release", "cast_recovery", "final_strike"):
        assert ASSETS / f"animations/{name}.animation.json" in supplied
    # Enumerate the authored inventory explicitly. A bare count drifted (it still
    # said 15 after the spell rigs, the transition, fall/landing and the cast beats
    # landed), which would have let a silent removal pass as an "adjustment".
    expected = {ASSETS / "geometry.json"} | {
        ASSETS / "animations" / f"{name}.json" for name in (
            "cast_charge.animation", "cast_recovery.animation", "cast_release.animation",
            "cocoon_fall.animation", "cocoon_land.animation", "dying",
            "final_strike.animation", "hurt", "idle", "phase_shift.animation",
            "running", "spell_arena_inferno.animation", "spell_rift_arrows.animation",
            "spell_rift_projectile.animation", "spell_summon_servants.animation",
            "spell_void_blast.animation", "spell_void_mark.animation", "swipe",
            "udar_iz_grudi", "udar_po_zemle.animation",
        )
    }
    assert set(supplied) == expected, (
        "the supplied Guardian JSON inventory changed; "
        f"missing={sorted(str(path) for path in expected - set(supplied))} "
        f"unexpected={sorted(str(path) for path in set(supplied) - expected)}"
    )
    for arguments in (
        ["init", "-q"], ["config", "core.autocrlf", "true"],
        ["config", "core.longpaths", "true"], ["add", "."],
        ["checkout-index", "--all", f"--prefix={(tmp_path / 'export').as_posix()}/"],
    ):
        result = subprocess.run(["git", "-C", str(checkout), *arguments],
                                capture_output=True, text=True, timeout=30)
        assert result.returncode == 0, result.stdout + result.stderr
    for relative, expected in supplied.items():
        assert (tmp_path / "export" / relative).read_bytes() == expected, (
            f"Windows Git checkout rewrote the supplied asset {relative}; "
            "preserve bytes with attributes instead of changing assets or hash tests"
        )


def test_generated_model_json_survives_windows_checkout_without_source_mutation(tmp_path):
    """Build outputs must already match checkout bytes before the importer runs."""
    checkout = tmp_path / "repo"
    checkout.mkdir()
    (checkout / ".gitattributes").write_bytes((ROOT / ".gitattributes").read_bytes())
    spell_meshes = sorted(
        (ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/geometry/guardian_spells").glob("*.json"))
    # The authored set grew from eight to sixteen; this guard only has to prove
    # the glob found the meshes and that none of them silently disappeared.
    assert len(spell_meshes) >= 16, "the authored spell mesh set must not shrink"
    models = (
        Path("CopiMineClient/src/main/resources/assets/copimineclient/geometry/end_rift_tentacle.json"),
        Path("resourcepacks/src/assets/copimine/models/item/end_event_rift_tentacle.json"),
    ) + tuple(path.relative_to(ROOT) for path in spell_meshes)
    for relative in models:
        destination = checkout / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes((ROOT / relative).read_bytes())
    for arguments in (
        ["init", "-q"], ["config", "core.autocrlf", "true"],
        ["config", "core.longpaths", "true"], ["add", "."],
        ["checkout-index", "--all", f"--prefix={(tmp_path / 'export').as_posix()}/"],
    ):
        result = subprocess.run(["git", "-C", str(checkout), *arguments],
                                capture_output=True, text=True, timeout=30)
        assert result.returncode == 0, result.stdout + result.stderr
    for relative in models:
        assert (tmp_path / "export" / relative).read_bytes() == (ROOT / relative).read_bytes(), (
            f"Checkout converted generated model {relative}; the ordinary client build "
            "would then rewrite a tracked source file to its existing canonical LF bytes"
        )
