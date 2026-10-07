"""The superseded amplifier subsystem must not affect Wave 6 spells."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
SCALING = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RitualSphereScalingPolicy.java"
PROGRESSION = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RitualCasterProgressionPolicy.java"


def test_volley_size_is_participant_profile_owned_not_amplifier_owned() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    scaling = SCALING.read_text(encoding="utf-8")
    for obsolete in ("RitualAmplifierPolicy", "channelingAmplifiers", "liveAmplifiers", "effective_intensity"):
        assert obsolete not in source
        assert obsolete not in scaling
    assert "ritualSphereState.profile().projectilesPerVolley()" in source
    assert "MAX_PROJECTILES_PER_VOLLEY = 7" in scaling
    assert "projectiles = count <= 2 ? 3" in scaling
    assert "count == 3 ? 4" in scaling
    assert "count == 6 ? 6 : 7" in scaling


def test_caster_deaths_add_fixed_spells_and_unlock_qwer_abilities() -> None:
    source = PROGRESSION.read_text(encoding="utf-8")
    assert "case 1 -> MajorSpell.RIFT_BARRAGE" in source
    assert "case 2 -> MajorSpell.GRAVITY_WELL" in source
    assert "case 3 -> MajorSpell.SOUL_BRAND" in source
    assert "case 4 -> MajorSpell.RIFT_CHAINS" in source
    assert "case 1 -> PrisonerAbility.Q_HEAL" in source
    assert "case 2 -> PrisonerAbility.W_BATTLE_SURGE" in source
    assert "case 3 -> PrisonerAbility.E_GUARDIAN_LINK" in source
    assert "case 4 -> PrisonerAbility.R_TURNCOAT" in source
    assert "deaths == TOTAL_CASTERS" in source
