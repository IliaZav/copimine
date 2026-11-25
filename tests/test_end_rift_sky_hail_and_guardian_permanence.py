"""Two LAST_SEAL guarantees that a refactor can quietly undo.

1. **Cooperative SKY_HAIL (V6):** the Wave 7 marksman heavy shot is a squad
   action. Two or three marksmen converge real travelling charges; the 12
   falling arrows only land when at least two charges arrive, and a third
   marksman adds a staggered second wave of 12. A lone survivor must not be
   able to cast it: the retired individual ceiling arrow-rain is superseded.

2. **Permanent guardian destruction (V6 §6):** a defensive Kagune killed in
   this generation is gone for the rest of the generation. The spawn reconciler
   must refuse a slot recorded in the death ledger, and every removal path must
   release a body the dead guardian was holding.

These are read from the real production methods because the cast is an inline
timer closure and the reconciler owns live entity state; a pure policy probe
cannot see either guarantee.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONOLITH = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
            / "CopiMineEndEvent.java")


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    depth = 0
    for index in range(start, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated method: {signature}")


def test_sky_hail_requires_a_cooperating_squad_and_never_casts_alone():
    source = MONOLITH.read_text(encoding="utf-8")
    start = _method(source, "private boolean beginMarksmanSkyHail(")
    assert "List<LivingEntity> squad = liveMarksmanSquad(chamber);" in start
    assert "squad.size() < 2" in start, (
        "a lone marksman survivor must be refused, not allowed to cast alone")
    assert "squad.getFirst() != leader" in start, (
        "only the squad leader may open the cooperative cast")

    # The third charge is the staggered second wave and only exists for a
    # three-marksman squad.
    assert "age == 42 && squad.size() == 3" in start, (
        "the third converged charge must be gated on a third live marksman")
    assert "squad.get(2)" in start, (
        "the second wave must be fired by the third marksman")


def test_sky_hail_releases_twelve_real_falling_arrows_per_wave():
    source = MONOLITH.read_text(encoding="utf-8")
    release = _method(source, "private void releaseMarksmanHail(")
    # Exactly twelve real arrows, spawned as real projectile entities.
    assert "for (int i = 0; i < 12; i++)" in release
    assert "spawnArrow(" in release, (
        "the hail must be real falling arrows, not damage rays or particles")
    assert "arrow.setShooter(shooter);" in release
    assert "MARKSMAN_SKY_HAIL" in release, (
        "each arrow must carry the named-trial attack identity so damage is attributed")
    # The arrows are presentation/collision real but land at validated marks.
    assert "isArenaLocation(impact)" in release
    assert "setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED)" in release


def test_sky_hail_never_falls_back_to_the_individual_arrow_rain():
    source = MONOLITH.read_text(encoding="utf-8")
    assert "Wave7MarksmanSkyHailPolicy" not in source, (
        "the superseded individual sky-hail policy must not return")
    # Both waves share the same release path and the same attack identity.
    start = _method(source, "private boolean beginMarksmanSkyHail(")
    assert "releaseMarksmanHail(cast, leader, 0);" in start
    assert "releaseMarksmanHail(cast, squad.get(2), 1);" in start


def test_a_destroyed_guardian_slot_is_never_rebuilt_in_this_generation():
    source = MONOLITH.read_text(encoding="utf-8")
    reconcile = _method(source, "private void ensurePermanentTentacles(")
    assert "guardianDeaths.isDestroyed(slot)" in reconcile, (
        "the spawn reconciler must refuse a permanently destroyed slot")
    # The refusal has to be a continue before the spawn attempt, i.e. it really
    # prevents the actor, not just skips a bookmark.
    gate = reconcile.index("guardianDeaths.isDestroyed(slot)")
    spawn = reconcile.index("spawnPermanentTentacle(") if "spawnPermanentTentacle(" in reconcile else None
    if spawn is not None:
        assert gate < spawn, "the destroyed-slot gate must run before the spawn"


def test_every_guardian_removal_releases_the_body_it_held():
    source = MONOLITH.read_text(encoding="utf-8")
    remove = _method(source, "private void removeTentacle(")
    assert "releaseTentacleHolder(entityId, reason);" in remove, (
        "a destroyed/removed guardian must release its held player")
    clear = _method(source, "private void clearTentacles(")
    assert "releaseAllTentacleHolders(reason);" in clear
    reset = _method(source, "private void resetTentacleGuardianLedger(")
    # The ledger is generation-scoped: only a new generation forgets a kill.
    assert "guardianDeaths = new GuardianDeathLedger(" in reset
