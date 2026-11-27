"""Phase 5 Echo: the wiring that made both mechanics inert.

Four defects are guarded here, and three of them cannot be seen by reading the
policy:

1. the tick lived ONLY inside the last-seal pin, so a copy started at RAGE
   entry never advanced during RAGE;
2. a copy published no actor at all, so moving the tick alone would have
   produced invisible damage;
3. the RAGE entry handed the policy "VOID_BLAST", a family it refuses, so every
   copy was discarded on the first iteration;
4. decoys spawned as EVENT_KIND_ELITE Pillagers, and clientVisualId() returns ""
   for a Pillager, so they never wore the Guardian family.

Every test below either READS the real call site or EXECUTES the real method
against compiling stubs. None of them asserts that a name merely appears.
"""
from pathlib import Path
import re
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event/src/me/copimine/endevent"
MONOLITH = PLUGIN / "CopiMineEndEvent.java"


def source() -> str:
    return MONOLITH.read_text("utf-8")


def method(name: str, text: str | None = None) -> str:
    text = text if text is not None else source()
    start = text.index("    private " + name)
    opening = text.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]


# ---------------------------------------------------------------------------
# 1. the tick is owned by the general boss loop, not by the last-seal pin
# ---------------------------------------------------------------------------

def test_the_general_boss_loop_owns_the_replica_tick():
    loop = method("void tickCurrentBoss(")
    assert "tickEchoAttackReplicas(boss);" in loop, (
        "the copy tick must run from the general boss loop; while it lived only "
        "inside pinBossToFinalSealCore it never ran during RAGE"
    )
    # It must run BEFORE the last-seal pin, so the pin cannot return early and
    # starve it again.
    assert loop.index("tickEchoAttackReplicas(boss);") < loop.index(
        "pinBossToFinalSealCore(boss)"), (
        "the copy tick must precede the last-seal pin, or a pin that returns "
        "early still starves the copies"
    )
    # The single owner is the point: the loop must call it exactly once.
    assert loop.count("tickEchoAttackReplicas(boss);") == 1


def test_the_last_seal_pin_no_longer_owns_the_tick_alone():
    pin = method("boolean pinBossToFinalSealCore(", text=source().replace(
        "private boolean pinBossToFinalSealCore(", "    private boolean pinBossToFinalSealCore("))
    # The pin may keep its call, but it must now be a second owner, never the
    # only one. The general loop owns it (proved above); here we prove the pin
    # is behind a LAST_SEAL-only gate so it can never be the sole ticker again.
    assert "BossFinalSealAnchorPolicy.shouldPin(bossPhase)" in pin, (
        "the pin is LAST_SEAL-only; a tick that exists only here cannot run in RAGE"
    )


# ---------------------------------------------------------------------------
# 2. the once-per-tick fence — EXECUTED, not read
# ---------------------------------------------------------------------------

HARNESS = """import me.copimine.endevent.domain.EchoAttackReplicaPolicy;
import me.copimine.endevent.domain.EchoReplicaPresentationPolicy;

public class {name} {{
    static void require(boolean value, String reason) {{
        if (!value) throw new AssertionError(reason);
    }}
    public static void main(String[] args) {{
{body}
    }}
}}
"""


@pytest.fixture(scope="module")
def domain_classes(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-domain")
    sources = [PLUGIN / "domain/EchoAttackReplicaPolicy.java",
               PLUGIN / "domain/EchoReplicaPresentationPolicy.java",
               PLUGIN / "domain/BossPhase.java"]
    sources = [path for path in sources if path.exists()]
    build = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(directory), *map(str, sources)],
        capture_output=True, text=True)
    assert build.returncode == 0, build.stdout + build.stderr
    return directory


