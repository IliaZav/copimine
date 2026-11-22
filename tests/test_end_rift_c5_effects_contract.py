"""C5 authored-effect contracts that can be checked without a live client.

The mandate explicitly forbids claiming a Minecraft visual from compilation or
synthetic previews.  These tests therefore pin only the source-level properties
that make a real visual possible, and the regression suites that the runtime
must keep: a native volumetric geyser, a warned damaging footprint, authored
meshes (not colour-swapped vanilla projectiles), and gravity-driven bone rain.
They do not replace the F2 screenshots still required from the owner.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event/src/me/copimine/endevent"
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
ASSETS = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient"


def test_phase_three_geyser_is_native_and_volumetric_not_a_flame_column():
    source = (PLUGIN / "ArenaInfernoGeyserEffects.java").read_text(encoding="utf-8")
    assert 'Particle.valueOf("GEYSER_PLUME")' in source
    assert 'Particle.valueOf("GEYSER_BASE")' in source
    assert "spawnParticle(NATIVE.plume()" in source
    assert "spawnParticle(NATIVE.base()" in source
    # The old placeholder was a long-lived FLAME/LAVA point column.
    assert "Particle.FLAME" not in source and "Particle.LAVA" not in source
    # One-shot burst has a hard, small component budget.
    assert "return 5;" in source
    assert "return 24;" in source
    assert "geyser_eruption" in source


def test_phase_three_has_a_warned_footprint_and_the_visible_column_is_the_hitbox():
    source = (PLUGIN / "domain/RiftFractureEruptionPolicy.java").read_text(encoding="utf-8")
    assert "MIN_WARNING_TICKS = 20" in source
    assert "MAX_WARNING_TICKS = 24" in source
    assert "MIN_GEYSER_HEIGHT = 8.0D" in source
    assert "MAX_GEYSER_HEIGHT = 12.0D" in source
    assert "GEYSER_RADIUS_BLOCKS = 1.9D" in source
    assert "ERUPTION_DAMAGE = 7.0D" in source
    # Safe cells stay legible and the mutation is reversible.
    assert "MINIMUM_SAFE_RATIO = 0.34D" in source
    assert "RESTORE_TICKS = 30" in source
    assert "MAX_SIMULTANEOUS = 6" in source
    assert "the visible geyser is the hitbox" in source


def test_inferno_and_kagune_use_authored_meshes():
    names = (CLIENT / "GuardianSpellMesh.java").read_text(encoding="utf-8")
    for mesh in ("inferno_core", "inferno_crown", "kagune_splinter", "final_seal"):
        assert f'"{mesh}"' in names, mesh
        geometry = ASSETS / "geometry/guardian_spells" / f"{mesh}.json"
        assert geometry.is_file(), geometry
        assert geometry.stat().st_size > 1000, geometry
    renderer = (CLIENT / "GuardianSpellRenderer.java").read_text(encoding="utf-8")
    assert "inferno_core" in renderer and "inferno_crown" in renderer


def test_wave_six_bone_rain_is_gravity_driven_and_falls_physically():
    policy = (PLUGIN / "domain/WaveSixPillarSkeletonPolicy.java").read_text(encoding="utf-8")
    assert "BONE_RAIN_ARROWS = 3" in policy
    assert "BONE_RAIN_HEIGHT = 11.0D" in policy
    assert "BONE_RAIN_SPEED = 0.55D" in policy
    assert "FALL_TIMEOUT_TICKS = 60" in policy
    assert "hasLanded(" in policy and "grounded" in policy
    # Two genuinely different attacks: a fan and a vertical rain.  A third,
    # SPIRE_LANCE, was added by the Spire Harbinger rig pass, so the enum is
    # checked by name rather than by an exact literal that would falsely fail.
    ability_line = next(line for line in policy.splitlines() if "enum Ability" in line)
    for ability in ("TRIPLE_SHOT", "BONE_RAIN", "SPIRE_LANCE"):
        assert ability in ability_line, ability_line
    assert "TRIPLE_SHOT_SPREAD_DEGREES" in policy
    runtime = (PLUGIN / "runtime/RiftRamEncounter.java").read_text(encoding="utf-8")
    assert "fireBoneRain" in runtime
    # The fall is observed, never an instant position write that declares itself landed.
    assert "skeletonFalling" in runtime and "dropPillarSkeleton" in runtime
