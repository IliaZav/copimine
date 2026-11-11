"""Master contract 15.3: Phase 5 RAGE summons Echo decoys, not spiders.

EchoDecoyPolicy states the invariants as plain questions - 50 hit points, at
most five alive, a ~150 s cooldown, no shared health, no boss bar, no defeat on
death, no loot. This file guards the CALL SITES, because a perfect policy that
nobody consults is exactly how the contract failed before.

The existing Wave 7 player-copy clones are a different feature and are left
alone: those replicate a PLAYER's loadout, these replicate the BOSS.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def source():
    return MONOLITH.read_text("utf-8")


def body(name, text=None):
    text = text if text is not None else source()
    start = text.index(name if "(" in name else name + "(")
    open_brace = text.index("{", start)
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace: index + 1]
    raise AssertionError("unbalanced " + name)


def test_rage_summons_go_to_the_decoy_path_not_spiders():
    summon = body("private void summonServants(")
    assert "bossPhase == BossPhase.RAGE && summonEchoDecoys(boss)" in summon, (
        "Phase 5 must route to decoys before the spider servant path"
    )
    # The decoy attempt must come first, or spiders get spawned alongside it.
    assert summon.index("summonEchoDecoys(boss)") < summon.index("EntityType.SPIDER")


def test_a_decoy_is_fifty_hit_points_of_real_server_health():
    spawn = body("private boolean summonEchoDecoys(")
    assert "EchoDecoyPolicy.decoyHealth(" in spawn, (
        "the 50 HP figure must come from the policy, not be typed twice"
    )
    assert "setBaseValue(health)" in spawn and "setHealth(health)" in spawn, (
        "the decoy's health must be set as real attribute + real health"
    )


def test_five_alive_is_a_real_cap_through_the_policy():
    spawn = body("private boolean summonEchoDecoys(")
    assert "EchoDecoyPolicy.resolveSummonBatch(" in spawn, (
        "the batch must be admitted by the policy's MAX_ALIVE=5 rule"
    )
    assert "EchoDecoyPolicy.maySummon(" in spawn, (
        "the summon cooldown must be honoured"
    )


def test_a_decoy_death_can_never_reach_the_boss_defeat_path():
    """The single most dangerous wiring mistake in this feature."""
    death = body("public void onOwnedEntityDeath(")
    assert "if (isEchoDecoy(entity))" in death, (
        "decoy deaths must be intercepted before any defeat/progress handling"
    )
    guard = death.index("if (isEchoDecoy(entity))")
    # The intercept must precede the reward/loot bookkeeping. riftRam.died is
    # deliberately NOT in this list: the Rift Ram check is an unrelated
    # wave-6-local bookkeeping call that legitimately runs first and cannot be
    # reached by a decoy (a decoy is never the ram).
    assert guard < death.index("lootIssuedEntityUuids.add"), (
        "the decoy guard must run before loot/progress bookkeeping"
    )
    # And it must return, so nothing downstream executes.
    decoy_block = death[guard: death.index("if (suppressEchoProbeDrops(event))")]
    assert "return;" in decoy_block
    assert "event.getDrops().clear()" in decoy_block, (
        "contract 15.3: a decoy drops no loot"
    )


def test_decoys_dissolve_when_the_final_seal_begins():
    text = source()
    assert 'clearEchoDecoys("entered-last-seal")' in text, (
        "contract 15.5: no decoy may survive into the last seal"
    )
    seal = text.index("transition.entered().contains(BossPhase.LAST_SEAL)")
    assert text.index("clearEchoDecoys(", seal) < seal + 400, (
        "the cleanup must be inside the LAST_SEAL entry branch"
    )


def test_decoys_are_not_persistent_orphans():
    spawn = body("private boolean summonEchoDecoys(")
    assert "registerOwnedEntity" in spawn or "spawnOwnedMob(" in spawn, (
        "a decoy must be an owned entity so generation cleanup can find it"
    )
    clear = body("private int clearEchoDecoys(")
    assert "entity.remove()" in clear
    assert "unregisterOwnedEntity(" in clear


def test_the_policy_still_denies_the_dangerous_answers():
    """Guard the contract numbers themselves, not just their use."""
    policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EchoDecoyPolicy.java").read_text("utf-8")
    assert "DECOY_HEALTH = 50.0D" in policy
    assert "MAX_ALIVE = 5" in policy
    for denied in ("sharesBossHealth", "joinsBossBar", "deathTriggersBossDefeat", "dropsLoot"):
        assert denied in policy
