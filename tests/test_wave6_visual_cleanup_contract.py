"""Regression contracts for the visible Wave 6 ritual scene."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
CLIENT_MIXIN = ROOT / (
    "CopiMineClient/src/main/java/me/copimine/client/mixin/"
    "EndermanEyesFeatureRendererMixin.java"
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_client_eyes_mixin_matches_1_21_entity_descriptor() -> None:
    source = read(CLIENT_MIXIN)
    assert "import net.minecraft.entity.Entity;" in source
    assert "T extends Entity" in source
    assert "T extends LivingEntity" not in source


def test_wave6_start_removes_stale_walls_and_wave3_portals() -> None:
    source = read(SERVER)
    start = source.index("private boolean startRitualSphereObjective")
    end = source.index("/** Place one ritual entity", start)
    body = source[start:end]
    assert "clearLegacyWave7BarrierArtifacts" in body
    assert "clearLegacyWave3PortalArtifacts" in body


def test_bootstrap_clears_stale_wave7_walls_and_wave3_portals_outside_wave7() -> None:
    source = read(SERVER)
    start = source.index("private void restorePersistedCombatRuntime")
    end = source.index("private void restorePersistedRitualSphereObjective", start)
    body = source[start:end]
    assert "clearRealitySplitBarriers(\"bootstrap-non-wave7\")" in body
    assert "clearLegacyWave7BarrierArtifacts(\"bootstrap-non-wave7\")" in body
    assert "clearLegacyWave3PortalArtifacts(\"bootstrap-non-wave7\")" in body


def test_prisoner_anchor_is_inside_the_sphere_not_three_blocks_to_the_side() -> None:
    source = read(SERVER)
    start = source.index("private Location ritualPrisonerLocation")
    end = source.index("private Location safeRitualLocation", start)
    body = source[start:end]
    assert "RITUAL_SPHERE_HEIGHT_OFFSET" in body
    assert "3.4D" not in body


def test_wave6_sphere_vfx_has_prisoner_focus_and_multiple_dense_layers() -> None:
    source = read(SERVER)
    start = source.index("private void renderCurrentRitualSphere")
    end = source.index("private void renderRitualZone", start)
    body = source[start:end]
    assert "ritualPrisonerId()" in body
    assert "Particle.DRAGON_BREATH" in body
    assert "Particle.PORTAL" in body
    assert body.count("spawnPatternRing") >= 3


def test_wave6_caster_display_name_is_plain_zaklinatel() -> None:
    source = read(SERVER)
    assert 'ChatColor.LIGHT_PURPLE + "Заклинатель"' in source
    assert "Кастёр Сферы" not in source
