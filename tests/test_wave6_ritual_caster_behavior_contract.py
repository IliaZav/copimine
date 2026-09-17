"""Contracts for the Wave 6 Ritual Sphere caster behaviour.

The caster is a staged encounter role, not a normal Enderman with a shorter
aggro timer.  These checks keep the guard gate, first-hit wake-up, persistent
state and four-role slot mapping explicit in source and in the pure policy.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event"
SRC = PLUGIN / "src/me/copimine/endevent"
DOMAIN = SRC / "domain"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_ritual_caster_policy_has_guarded_exposed_and_awakened_states() -> None:
    policy_path = DOMAIN / "RitualCasterTacticsPolicy.java"
    assert policy_path.is_file(), "Wave 6 casters need a dedicated pure tactics policy"
    policy = read(policy_path)
    for state in ("GUARDED_CASTING", "EXPOSED_CASTING", "AWAKENED_ATTACKING"):
        assert state in policy
    assert "state(boolean guardAlive, boolean damaged)" in policy
    assert "if (guardAlive)" in policy
    assert "damaged ? State.AWAKENED_ATTACKING : State.EXPOSED_CASTING" in policy
    assert "canTargetPlayers" in policy
    assert "castsSphere" in policy


def test_wave6_caster_slots_use_core_roles_and_bounded_amplifiers() -> None:
    policy = read(DOMAIN / "RitualCasterTacticsPolicy.java")
    for role in (
        "PROJECTILE_CASTER",
        "ZONE_CASTER",
        "REVERSE_CASTER",
        "CONTROL_SWAP_CASTER",
        "AMPLIFIER",
    ):
        assert role in policy
    assert "public static Role roleForSlot(int casterSlot)" in policy
    for mapping in (
        "case 0 -> Role.PROJECTILE_CASTER",
        "case 1 -> Role.ZONE_CASTER",
        "case 2 -> Role.REVERSE_CASTER",
        "case 3 -> Role.CONTROL_SWAP_CASTER",
        "default -> Role.AMPLIFIER",
    ):
        assert mapping in policy
    assert "VOID_LANCE" not in policy
    assert "RIFT_SPIKES" not in policy


def test_wave6_caster_runtime_keeps_casters_passive_until_guard_death_and_first_hit() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    assert "RitualCasterTacticsPolicy" in root
    assert "keyRitualCasterAwakened" in root
    assert "PersistentDataType.BYTE" in root
    assert "livingGuards > 0" in root
    assert "RitualCasterTacticsPolicy.state" in root
    assert "caster.setTarget(null)" in root
    assert "caster.setAI(false)" in root
    assert "caster.setAware(false)" in root
    assert "caster.setAI(true)" in root
    assert "caster.setAware(true)" in root
    assert "ritualCasterCanAttack" in root
    assert "readRitualCasterAwakened" in root
    assert "markRitualCasterAwakened" in root
    assert "event.getFinalDamage()" in root


def test_wave6_caster_attack_dispatch_is_explicit_and_not_a_shared_slot_modulo_ability() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    start = root.index("private void castNextRitualAbility")
    end = root.index("private void startRitualZone", start)
    body = root[start:end]
    assert "RitualCasterTacticsPolicy.Role role" in body
    assert "RitualCasterTacticsPolicy.roleForSlot(slot)" in body
    assert "RitualCasterTacticsPolicy.Role.AMPLIFIER" in body
    assert "switch (role)" in body
    for handler in (
        "spawnRitualProjectileVolley",
        "startRitualZone",
        "startRitualReverse",
        "startRitualControlSwap",
    ):
        assert handler in body
    for removed in (
        "VOID_LANCE",
        "RIFT_SPIKES",
        "spawnRitualVoidLance",
        "spawnRitualRiftSpikes",
    ):
        assert removed not in body
    assert "RitualSphereEncounterPolicy.Ability.values()" not in body


def test_wave6_caster_visual_binding_has_a_dedicated_raised_arms_variant() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    catalog = read(ROOT / "CopiMineClient/src/main/java/me/copimine/client/EndEventTextureCatalog.java")
    selection = read(ROOT / "CopiMineClient/src/main/java/me/copimine/client/EndermanRendererSelection.java")
    model = read(ROOT / "CopiMineClient/src/main/java/me/copimine/client/RiftEventEndermanModel.java")
    assert "END_RIFT_RITUAL_CASTER_V1" in root
    assert "end_rift_ritual_caster.png" in catalog
    assert "RITUAL_CASTER" in selection
    assert "caster" in model.lower()
    assert "leftArm.pitch" in model
    assert "rightArm.pitch" in model


def test_wave6_live_ai_probe_allows_server_controlled_passive_casters() -> None:
    probe = read(ROOT / "tests/RunEndRiftAiPhasesLive.ps1")
    root = read(SRC / "CopiMineEndEvent.java")
    assert "AllowPassiveRitualCasters" in probe
    assert "ritualCasters=(\\d+)" in probe
    assert "$expectedEnabled = $mobile - $casterCount" in probe
    assert "enabled -ne $expectedEnabled" in probe
    assert "ritualCastersTargeted" in probe
    assert "target=none" in probe
    assert "-AllowPassiveRitualCasters" in probe
    assert "ritualCasters=" in root
    assert "ritualCastersPassive=" in root
    assert "ritualCastersTargeted=" in root