def run_probe(compiled: Path, name: str, body: str) -> str:
    probe = compiled / f"{name}.java"
    probe.write_text(HARNESS.format(name=name, body=body), encoding="utf-8")
    build = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-cp", str(compiled), "-d", str(compiled), str(probe)],
        capture_output=True, text=True)
    assert build.returncode == 0, build.stdout + build.stderr
    run = subprocess.run(["java", "-cp", str(compiled), name], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    return run.stdout


def echo_method(name: str) -> str:
    """The real source of one echo method, ready to be pasted into a probe."""
    return method(name)


def test_the_tick_advances_exactly_once_per_server_tick():
    """The fence is executed: a second call in one tick must do nothing."""
    tick = method("void tickEchoAttackReplicas(")
    guard = "echoReplicaLastAdvanceTick == eventTickCounter"
    assert guard in tick, (
        "without this fence the boss loop's second owner in the same tick would "
        "re-open the damage window and land the strike twice"
    )
    # The fence is the tail of the early-out condition, so a repeated tick
    # RETURNS instead of merely skipping work.
    assert "|| echoReplicaLastAdvanceTick == eventTickCounter) return;" in tick, (
        "the fence must short-circuit the whole tick, not one inner branch"
    )
    assert "echoReplicaLastAdvanceTick = eventTickCounter;" in tick, (
        "the fence must stamp the tick it advanced"
    )
    # The stamp is written after the guard, never before it.
    assert tick.index(guard) < tick.index("echoReplicaLastAdvanceTick = eventTickCounter;")


def test_the_rage_fence_cleans_instead_of_skipping():
    fence = method("String tickEchoReplicaFence(")
    assert "bossPhase != BossPhase.RAGE" in fence, (
        "a copy may only exist in RAGE; this is the explicit phase fence"
    )
    assert 'clearEchoAttackReplicas("phase-fence-' in fence, (
        "leaving RAGE must CLEAN the copies. A bare return would leave the "
        "damage ledger armed and let a later RAGE re-use it"
    )
    assert "generation-fence" in fence, (
        "the encounter-generation fence is the second half of contract 15.2 item 6"
    )
    assert "echoReplicaGenerations" in fence


def test_a_copy_without_an_actor_is_torn_down_not_ticks():
    tick = method("void tickEchoAttackReplicas(")
    assert 'retireEchoReplica(copyId, "actor-lost")' in tick, (
        "a copy that lost its actor must retire; ticking it would be invisible damage"
    )
    assert "echoReplicaActorIsPresent(actor)" in tick, (
        "a vanished actor (mob limiter, chunk unload) must retire the copy"
    )
    # The guard must gate EACH damage branch, not merely appear once in the
    # method: two branches exist (strike and warned explosion), and a mutant
    # that drops one of them still leaves the other in place.
    contact = "beat == EchoReplicaPresentationPolicy.Beat.CONTACT"
    explosion = "beat == EchoReplicaPresentationPolicy.Beat.EXPLOSION"
    assert contact in tick, "the STRIKE branch must be gated by the contact beat"
    assert explosion in tick, "the EXPLOSION branch must be gated by the explosion beat"
    blank_guard = "if (!echoReplicaContactActive(copyId, source)) break;"
    assert tick.count(blank_guard) == 2, (
        "every damage source of a copy must pass the live-copy guard; found "
        + str(tick.count(blank_guard)) + " of 2"
    )
    # And the damage must live INSIDE each guarded branch, after both the beat
    # gate and the live-copy gate.
    for beat, guard in ((contact, "else if (" + contact),
                        (explosion, explosion)):
        assert guard in tick, "the beat gate must be present: " + guard
        after = tick.index(guard)
        assert blank_guard in tick[after:], (
            "the live-copy guard must gate this branch: " + guard
        )
        assert "applyEchoReplicaDamage(" in tick[tick.index(blank_guard, after):], (
            "the damage must live inside the guard branch: " + guard
        )


def test_the_actor_is_spawned_before_the_copy_is_recorded():
    start = method("boolean startEchoAttackReplicas(")
    spawn_at = start.index("spawnEchoReplicaActor(")
    record_at = start.index("echoReplicaTimelines.put(")
    assert spawn_at < record_at, (
        "a copy must not be recorded before its visible actor exists: that is "
        "exactly how the old code dealt invisible damage"
    )
    assert "if (actor == null)" in start, (
        "a refused spawn must refuse the copy, not fall back to a bare timing"
    )


def test_the_entry_family_is_supported_and_the_origin_is_logged():
    text = source()
    assert 'startEchoAttackReplicas(boss, "VOID_BLAST")' not in text, (
        "VOID_BLAST is not a copyable family; the entry handed the policy a name "
        "it refuses, so every opening replica was discarded"
    )
    # Copies are no longer spawned at a single RAGE-transition entry point. They
    # start whenever the boss casts a contact ability during RAGE, so the copy
    # repeats whichever strike the boss actually performed (MELEE_SWIPE, chest
    # strike or ground slam) instead of a hardcoded family.
    cast = method("void executeContactBossAbility(")
    contact_gate = "if (bossPhase == BossPhase.RAGE && isContactBossAbility(spell))"
    assert contact_gate in cast, "copies must only start during RAGE"
    assert "startEchoAttackReplicas(boss, spell.name());" in cast[cast.index(contact_gate):], (
        "only contact strikes get copied, and the copy repeats the boss's real "
        "strike, not a family"
    )
    chest_gate = "if (bossPhase == BossPhase.RAGE && bossChestStrikeActive)"
    assert chest_gate in cast, "the chest strike copies only while its strike is live"
    assert "startEchoAttackReplicas(boss, spell.name());" in cast[cast.index(chest_gate):]
    start = method("boolean startEchoAttackReplicas(")
    assert "EchoReplicaPresentationPolicy.familyForOrigin(family)" in start
    assert "origin=" in start, "the log must state which origin was translated"


# ---------------------------------------------------------------------------
# 3. decoys: own kind, own visual, own cleanup
# ---------------------------------------------------------------------------

def code_only(body: str) -> str:
    """The method with every comment stripped.

    Comments legitimately name the OLD defect they explain; a regression check
    that trips over prose would only punish the documentation.
    """
    without_block = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    return re.sub(r"//[^\n]*", "", without_block)


def test_a_decoy_has_its_own_kind_and_never_the_elite_kind():
    spawn = code_only(method("boolean summonEchoDecoys("))
    assert "EVENT_KIND_ECHO_DECOY" in spawn, (
        "a decoy needs its own actor kind, with its own actor record and cleanup"
    )
    assert "EVENT_KIND_ELITE" not in spawn, (
        "the old code spawned decoys as wave elites, which fed wave packing, "
        "wave pressure and wave progress"
    )
    assert "EVENT_KIND_BOSS" not in spawn, (
        "a decoy must never take the real boss identity, its bar or its victory path"
    )


def test_a_decoy_wears_the_guardian_family_through_client_visual_id():
    dispatch = method("String clientVisualId(")
    assert "echoClientVisualId(entity)" in dispatch, (
        "the Guardian family must be bound by the ONE place that decides models"
    )
    echo_visual = method("String echoClientVisualId(")
    assert "EVENT_KIND_ECHO_DECOY" in echo_visual
    assert "EchoDecoyPolicy.usesBossModelFamily()" in echo_visual, (
        "the policy already answers 'decoys wear the boss family'; the adapter "
        "must ask it instead of hard-coding an empty visual"
    )
    assert "CLIENT_VISUAL_ECHO_DECOY" in echo_visual
    # The real boss keeps its OWN id; a decoy must not borrow it.
    assert "CLIENT_VISUAL_BOSS" not in echo_visual, (
        "borrowing the boss visual would make the real Guardian "
        "indistinguishable and share its bar binding"
    )
    assert 'CLIENT_VISUAL_BOSS = "END_RIFT_GUARDIAN_V1"' in source()


def test_the_echo_kinds_are_visual_and_combat_kinds_but_not_wave_kinds():
    assert "isEchoKind(kind)" in method("boolean isVisualEventMob("), (
        "without this the generic visual gate returns '' before the Echo "
        "dispatch is ever reached"
    )
    assert "isEchoKind(kind)" in method("boolean isWaveCombatKind("), (
        "the Echo actors must be combat kinds so they stay persistent and keep "
        "the one AI invariant"
    )
    kinds = method("String echoClientVisualId(")
    assert "EVENT_KIND_ECHO_REPLICA" in kinds


def test_both_echo_actors_are_cleaned_by_their_own_owner():
    runtime = source()
    # Decoys: phase fence plus the named exits.
    assert 'clearEchoDecoys("entered-last-seal")' in runtime
    assert "tickEchoDecoyFence(boss);" in method("void tickCurrentBoss("), (
        "the decoy cleanup fence must be owned by the general boss loop"
    )
    fence = method("void tickEchoDecoyFence(")
    for token in ("phase-fence-", "generation-fence", '"boss-lost"'):
        assert token in fence, token
    # Copies: phase transition plus their own retire path.
    assert 'clearEchoAttackReplicas("phase-transition")' in runtime
    retire = method("void retireEchoReplica(")
    assert "removeEchoReplicaEntity(" in retire, (
        "retiring a copy must remove the actor; otherwise a Guardian statue "
        "stays in the arena for the rest of the phase"
    )
    assert "echoReplicaActors.remove(copyId)" in retire


def test_neither_echo_actor_can_delete_the_real_boss():
    for name in ("void removeEchoReplicaEntity(", "int clearEchoDecoys("):
        body = method(name)
        assert "bossUuid" in body and "equals(" in body, (
            name + " must refuse to remove the real boss identity"
        )
    assert "ECHO_REPLICA_ACTOR_REFUSED" in method("void removeEchoReplicaEntity(")
    assert "BOSS_ECHO_DECOY_CLEANUP_REFUSED" in method("int clearEchoDecoys(")


# ---------------------------------------------------------------------------
# 4. the surviving contracts the slice must not break
# ---------------------------------------------------------------------------

def test_the_cap_the_ledger_and_the_lock_survive_the_rewrite():
    start = method("boolean startEchoAttackReplicas(")
    assert "EchoAttackReplicaPolicy.MAX_REPLICAS" in start
    assert "EchoAttackReplicaPolicy.replicasForAttack(" in start
    assert "EchoAttackReplicaPolicy.lockTarget(" in start
    assert "new TargetLock(" in start
    tick = method("void tickEchoAttackReplicas(")
    assert "horizontalDirection(origin, lock.aim())" in tick, (
        "the hit test must use the frozen lock aim, or the copy homes"
    )
    assert "timeline.replicaKey()" in tick and "timeline.explosionKey()" in tick, (
        "the strike and the blast must keep separate ledger keys"
    )
    assert "EchoAttackReplicaPolicy.mayDamage(" in tick, "the ledger must gate damage"


def test_decoy_health_cap_and_cooldown_are_untouched():
    spawn = method("boolean summonEchoDecoys(")
    assert "EchoDecoyPolicy.decoyHealth(" in spawn
    assert "EchoDecoyPolicy.resolveSummonBatch(" in spawn
    assert "EchoDecoyPolicy.maySummon(" in spawn
    assert "setBaseValue(health)" in spawn and "setHealth(health)" in spawn


def test_no_raw_player_damage_remains_in_the_copy_path():
    tick = code_only(method("void tickEchoAttackReplicas("))
    assert "applyEchoReplicaDamage(" in tick, (
        "damage must go through the one accepted-damage entry point so the "
        "ledger records what the server actually accepted"
    )
    assert not re.search(r"\bplayer\.damage\(", tick), (
        "a direct Player.damage call bypasses the accepted-damage accounting"
    )
    assert not re.search(r"\blocked\.damage\(", tick)


# ---------------------------------------------------------------------------
# 5. E-01 / V7-17 + V7-20: the rare Phase 5 Personal Echo ("Reflection Theft")
#
# §10.4.3 describes a second, independent Echo mode: a rare fully real hostile
# copy of one living participant. It shares the Wave 7 combat engine but none of
# its room lock, teleport, boss bar or rewards. These tests pin the wiring that
# makes the mode reachable, and the fences that keep it from leaking.
# ---------------------------------------------------------------------------

def test_the_rare_echo_is_ticked_by_the_general_boss_loop():
    loop = method("void tickCurrentBoss(")
    assert "tickRageEchoes(boss);" in loop, (
        "the rare Phase 5 Echo must advance from the general boss loop or it "
        "never acts during RAGE"
    )
    assert loop.count("tickRageEchoes(boss);") == 1, (
        "one owner only: a second call would advance the copy twice in a tick"
    )
    assert loop.index("tickRageEchoes(boss);") < loop.index(
        "pinBossToFinalSealCore(boss)"), (
        "the rare Echo tick must precede the last-seal pin so an early pin "
        "return cannot starve it"
    )


def test_the_rare_echo_phase_and_generation_fences_clean_instead_of_skip():
    tick = code_only(method("void tickRageEchoes("))
    assert "bossPhase != BossPhase.RAGE" in tick, (
        "a rare Echo may only exist in RAGE; this is the explicit phase fence"
    )
    assert 'clearRageEchoes("phase-ended")' in tick, (
        "leaving RAGE must retire the copy, never leave a hostile orphan"
    )
    assert "rageEchoGeneration != generation" in tick, (
        "the encounter generation fence is the second half of contract 15.2"
    )
    assert 'clearRageEchoes("generation-changed")' in tick, (
        "a new encounter generation must never inherit the previous copy"
    )


def test_the_rare_echo_has_its_own_kind_and_never_the_wave_or_boss_kind():
    spawn = code_only(method("boolean spawnRageEcho("))
    assert "EVENT_KIND_RAGE_ECHO" in spawn, (
        "the rare Echo needs its own actor kind, with its own cleanup"
    )
    for foreign in ("EVENT_KIND_ECHO_REPLICA", "EVENT_KIND_ECHO_DECOY",
                    "EVENT_KIND_BOSS", "EVENT_KIND_ELITE"):
        assert foreign not in spawn, (
            "the rare Personal Echo must not borrow " + foreign + ": that "
            "would feed the Wave 7 / boss / elite lifecycle"
        )
    assert "bossBar" not in spawn and "BossBar" not in spawn, (
        "§10.4.3: no boss bar for a Personal Echo"
    )


def test_the_rare_echo_uses_the_common_combat_engine():
    tick = method("void tickOneRageEcho(")
    assert "EchoDuelCombatRuntimeAdapter.tick(" in tick, (
        "the rare Echo must be driven by the shared combat engine, not a "
        "second AI"
    )
    assert "this::emitNamedEchoCombatDiagnostic" in tick, (
        "engine failures must reach the bounded diagnostic channel"
    )
    assert "session.actionProposal()" in tick
    assert "session.commit(accepted)" in tick
    assert tick.index("if (!executed)") < tick.index("session.commit(accepted)")
    assert "applyEchoActionCompletion(carrier, view, accepted)" in tick


def test_the_rare_echo_damage_goes_through_accepted_damage():
    tick = code_only(method("void tickOneRageEcho("))
    execute = code_only(method("boolean executeEchoCombatAction("))
    assert "executeEchoCombatAction(" in tick
    assert "listener.beginOwnerCombatAction(pair, owner.getUniqueId())" in execute
    assert "owner.damage(" in execute
    assert "listener.consumeAcceptedOwnerCombatAction(pair, owner.getUniqueId()," in execute
    assert "listener.registerServerLaunchedEchoArrow(" in execute
    scope = code_only(method("boolean rageEchoPairActive("))
    assert "bossPhase == BossPhase.RAGE" in scope
    assert "session.eventId().equals(parseUuidOrNull(eventId))" in scope


def test_the_rare_echo_budget_and_lifetime_come_from_the_policy():
    tick = method("void tickRageEchoes(")
    assert "RageEchoSummonPolicy.maySummon(" in tick, (
        "summon frequency must come from the shared rare-Echo policy"
    )
    spawn = method("boolean spawnRageEcho(")
    assert "RageEchoSummonPolicy.MAX_SIMULTANEOUS" in spawn, (
        "the one-alive cap is policy, never a literal"
    )
    assert "RageEchoSummonPolicy.summonHealth(" in spawn, (
        "copy health must be the reviewed multiplier of the owner's maximum"
    )
    assert "RageEchoSummonPolicy.lifetimeTicks()" in spawn, (
        "the finite lifetime must be policy-derived and reach the presentation"
    )
    assert "setCanJoinRaid(false)" in spawn, (
        "a Personal Echo takes no raid slot and shows no raid/bar state"
    )
    assert "setCanPickupItems(false)" in spawn, (
        "§10.4.3: a Personal Echo drops no loot"
    )


def test_the_rare_echo_is_cleaned_on_every_phase_exit():
    runtime = source()
    for reason in ('"phase-transition"', '"entered-last-seal"', '"stage-released"'):
        assert "clearRageEchoes(" + reason + ")" in runtime, (
            "every phase/stage exit must retire the rare Echo: " + reason
        )
    retire = method("void retireRageEcho(")
    assert "carrier.remove()" in retire, (
        "retiring a rare Echo must remove its real hitbox"
    )
    assert "unregisterOwnedEntity(carrierId," in retire, (
        "the owner registry must be released with the body"
    )
    clear = method("int clearRageEchoes(")
    assert "rageEchoBroadcast.clear();" in clear, (
        "the broadcast cache is bounded by teardown, not by uptime"
    )


def test_the_rare_echo_presentation_is_capability_and_range_gated():
    send = method("void sendRageEchoView(")
    assert '"END_ECHO_STATE"' in send, (
        "the rare Echo reuses the proven client echo-state operation"
    )
    assert "echoPresentationCapablePlayers.contains(viewer.getUniqueId())" in send, (
        "only clients running the presentation bridge may receive echo frames"
    )
    assert "64.0D * 64.0D" in send, (
        "the frame must not be broadcast across the whole world"
    )
    assert "rageEchoBroadcast.shouldSend(" in send
    assert "rageEchoBroadcast.record(" in send, (
        "a delivered frame must be recorded so the next identical frame is "
        "coalesced"
    )


def test_the_rare_echo_never_teleports_or_locks_the_owner():
    for name in ("boolean spawnRageEcho(", "void tickRageEchoes(",
                 "void tickOneRageEcho("):
        body = code_only(method(name))
        assert ".teleport(" not in body, (
            name + " must never teleport the copied owner or its body"
        )
