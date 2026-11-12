"""Master contract 15.2 Type A: delayed spectral attack replicas.

EchoAttackReplicaPolicy decides the two silent failures for us - no invisible
homing (the aim locks at the telegraph tick) and no double damage (every damage
source of one copy has its own ledger key). This file guards the wiring, because
both failures look like working code: the copy appears, damage happens, and only
a player who counts their own hit points knows that something home in on them or
hit them twice.
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


def test_replicas_appear_when_rage_begins():
    text = source()
    # The entry used to pass "VOID_BLAST", a family EchoAttackReplicaPolicy
    # refuses, so every opening copy was discarded on the first iteration. The
    # opening copy now repeats the boss's own authored opening strike.
    assert 'startEchoAttackReplicas(boss, "VOID_BLAST")' not in text, (
        "VOID_BLAST has no timeline: handing it to the policy silently empties "
        "the whole mechanic"
    )
    assert "transition.entered().contains(BossPhase.RAGE)" in text
    rage = text.index("transition.entered().contains(BossPhase.RAGE)")
    call = text.index("startEchoAttackReplicas(boss,", rage)
    assert "EndRiftAiPolicy.BossSpell.MELEE_SWIPE.name()" in text[rage:call + 200], (
        "contract 15.2 Type A belongs to RAGE, and the copy repeats the strike "
        "the boss actually committed"
    )


def test_the_cap_is_the_policys_three():
    start = body("private boolean startEchoAttackReplicas(")
    assert "EchoAttackReplicaPolicy.MAX_REPLICAS" in start, (
        "the number of copies alive at once must come from the policy"
    )
    assert "EchoAttackReplicaPolicy.replicasForAttack(" in start


def test_aim_locks_and_never_homes():
    start = body("private boolean startEchoAttackReplicas(")
    assert "EchoAttackReplicaPolicy.lockTarget(" in start, (
        "the lock is what stops the copy homing onto a player after telegraph"
    )
    assert "new TargetLock(" in start, (
        "the locked POINT must be stored, not a live entity reference"
    )
    tick = body("private void tickEchoAttackReplicas(")
    # Distance must be measured to the LOCKED POINT, not to the target's
    # current position - otherwise the copy follows the player.
    assert "lock.point().getX()" in tick, (
        "the hit test must use the frozen lock point, or the copy homes"
    )
    # The connection test must GATE the damage. Asserting only that the call
    # appears lets a mutant that calls it and ignores the result pass - which is
    # precisely the "invisible homing" this contract forbids.
    assert "if (EchoAttackReplicaPolicy.strikeConnects(" in tick, (
        "strikeConnects must be an if-gate; calling it and ignoring the boolean "
        "means every locked target is always hit"
    )
    gate = tick.index("if (EchoAttackReplicaPolicy.strikeConnects(")
    # Damage no longer goes through a raw Player.damage call: it goes through
    # the accepted-damage adapter, but it must still live INSIDE the
    # strikeConnects branch.
    assert "applyEchoReplicaDamage(" in tick[gate:], (
        "the strike damage must live inside the strikeConnects branch"
    )


def test_each_damage_source_has_its_own_ledger_key():
    tick = body("private void tickEchoAttackReplicas(")
    assert "timeline.replicaKey()" in tick, (
        "the strike needs its own ledger key"
    )
    assert "timeline.explosionKey()" in tick, (
        "the explosion needs a DIFFERENT ledger key, or strike+explosion "
        "silently collapse into one damage event"
    )
    assert "EchoAttackReplicaPolicy.mayDamage(" in tick, (
        "the ledger must gate the damage"
    )
    strike_key = tick.index("timeline.replicaKey()")
    explosion_key = tick.index("timeline.explosionKey()")
    assert strike_key != explosion_key


def test_replicas_never_survive_a_phase_change():
    text = source()
    assert 'clearEchoAttackReplicas("phase-transition")' in text, (
        "a copy left behind becomes an orphaned hazard in the next phase"
    )
    clear = body("private int clearEchoAttackReplicas(")
    for forgotten in ("echoReplicaTimelines.clear()", "echoReplicaLocks.clear()",
                      "echoReplicaDamageLedger.clear()"):
        assert forgotten in clear


def test_replicas_expire_on_their_own_timeline():
    tick = body("private void tickEchoAttackReplicas(")
    assert "timeline.expiryTick()" in tick, (
        "a copy is a delayed action, not a miniboss; it must expire"
    )
    assert "retireEchoReplica(" in tick


def test_the_replica_path_ticks_from_the_boss_loop():
    text = source()
    assert "tickEchoAttackReplicas(boss);" in text, (
        "without a ticker the copies never strike and never expire"
    )
