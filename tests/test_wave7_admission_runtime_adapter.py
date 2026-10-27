"""Exercise the real Wave 7 admission adapter against the real controllers."""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "copimine-end-event/src/me/copimine/endevent"

PRODUCTION = [
    "domain/ChamberIsolationPolicy.java",
    "domain/RealitySplitBarrierPolicy.java",
    "domain/Wave7AdmissionPolicy.java",
    "domain/Wave7RoomLayoutPolicy.java",
    "domain/Wave7EchoDuelCellPolicy.java",
    "domain/wave7/EchoBehaviorStrategy.java",
    "domain/wave7/EchoLoadoutState.java",
    "runtime/RealitySplitChamberController.java",
    "runtime/RealitySplitTrialController.java",
    "runtime/RealitySplitEchoDuelState.java",
    "runtime/wave7/Wave7AdmissionRuntimeAdapter.java",
]

HARNESS = r'''
import java.util.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.domain.wave7.*;
import me.copimine.endevent.runtime.*;
import me.copimine.endevent.runtime.RealitySplitTrialController.*;
import me.copimine.endevent.runtime.wave7.Wave7AdmissionRuntimeAdapter;
import me.copimine.endevent.runtime.wave7.Wave7AdmissionRuntimeAdapter.Decision;

public class Wave7AdmissionRuntimeChecks {
    static UUID id(int n) { return new UUID(0, n); }
    static void require(boolean ok, String why) { if (!ok) throw new AssertionError(why); }

    static final long GEN = 73L;
    static final UUID EVENT = id(90);
    static final UUID A = id(1), B = id(2), C = id(3), D = id(4), E = id(5);
    static final UUID WARDEN = id(11), ECHO = id(12), MARKSMAN = id(13), ARCHMAGE = id(14),
            STRAY = id(15), OTHER = id(16);
    static final ChamberIsolationPolicy.Assignment ASSIGNMENT =
            new ChamberIsolationPolicy.Assignment(4, Map.of(A, 0, B, 1, C, 2, D, 3, E, 1));
    static final List<NamedTrial> LAYOUT = List.of(NamedTrial.WARDEN, NamedTrial.PERSONAL_ECHO,
            NamedTrial.MOBILE_MARKSMAN, NamedTrial.RIFT_ARCHMAGE);

    static final Map<UUID, Integer> rooms = new HashMap<>();
    static final long[] gen = {GEN};
    static RealitySplitChamberController chambers = new RealitySplitChamberController();
    static RealitySplitTrialController trials = new RealitySplitTrialController();
    static Wave7AdmissionRuntimeAdapter adapter;

    static void boot() {
        chambers.restore(GEN, ASSIGNMENT, Set.of(), Set.of());
        trials.beginNamed(EVENT, GEN, ASSIGNMENT, LAYOUT);
        rooms.put(A, 0); rooms.put(B, 1); rooms.put(C, 2); rooms.put(D, 3); rooms.put(E, 1);
        chambers.assignEntity(GEN, WARDEN, 0);
        chambers.assignEntity(GEN, ECHO, 1);
        chambers.assignEntity(GEN, MARKSMAN, 2);
        chambers.assignEntity(GEN, ARCHMAGE, 3);
        adapter = new Wave7AdmissionRuntimeAdapter(chambers, trials, () -> gen[0], rooms::get);
    }

    static void bindEchoActor() {
        var planned = trials.namedSnapshot().personalEchoDuels().get(1).get(B);
        var loadout = EchoLoadoutState.create(EVENT, GEN, planned.duelId(), B,
                Collections.nCopies(41, EchoLoadoutState.Item.empty()), 0);
        var actor = new RealitySplitEchoDuelState.ActorState(ECHO, 20.0D, 20.0D,
                RealitySplitEchoDuelState.ActorLifecycle.ALIVE, 0L, 0L, 0L, 0L, 0L, 0L);
        var strategy = new EchoBehaviorStrategy(500, 500, 500, 500, 500, 500, 0, true);
        require(trials.registerPersonalEchoDuel(planned.withActor(GEN, 20.0D, 20.0D, 0L,
                actor, 1L, 0L, loadout.encode(), strategy)), "Echo replica must bind to its owner duel");
    }

    public static void main(String[] args) {
        boot();
        String s = args[0];
        switch (s) {
            case "warden-owner-allowed" -> require(adapter.target(WARDEN, A) == Decision.ALLOW,
                    "the Warden may hit the owner standing in its own room");
            case "warden-cross-room-denied" -> require(adapter.target(WARDEN, C) == Decision.DENY,
                    "a Warden target in another live room must be refused");
            case "warden-neutral-denied" -> {
                rooms.put(A, -1);
                require(adapter.target(WARDEN, A) == Decision.DENY,
                        "the neutral Core column is not a legal target platform");
            }
            case "echo-owner-allowed" -> {
                bindEchoActor();
                require(adapter.target(ECHO, B) == Decision.ALLOW,
                        "a personal Echo may hit its own unfinished owner");
            }
            case "echo-foreign-owner-denied" -> {
                bindEchoActor();
                require(adapter.target(ECHO, E) == Decision.DENY,
                        "one owner's personal Echo must not hit the other owner");
            }
            case "echo-unbound-actor-denied" -> {
                chambers.assignEntity(GEN, STRAY, 1);
                require(adapter.target(STRAY, B) == Decision.DENY,
                        "an Echo-room actor without a live duel may not hit anyone");
            }
            case "echo-passage-filtered" -> {
                bindEchoActor();
                require(chambers.markChamberComplete(GEN, 0), "room 0 must complete");
                require(chambers.openCompletedPassage(GEN, 0, 1), "passage 0-1 must open");
                require(adapter.snapshot() != null,
                        "an Echo-adjacent passage must not invalidate the whole attempt");
                require(adapter.target(WARDEN, A) == Decision.ALLOW,
                        "ordinary gating survives an Echo-adjacent passage");
            }
            case "marksman-helper-allowed" -> {
                require(trials.completeNamedActor(EVENT, GEN, 0, NamedTrial.WARDEN), "room 0 completion");
                rooms.put(A, 2);
                require(adapter.target(MARKSMAN, A) == Decision.ALLOW,
                        "a helper admitted into the Marksman room is a legal target");
            }
            case "helper-unfinished-denied" -> {
                rooms.put(A, 2);
                require(adapter.target(MARKSMAN, A) == Decision.DENY,
                        "an owner who has not finished their own claim cannot be a helper");
            }
            case "launch-scope-frozen" -> {
                var first = id(201);
                require(adapter.launch(first, MARKSMAN) == Decision.ALLOW, "first launch");
                require(trials.completeNamedActor(EVENT, GEN, 3, NamedTrial.RIFT_ARCHMAGE),
                        "room 3 completion");
                require(chambers.markChamberComplete(GEN, 2), "room 2 room-graph completion");
                require(chambers.openCompletedPassage(GEN, 2, 3), "passage 2-3");
                rooms.put(C, 3);
                require(adapter.damage(first, MARKSMAN, C) == Decision.DENY,
                        "an airborne projectile keeps its launch room scope");
                var second = id(202);
                require(adapter.launch(second, MARKSMAN) == Decision.ALLOW, "second launch");
                require(adapter.damage(second, MARKSMAN, C) == Decision.ALLOW,
                        "a new launch may use the region completed since");
            }
            case "untracked-projectile-denied" -> require(
                    adapter.damage(id(998), MARKSMAN, A) == Decision.DENY,
                    "an unrecorded projectile from a Wave 7 actor is refused");
            case "foreign-projectile-ignored" -> require(
                    adapter.damage(id(999), OTHER, A) == Decision.IGNORE,
                    "an unrelated shooter is never touched by Wave 7 admission");
            case "stale-generation-denied" -> {
                rooms.put(C, 2);
                var shot = id(203);
                require(adapter.launch(shot, MARKSMAN) == Decision.ALLOW, "launch in the live attempt");
                gen[0] = GEN + 1;
                chambers.restore(GEN + 1, ASSIGNMENT, Set.of(), Set.of());
                trials.beginNamed(EVENT, GEN + 1, ASSIGNMENT, LAYOUT);
                chambers.assignEntity(GEN + 1, MARKSMAN, 2);
                require(adapter.damage(shot, MARKSMAN, C) == Decision.DENY,
                        "a launch receipt from an older generation cannot resolve");
            }
            case "legacy-ignored" -> {
                var legacyChambers = new RealitySplitChamberController();
                var legacyTrials = new RealitySplitTrialController();
                legacyChambers.restore(GEN, ASSIGNMENT, Set.of(), Set.of());
                legacyTrials.begin(GEN, ASSIGNMENT);
                legacyChambers.assignEntity(GEN, WARDEN, 0);
                var legacy = new Wave7AdmissionRuntimeAdapter(legacyChambers, legacyTrials,
                        () -> GEN, rooms::get);
                require(legacy.target(WARDEN, C) == Decision.IGNORE,
                        "the legacy unnamed layout is not gated by the named policy");
                require(legacy.launch(id(300), WARDEN) == Decision.IGNORE,
                        "a legacy launch is left to the existing wave runtime");
            }
            case "inactive-ignored" -> {
                gen[0] = 0L;
                require(adapter.target(WARDEN, C) == Decision.IGNORE,
                        "an inactive attempt must not gate anything");
                require(adapter.launch(id(301), WARDEN) == Decision.IGNORE,
                        "an inactive attempt must not cancel launches");
            }
            case "scope-cap-evicts" -> {
                for (int i = 0; i < 64; i++) {
                    require(adapter.launch(id(400 + i), MARKSMAN) == Decision.ALLOW, "launch " + i);
                }
                require(adapter.trackedScopes() == 64, "scope budget is bounded");
                require(adapter.launch(id(500), MARKSMAN) == Decision.ALLOW,
                        "a full budget evicts instead of locking the actor out");
                require(adapter.trackedScopes() == 64, "scope budget stays bounded");
                require(adapter.damage(id(400), MARKSMAN, C) == Decision.DENY,
                        "an evicted receipt cannot resolve");
            }
            default -> throw new AssertionError("unknown " + s);
        }
        require(adapter.trackedScopes() >= 0, "adapter stays usable");
    }
}
'''


