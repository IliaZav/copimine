"""W6-002 [U]: the Wave 6 stage sequence owns who exists right now.

Runs the production ``WaveSixSequencePolicy`` in a real JVM. These are not
native Minecraft checks; they prove the pure state machine that the server
adapter drives: one mage at a time, identity-based signatures, exactly one
mandatory reinforcement pack per death, and a fail-closed completion gate.
"""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "copimine-end-event/src"

PROBE = r'''import java.util.*;
import me.copimine.endevent.domain.*;

public class WaveSixSequenceProbe {
    static int failures = 0;
    static void check(boolean ok, String message) { if (!ok) { System.out.println("FAIL: " + message); failures++; } }

    static List<MageSignaturePolicy.MageId> order() {
        return List.of(MageSignaturePolicy.MageId.ORRINTH, MageSignaturePolicy.MageId.VESPERAL,
                MageSignaturePolicy.MageId.NOCTURN, MageSignaturePolicy.MageId.KAILEN,
                MageSignaturePolicy.MageId.MALZURETH);
    }

    static WaveSixSequencePolicy finishStage(WaveSixSequencePolicy state, int guards) {
        if (!state.mageStageActive()) return state;
        for (int slot = 0; slot < guards; slot++) state = state.guardFilled(slot);
        return state;
    }

    public static void main(String[] args) {
        switch (args[0]) {
            case "one-mage-at-a-time" -> {
                var s = WaveSixSequencePolicy.start(order(), 5);
                check(s.currentMage() == order().get(0), "only the first mage exists");
                check(s.stageIndex() == 0, "stage starts at zero");
                check(!s.stageRosterComplete(), "roster is incomplete until every guard spawns");
                check(s.currentSignature() == MageSignaturePolicy.signatureFor(order().get(0)), "signature of the current mage");
            }
            case "early-defeat-rejected" -> {
                var s = WaveSixSequencePolicy.start(order(), 3);
                var r = s.defeatCurrent(s.currentMage());
                check(!r.accepted(), "a mage cannot die before its stage finished spawning");
                check(r.blocker() == WaveSixSequencePolicy.Blocker.STAGE_INCOMPLETE, "blocker is STAGE_INCOMPLETE");
            }
            case "duplicate-and-foreign-rejected" -> {
                var s = finishStage(WaveSixSequencePolicy.start(order(), 3), 3);
                var first = s.currentMage();
                var r = s.defeatCurrent(first);
                check(r.accepted(), "the current mage advances the stage");
                var again = r.state().defeatCurrent(first);
                check(!again.accepted(), "a duplicate death callback is rejected");
                check(again.blocker() == WaveSixSequencePolicy.Blocker.NOT_CURRENT_MAGE, "duplicate is NOT_CURRENT_MAGE");
                var foreign = r.state().defeatCurrent(order().get(4));
                check(!foreign.accepted(), "a mage outside the current stage is rejected");
            }
            case "signature-follows-identity" -> {
                var a = finishStage(WaveSixSequencePolicy.start(order(), 1), 1);
                var firstA = a.defeatCurrent(a.currentMage());
                var swapped = List.of(order().get(1), order().get(0), order().get(2),
                        order().get(3), order().get(4));
                var b = finishStage(WaveSixSequencePolicy.start(swapped, 1), 1);
                var firstB = b.defeatCurrent(b.currentMage());
                check(firstA.acquiredSignature() != firstB.acquiredSignature(),
                        "the first death yields a different signature depending on WHO died");
                check(firstA.acquiredSignature() == MageSignaturePolicy.signatureFor(order().get(0)),
                        "the acquired signature belongs to the defeated identity");
            }
            case "all-signatures-unique" -> {
                var base = new ArrayList<>(List.of(MageSignaturePolicy.MageId.values()));
                int permutations = 0;
                do {
                    var state = finishStage(WaveSixSequencePolicy.start(List.copyOf(base), 2), 2);
                    var seen = new LinkedHashSet<MageSignaturePolicy.Signature>();
                    for (int i = 0; i < 5; i++) {
                        var r = state.defeatCurrent(state.currentMage());
                        check(r.accepted(), "every stage of a permutation is accepted");
                        seen.add(r.acquiredSignature());
                        state = finishStage(r.state(), 2);
                    }
                    check(seen.size() == 5, "five distinct signatures for " + base);
                    check(state.reinforcementPackReceipts() == 5, "five mandatory packs owed");
                    permutations++;
                } while (nextPermutation(base));
                check(permutations == 120, "all 120 orders examined, saw " + permutations);
            }
            case "shield-uses-assigned-guards-only" -> {
                var s = WaveSixSequencePolicy.start(order(), 4);
                check(s.shieldHolds(4), "shield holds with assigned guards");
                check(s.lens(4) == WaveSixSequencePolicy.Lens.GUARDED, "guarded while assigned guards live");
                check(s.lens(0) == WaveSixSequencePolicy.Lens.EXPOSED, "exposed when the assigned guards die");
                check(!s.shieldHolds(0), "no shield without assigned guards");
                var done = finishStage(s, 4);
                var r = done.defeatCurrent(done.currentMage());
                check(r.state().lens(0) == WaveSixSequencePolicy.Lens.EXPOSED, "the exposed mage has no guard shield");
                check(r.state().lens(r.state().guardSlots()) == WaveSixSequencePolicy.Lens.GUARDED,
                        "the next stage starts guarded once its guards live");
            }
            case "completion-requires-final-pack" -> {
                var state = finishStage(WaveSixSequencePolicy.start(order(), 1), 1);
                for (int i = 0; i < 5; i++) {
                    var r = state.defeatCurrent(state.currentMage());
                    check(r.accepted(), "stage " + i + " accepted");
                    state = finishStage(r.state(), 1);
                }
                check(state.prisonBroken(), "the fifth death breaks the prison");
                check(!state.mageStageActive(), "no mage remains");
                check(!state.mayComplete(4, true), "four released packs are not enough");
                check(!state.mayComplete(5, false), "an unclean runtime blocks completion");
                check(state.mayComplete(5, true), "five released packs and a clean runtime complete");
                check(!state.defeatCurrent(order().get(0)).accepted(), "nothing can die after the fifth");
                check(state.lens(0) == WaveSixSequencePolicy.Lens.COMPLETE, "no lens remains after the fifth");
            }
            case "restore-is-strict" -> {
                var order = order();
                var s = finishStage(WaveSixSequencePolicy.start(order, 3), 3);
                var r = s.defeatCurrent(s.currentMage());
                var restored = WaveSixSequencePolicy.restore(order, 1, r.state().defeated(), false, 3, Set.of());
                check(restored.currentMage() == order.get(1), "a restart resumes the same next mage");
                boolean threw = false;
                try {
                    WaveSixSequencePolicy.restore(order, 1, List.of(order.get(1)), false, 3, Set.of());
                } catch (IllegalArgumentException expected) {
                    threw = true;
                }
                check(threw, "a tampered defeated prefix is rejected");
            }
            default -> throw new AssertionError("unknown scenario " + args[0]);
        }
        System.out.println(failures == 0 ? "OK" : "FAILURES=" + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    static boolean nextPermutation(List<MageSignaturePolicy.MageId> list) {
        int i = list.size() - 2;
        while (i >= 0 && list.get(i).compareTo(list.get(i + 1)) >= 0) i--;
        if (i < 0) return false;
        int j = list.size() - 1;
        while (list.get(j).compareTo(list.get(i)) <= 0) j--;
        Collections.swap(list, i, j);
        Collections.reverse(list.subList(i + 1, list.size()));
        return true;
    }
}
'''


@pytest.fixture(scope="module")
def sequence_probe(tmp_path_factory):
    folder = tmp_path_factory.mktemp("wave6-sequence")
    java = folder / "WaveSixSequenceProbe.java"
    java.write_text(PROBE, encoding="utf-8")
    compiled = subprocess.run(
        ["javac", "-J-Xmx256m", "-proc:none", "-encoding", "UTF-8",
         "-sourcepath", str(SRC), "-d", str(folder), str(java)],
        capture_output=True, text=True, timeout=120)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return folder


@pytest.mark.parametrize("scenario", [
    "one-mage-at-a-time",
    "early-defeat-rejected",
    "duplicate-and-foreign-rejected",
    "signature-follows-identity",
    "all-signatures-unique",
    "shield-uses-assigned-guards-only",
    "completion-requires-final-pack",
    "restore-is-strict",
])
def test_wave_six_sequence_is_owned_by_one_state_machine(sequence_probe, scenario):
    result = subprocess.run(["java", "-Xmx128m", "-cp", str(sequence_probe),
                             "WaveSixSequenceProbe", scenario],
                            capture_output=True, text=True, timeout=60)
    assert result.returncode == 0, result.stdout + result.stderr
