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
    # Copies are no longer spawned from one RAGE-transition site. They start on
    # any contact ability cast during RAGE, so the copy repeats the strike the
    # boss actually committed instead of a hardcoded family.
    cast = body("private void executeContactBossAbility(")
    contact_gate = "if (bossPhase == BossPhase.RAGE && isContactBossAbility(spell))"
    assert contact_gate in cast, "contract 15.2 Type A belongs to RAGE"
    assert "startEchoAttackReplicas(boss, spell.name());" in cast[cast.index(contact_gate):], (
        "only contact strikes get copied, and the copy repeats the strike the "
        "boss actually committed"
    )
    # The chest strike is a contact move too, and it starts copies under its own
    # gate - still inside RAGE, still from the committed spell.
    chest_gate = "if (bossPhase == BossPhase.RAGE && bossChestStrikeActive)"
    assert chest_gate in cast, "the chest strike copies only while its strike is live"
    assert "startEchoAttackReplicas(boss, spell.name());" in cast[cast.index(chest_gate):], (
        "the chest-strike copy repeats the committed spell, not a family guess"
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
    # The aim may be refreshed ONLY up to the telegraph lock tick; after that it
    # is frozen. A copy that kept re-aiming would follow a player through their
    # whole dodge, which is the invisible-homing defect this guards.
    freeze = "if (eventTickCounter <= timeline.telegraphLockTick())"
    assert freeze in tick, (
        "the aim must freeze at the telegraph lock tick, or the copy homes"
    )
    assert "new TargetLock(" in tick[tick.index(freeze):], (
        "the only permitted aim refresh is the pre-lock one"
    )
    # The hit test is built from the FROZEN aim direction, not from the target's
    # current position: that is what makes the locked geometry real.
    assert "horizontalDirection(origin, lock.aim())" in tick, (
        "the strike axis must come from the frozen lock aim, or the copy homes"
    )
    assert "flat.normalize().dot(axis)" in tick, (
        "a melee swipe must resolve inside the locked front arc, not at the "
        "player's live position"
    )
    # The connection test must GATE the damage, together with the once-only
    # ledger. Asserting only that geometry appears lets a mutant that computes it
    # and ignores the result pass - precisely the "everyone is always hit" bug.
    gate = "if (!connects || !EchoAttackReplicaPolicy.mayDamage("
    assert gate in tick, (
        "the connection result and the ledger must decide the strike; computing "
        "geometry without gating on it means every locked player is always hit"
    )
    assert "applyEchoReplicaDamage(" in tick[tick.index(gate):], (
        "the strike damage must live inside the connects+ledger branch"
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