@pytest.fixture(scope="module")
def admission_probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("wave7-admission-runtime")
    harness = directory / "Wave7AdmissionRuntimeChecks.java"
    harness.write_text(HARNESS, encoding="utf-8")
    sources = [str(BASE / relative) for relative in PRODUCTION]
    missing = [path for path in sources if not Path(path).exists()]
    assert not missing, f"missing production sources: {missing}"
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(directory), *sources, str(harness)],
        capture_output=True, text=True,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return directory


@pytest.mark.parametrize("scenario", [
    "warden-owner-allowed", "warden-cross-room-denied", "warden-neutral-denied",
    "echo-owner-allowed", "echo-foreign-owner-denied", "echo-unbound-actor-denied",
    "echo-passage-filtered", "marksman-helper-allowed", "helper-unfinished-denied",
    "launch-scope-frozen", "untracked-projectile-denied", "foreign-projectile-ignored",
    "stale-generation-denied", "legacy-ignored", "inactive-ignored", "scope-cap-evicts",
])
def test_adapter_admits_only_the_named_wave7_action(admission_probe, scenario):
    result = subprocess.run(
        ["java", "-cp", str(admission_probe), "Wave7AdmissionRuntimeChecks", scenario],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr


def test_listener_is_cancelling_and_not_rewriting_events():
    source = (BASE / "runtime/wave7/Wave7AdmissionListener.java").read_text(encoding="utf-8")
    assert source.count("@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)") == 3
    assert "event.setCancelled(true)" in source
    assert "setDamage(" not in source