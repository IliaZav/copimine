import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.runtime.RealitySplitTrialController;

public final class RealitySplitTrialControllerTest {
    public static void main(String[] args) {
        UUID[] players = {
                player("trial-a"), player("trial-b"), player("trial-c"), player("trial-d"),
                player("trial-e"), player("trial-f")
        };

        RealitySplitTrialController solo = new RealitySplitTrialController();
        solo.begin(41L, ChamberIsolationPolicy.assign(List.of(players[0])));
        check(solo.activeTrialCount() == 1, "a solo roster gets one appropriate trial room");
        check(solo.trial(0).trial() == RealitySplitTrialController.Trial.WARDEN,
                "the first active room hosts the Warden trial");
        check(solo.defeatWarden(41L, 0).complete(), "Warden death completes its room");
        check(solo.allComplete(41L), "a one-room encounter completes after its trial");

        RealitySplitTrialController reflection = new RealitySplitTrialController();
        reflection.begin(42L, ChamberIsolationPolicy.assign(List.of(players[0], players[1])));
        check(reflection.activeTrialCount() == 2, "a duo roster receives two trial rooms");
        check(!reflection.hitReflectionSeal(42L, 1, 0, false).accepted(),
                "melee cannot advance the Reflection trial");
        check(reflection.trial(1).progress() == 0, "rejected melee leaves the active seal unchanged");
        check(!reflection.hitReflectionSeal(42L, 1, 1, true).accepted(),
                "only the active seal accepts a reflected projectile");
        for (int seal = 0; seal < 3; seal++) {
            var result = reflection.hitReflectionSeal(42L, 1, seal, true);
            check(result.accepted(), "the current seal accepts a reflected projectile");
            check(result.complete() == (seal == 2), "exactly three seals complete the trial");
        }
        check(!reflection.hitReflectionSeal(41L, 1, 0, true).accepted(),
                "a previous encounter generation cannot change trial progress");

        RealitySplitTrialController juggernaut = new RealitySplitTrialController();
        juggernaut.begin(43L, ChamberIsolationPolicy.assign(
                List.of(players[0], players[1], players[2])));
        check(juggernaut.activeTrialCount() == 3, "three players use three trial rooms");
        check(juggernaut.trial(2).trial() == RealitySplitTrialController.Trial.JUGGERNAUT,
                "the third room hosts the Juggernaut trial");
        check(!juggernaut.mayDamageJuggernaut(43L, 2),
                "ordinary damage cannot bypass the three armor anchors");
        check(!juggernaut.hitJuggernautAnchor(43L, 2, 1, true).accepted(),
                "anchors must be hit in sequence");
        for (int anchor = 0; anchor < 3; anchor++) {
            var result = juggernaut.hitJuggernautAnchor(43L, 2, anchor, true);
            check(result.accepted(), "a valid charge collision breaks the next armor stage");
            check(result.stage() == (anchor == 2
                    ? RealitySplitTrialController.Stage.EXPOSED
                    : RealitySplitTrialController.Stage.ACTIVE),
                    "the Juggernaut is exposed only after the third anchor");
        }
        check(juggernaut.defeatJuggernaut(43L, 2).complete(),
                "the exposed Juggernaut can be finished to complete the room");

        RealitySplitTrialController fullParty = new RealitySplitTrialController();
        fullParty.begin(44L, ChamberIsolationPolicy.assign(List.of(players)));
        check(fullParty.activeTrialCount() == 4, "four or more players use all four trials");
        check(fullParty.trial(3).trial() == RealitySplitTrialController.Trial.RIFT_HUNTER,
                "the fourth room hosts the Hunter trial");
        var fivePlayerAssignment = ChamberIsolationPolicy.assign(List.of(
                players[0], players[1], players[2], players[3], players[4]));
        check(fivePlayerAssignment.playersIn(0).size() == 2
                        && fivePlayerAssignment.playersIn(1).size() == 1
                        && fivePlayerAssignment.playersIn(2).size() == 1
                        && fivePlayerAssignment.playersIn(3).size() == 1,
                "five players are distributed 2/1/1/1 across the four trials");
        var sixPlayerAssignment = ChamberIsolationPolicy.assign(List.of(
                players[0], players[1], players[2], players[3], players[4], players[5]));
        check(sixPlayerAssignment.playersIn(0).size() == 2
                        && sixPlayerAssignment.playersIn(1).size() == 2
                        && sixPlayerAssignment.playersIn(2).size() == 1
                        && sixPlayerAssignment.playersIn(3).size() == 1,
                "six players are distributed 2/2/1/1 across the four trials");
        check(fullParty.defeatHunter(44L, 3).complete(), "Hunter death completes its room");
        var previousTrials = fullParty.snapshot();
        var invalidTrials = new java.util.LinkedHashMap<>(previousTrials);
        invalidTrials.put(3, new RealitySplitTrialController.TrialState(3,
                RealitySplitTrialController.Trial.WARDEN,
                RealitySplitTrialController.Stage.ACTIVE, 0, 1));
        boolean invalidRestoreRejected = false;
        try {
            fullParty.restore(99L, ChamberIsolationPolicy.assign(List.of(players)), invalidTrials);
        } catch (IllegalArgumentException expected) {
            invalidRestoreRejected = true;
        }
        check(invalidRestoreRejected, "foreign trial identity must be rejected");
        check(fullParty.owns(44L) && !fullParty.owns(99L),
                "rejected trial restore must not publish a new generation");
        check(fullParty.snapshot().equals(previousTrials),
                "rejected trial restore must preserve earlier completed rooms and progress");
        for (var rejected : java.util.Arrays.<java.util.Map<Integer,
                RealitySplitTrialController.TrialState>>asList(null, java.util.Map.of())) {
            try {
                fullParty.restore(100L, ChamberIsolationPolicy.assign(List.of(players)), rejected);
                throw new AssertionError("missing trial states must be rejected");
            } catch (IllegalArgumentException expected) { }
            check(fullParty.owns(44L) && fullParty.snapshot().equals(previousTrials),
                    "incomplete restore must preserve live trial state");
        }
        fullParty.clear();
        check(!fullParty.owns(44L) && fullParty.activeTrialCount() == 0,
                "clear removes all trial state and is safe to repeat");
        fullParty.clear();

        System.out.println("RealitySplitTrialControllerTest OK");
    }

    private static UUID player(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
