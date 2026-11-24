"""N23-P1-04: the chest beam's attempted/accepted ledger and torso-accurate cover.

Two defects are guarded here, and both are ordering/geometry properties of the
Bukkit-bound damage loop that a pure policy test cannot see:

1. The loop used to do ``alreadyHit.add(player.getUniqueId())`` *before*
   ``applyContactHit(...)`` returned, so a protection-cancelled or otherwise
   refused hit was still recorded — and later reported — as a hit.
2. The damage capsule is intersected at the player's torso
   (``getHeight() * 0.5``), but solid cover was sampled at
   ``player.getEyeLocation()``. A wall that shields the body while the head peeks
   over cover therefore produced a hit *through* solid cover, which is exactly the
   untelegraphed harm the spec forbids.

The master specification (N23-P1-04) requires: separate attempted/accepted
receipts so a refused damage event cannot be labelled a successful hit while
retry storms are still prevented, and a DAMAGEABLE body sample that matches the
announced cover rules. This is a static source scan because the loop needs live
Bukkit world/chunk/entity state; a behavioural harness would be a full Paper
adapter. The scan fails closed on the exact pre-fix shapes.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/ChestBeamAdmissionPolicy.java"


def _damage_loop() -> str:
    source = EVENT.read_text(encoding="utf-8")
    start = source.index("private void startChestBeam(")
    end = source.index("private void renderChestBeamStage(", start)
    body = source[start:end]
    # Isolate the damage window: from the damagesAt branch to the aftermath branch.
    dmg = body.index("if (ChestBeamPolicy.damagesAt(tick)) {")
    after = body.index("} else if (ChestBeamPolicy.aftermathAt(tick)) {", dmg)
    return body[dmg:after]


def test_the_attempt_is_remembered_before_damage_but_counted_only_when_accepted():
    loop = _damage_loop()
    attempt = loop.index("attempted.add(player.getUniqueId());")
    hit = loop.index("applyContactHit(player, boss, ChestBeamPolicy.DAMAGE, push)")
    counter = loop.index("hits[0]++;")
    assert attempt < hit, (
        "the attempt must be remembered before damage resolves, otherwise a "
        "refused (protected) event would re-roll this player every tick"
    )
    assert hit < counter, (
        "the accepted-hit counter must be incremented only after the damage call"
    )
    # The counter must sit inside the accepted branch that guards the impact.
    accepted_block = loop[hit:loop.index("renderBossContactImpact(", hit)]
    assert "hits[0]++" in accepted_block, (
        "the hit counter must be incremented only when applyContactHit accepted"
    )


def test_the_old_preemptive_already_hit_set_is_gone():
    loop = _damage_loop()
    assert "alreadyHit.add(" not in loop, (
        "a pre-emptive hit-set insertion labelled refused hits as successful"
    )
    assert "alreadyHit.contains(" not in loop, (
        "the skip must be an explicit admission decision, not a pre-emptive set test"
    )
    assert "attempted.contains(player.getUniqueId())" in loop, (
        "retry prevention must consult the attempted set through the policy"
    )


def test_cover_is_sampled_at_the_damageable_body_not_the_eye():
    loop = _damage_loop()
    assert "hasProjectileBlockLineOfSight(chest, torso)" in loop, (
        "solid cover must be sampled at the same body point the damage resolves against"
    )
    assert "hasProjectileBlockLineOfSight(chest, player.getEyeLocation())" not in loop, (
        "sampling the eye lets a body behind cover be hit because its head is visible"
    )
    assert "ChestBeamAdmissionPolicy.DAMAGEABLE_SAMPLE" in loop, (
        "the damageable sample must come from the tested policy, not an inline guess"
    )
    # The torso the capsule is intersected at and the torso cover is sampled at
    # must be the same location expression.
    assert loop.count("player.getHeight() * 0.5D") == 1, (
        "one shared torso point must serve both the capsule test and the cover ray"
    )


def test_admission_goes_through_the_pure_policy():
    loop = _damage_loop()
    assert "ChestBeamAdmissionPolicy.decide(" in loop, (
        "the per-tick verdict must delegate to the tested decide rule"
    )
    assert "ChestBeamAdmissionPolicy.admitted(decision)" in loop, (
        "only the admitted decision may resolve a hit"
    )


def test_the_resolve_ledger_separates_attempted_from_accepted():
    source = EVENT.read_text(encoding="utf-8")
    start = source.index("private void startChestBeam(")
    body = source[start:source.index("private void renderChestBeamStage(", start)]
    assert "BOSS_CHEST_BEAM_RESOLVE" in body, (
        "the beam must publish a resolve receipt"
    )
    ledger = body[body.index("BOSS_CHEST_BEAM_RESOLVE"):]
    ledger = ledger[:ledger.index(");") + 2]
    assert "attempted=" in ledger and "accepted=" in ledger, (
        "the receipt must separate attempts from accepted hits"
    )
    assert "hits[0]" in ledger, (
        "accepted must be the accepted-damage counter, not the attempt count"
    )


def test_policy_pins_the_damageable_sample_to_the_torso():
    text = POLICY.read_text(encoding="utf-8")
    assert ("DAMAGEABLE_SAMPLE = BodySample.TORSO" in text
            or "DAMAGEABLE_SAMPLE =\n            BodySample.TORSO" in text), (
        "the policy must fix the damageable sample at the torso"
    )
    assert "Decision.ATTEMPT" in text and "SKIP_ATTEMPTED" in text, (
        "the policy must distinguish an attempt from a retry skip"
    )
