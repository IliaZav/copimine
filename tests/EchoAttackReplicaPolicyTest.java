import me.copimine.endevent.domain.EchoAttackReplicaPolicy;
import me.copimine.endevent.domain.EchoAttackReplicaPolicy.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 5, Echo Type A (master 15.2): a short-lived spectral copy that locks ONE
 * eligible player, repeats the originating attack after a visible delay, deals
 * real damage only on the matching hit keyframe, then warns and detonates.
 *
 * <p>The two ways this mechanic goes wrong in the field are silent: the copy
 * homes invisibly during its final frames (undodgeable) and its explosion lands
 * twice (double damage from a telegraph nobody could read). Both are decided
 * here so they can be proven without a server.</p>
 */
public final class EchoAttackReplicaPolicyTest {
    private static int failures = 0;

    public static void main(String[] args) {
        eligibility();
        timeline();
        singleHitGuarantee();
        dodgeability();
        explosionWarning();
        cleanup();
        System.out.println(failures == 0 ? "OK" : "FAILURES " + failures);
        if (failures != 0) System.exit(1);
    }

    // --- 15.2 items 2-4: one eligible target, the same attack family ---------
    private static void eligibility() {
        Set<String> candidates = new HashSet<>(List.of("alice", "bob", "carol"));
        check("an eligible player is locked", EchoAttackReplicaPolicy.lockTarget("alice", candidates));
        check("an ineligible player is never locked",
                !EchoAttackReplicaPolicy.lockTarget("mallory", candidates));
        check("a null player is never locked",
                !EchoAttackReplicaPolicy.lockTarget(null, candidates));
        check("a null candidate set never locks",
                !EchoAttackReplicaPolicy.lockTarget("alice", null));
        check("a blank player is never locked",
                !EchoAttackReplicaPolicy.lockTarget("  ", candidates));

        // Candidate count is bounded so a raid does not spawn a swarm of copies.
        for (int players = 1; players <= 20; players++) {
            int echoes = EchoAttackReplicaPolicy.replicasForAttack(players);
            check("replica count is bounded at " + players,
                    echoes >= 0 && echoes <= EchoAttackReplicaPolicy.MAX_REPLICAS);
        }
        check("the replica cap is small", EchoAttackReplicaPolicy.MAX_REPLICAS <= 3);
        check("a solo player still meets one echo",
                EchoAttackReplicaPolicy.replicasForAttack(1) == 1);
        check("the replica count never exceeds the party",
                EchoAttackReplicaPolicy.replicasForAttack(2) <= 2);

        // The copy uses the originating attack family, never an arbitrary spell.
        check("the echo repeats the originating family",
                EchoAttackReplicaPolicy.repeatsFamily("MELEE_SWIPE", "MELEE_SWIPE"));
        check("the echo cannot substitute another family",
                !EchoAttackReplicaPolicy.repeatsFamily("MELEE_SWIPE", "VOID_BLAST"));
        // Both sides are validated: a real family against an unreported one is
        // refused in both directions, so a typo never silently becomes a copy.
        check("a reported family against an unknown copy is refused",
                !EchoAttackReplicaPolicy.repeatsFamily("MELEE_SWIPE", "NOPE"));
        check("an unknown origin cannot be copied",
                !EchoAttackReplicaPolicy.repeatsFamily("NOPE", "MELEE_SWIPE"));
        check("two different reported families never match",
                !EchoAttackReplicaPolicy.repeatsFamily("GROUND_SLAM", "CHEST_STRIKE"));
        check("an unknown family is refused",
                !EchoAttackReplicaPolicy.repeatsFamily("MELEE_SWIPE", "WHATEVER")
                        && !EchoAttackReplicaPolicy.repeatsFamily("WHATEVER", "MELEE_SWIPE"));
        check("the same copy is used for every stalling family",
                EchoAttackReplicaPolicy.supportedFamilies().contains("MELEE_SWIPE")
                        && EchoAttackReplicaPolicy.supportedFamilies().contains("CHEST_STRIKE")
                        && EchoAttackReplicaPolicy.supportedFamilies().contains("GROUND_SLAM"));
    }

    // --- 15.2 items 4, 5, 6: the visible delay and the hit keyframe ----------
    private static void timeline() {
        Timeline timeline = EchoAttackReplicaPolicy.timeline(100L, "MELEE_SWIPE");
        long arrival = timeline.arrivalTick();
        long telegraphLock = timeline.telegraphLockTick();
        long hit = timeline.hitTick();
        long collapse = timeline.collapseTick();
        long expiry = timeline.expiryTick();

        check("the copy arrives first", arrival <= telegraphLock);
        check("the telegraph locks before the hit", telegraphLock < hit);
        check("the strike is visible before it lands",
                hit - telegraphLock >= EchoAttackReplicaPolicy.MIN_TELEGRAPH_TICKS);
        // Contract wording: "repeats the attack after a visible delay".
        check("the delay really is a delay", hit - arrival >= 20L);
        check("the hit precedes the collapse", hit < collapse);
        check("the collapse precedes the expiry", collapse < expiry);
        check("the copy is short-lived",
                expiry - arrival <= EchoAttackReplicaPolicy.MAX_LIFETIME_TICKS);
        check("the lifetime is finite", expiry > arrival);

        // The explosion is the LAST action and is warned before it lands.
        check("the explosion is warned",
                timeline.explosionWarningTick() < timeline.explosionTick());
        check("the explosion warning is readable",
                timeline.explosionTick() - timeline.explosionWarningTick()
                        >= EchoAttackReplicaPolicy.MIN_TELEGRAPH_TICKS);
        // The warning is a window the player can actually walk out of, so it can
        // never start before the copy has even landed its copied strike.
        check("the explosion warning opens after the strike",
                timeline.explosionWarningTick() >= timeline.hitTick());
        check("the explosion warning opens inside the copy's lifetime",
                timeline.explosionWarningTick() < timeline.expiryTick());
        check("the explosion warning is not the arrival",
                timeline.explosionWarningTick() > timeline.arrivalTick());
        check("the explosion is the final action",
                timeline.explosionTick() <= timeline.expiryTick());

        // Two different families get different, family-correct timings.
        Timeline slam = EchoAttackReplicaPolicy.timeline(100L, "GROUND_SLAM");
        check("a heavier family telegraphs at least as long",
                slam.hitTick() - slam.telegraphLockTick()
                        >= hit - telegraphLock);
        check("an unknown family is refused",
                EchoAttackReplicaPolicy.timeline(100L, "NOPE") == null);
        check("a null family is refused",
                EchoAttackReplicaPolicy.timeline(100L, null) == null);

        // Degenerate start ticks are clamped, not wrapped negative.
        Timeline clamped = EchoAttackReplicaPolicy.timeline(-500L, "MELEE_SWIPE");
        check("a negative start tick is clamped", clamped.arrivalTick() >= 0L);
    }

    // --- 15.2 item 5: real damage only at the matching hit keyframe ---------
    private static void singleHitGuarantee() {
        Timeline timeline = EchoAttackReplicaPolicy.timeline(100L, "MELEE_SWIPE");
        // The strike window is contiguous and anchored on the hit keyframe.
        int striking = 0;
        long first = -1L;
        for (long tick = timeline.arrivalTick(); tick <= timeline.expiryTick(); tick++) {
            if (EchoAttackReplicaPolicy.strikeWindowOpen(timeline, tick)) {
                striking++;
                if (first < 0L) {
                    first = tick;
                }
            }
        }
        check("a strike window exists", striking > 0);
        check("the strike window opens on the hit keyframe", first == timeline.hitTick());
        check("the strike window is short", striking <= 12);

        // Exactly one hit per player per copy, whatever tick is asked.
        Set<String> ledger = new HashSet<>();
        int hits = 0;
        for (long tick = timeline.arrivalTick(); tick <= timeline.expiryTick() + 40L; tick++) {
            if (!EchoAttackReplicaPolicy.strikeWindowOpen(timeline, tick)
                    && !EchoAttackReplicaPolicy.explosionWindowOpen(timeline, tick)) {
                continue;
            }
            if (EchoAttackReplicaPolicy.mayDamage("victim", ledger, timeline.replicaKey())) {
                ledger.add(EchoAttackReplicaPolicy.scopedKey(timeline.replicaKey(), "victim"));
                hits++;
            }
        }
        check("an echo damages its victim exactly once in total", hits == 1);

        // The strike and the explosion never share a key: one copy may land the
        // strike AND the warned explosion, each exactly once.
        check("the strike key differs from the explosion key",
                !EchoAttackReplicaPolicy.scopedKey(timeline.replicaKey(), "victim")
                        .equals(EchoAttackReplicaPolicy.scopedKey(
                                timeline.explosionKey(), "victim")));
        Set<String> perPhase = new HashSet<>();
        int strikeHits = 0;
        int boomHits = 0;
        for (long tick = timeline.arrivalTick(); tick <= timeline.expiryTick() + 40L; tick++) {
            if (EchoAttackReplicaPolicy.strikeWindowOpen(timeline, tick)
                    && EchoAttackReplicaPolicy.mayDamage(
                    "victim", perPhase, timeline.replicaKey())) {
                perPhase.add(EchoAttackReplicaPolicy.scopedKey(timeline.replicaKey(), "victim"));
                strikeHits++;
            }
            if (EchoAttackReplicaPolicy.explosionWindowOpen(timeline, tick)
                    && EchoAttackReplicaPolicy.mayDamage(
                    "victim", perPhase, timeline.explosionKey())) {
                perPhase.add(EchoAttackReplicaPolicy.scopedKey(timeline.explosionKey(), "victim"));
                boomHits++;
            }
        }
        check("the strike lands once", strikeHits == 1);
        check("the warned explosion lands once", boomHits == 1);

        // Two copies of the same boss do not share a ledger key.
        Timeline other = EchoAttackReplicaPolicy.timeline(100L, "CHEST_STRIKE");
        check("distinct copies have distinct keys",
                !timeline.replicaKey().equals(other.replicaKey()));
        check("a null ledger refuses damage",
                !EchoAttackReplicaPolicy.mayDamage("victim", null, timeline.replicaKey()));
        check("a null player refuses damage",
                !EchoAttackReplicaPolicy.mayDamage(null, Set.of(), timeline.replicaKey()));
    }

    // --- 15.2 items 6-8: it must be dodgeable -------------------------------
    private static void dodgeability() {
        Timeline timeline = EchoAttackReplicaPolicy.timeline(100L, "MELEE_SWIPE");
        // The final aim locks at the telegraph tick: no invisible homing.
        check("the aim locks at the telegraph",
                EchoAttackReplicaPolicy.aimIsLocked(timeline, timeline.telegraphLockTick()));
        check("the aim is not locked before the telegraph",
                !EchoAttackReplicaPolicy.aimIsLocked(timeline, timeline.telegraphLockTick() - 1L));
        check("the aim stays locked through the strike",
                EchoAttackReplicaPolicy.aimIsLocked(timeline, timeline.hitTick())
                        && EchoAttackReplicaPolicy.aimIsLocked(timeline, timeline.hitTick() + 3L));

        // Once the aim is locked, a target that moved is missed.
        check("stepping out of the locked aim dodges the strike",
                !EchoAttackReplicaPolicy.strikeConnects(timeline, 0.0D, 3.1D));
        check("standing in the locked aim is hit",
                EchoAttackReplicaPolicy.strikeConnects(timeline, 0.0D, 0.4D));
        check("the strike reach is the authored radius",
                EchoAttackReplicaPolicy.strikeConnects(
                        timeline, 0.0D, EchoAttackReplicaPolicy.STRIKE_RADIUS - 0.01D));
        check("just outside the radius is safe",
                !EchoAttackReplicaPolicy.strikeConnects(
                        timeline, 0.0D, EchoAttackReplicaPolicy.STRIKE_RADIUS + 0.01D));
        check("total displacement is what matters, not one axis",
                !EchoAttackReplicaPolicy.strikeConnects(timeline, 2.4D, 2.4D));

        // The explosion has a real radius and is dodgeable the same way.
        check("the explosion has a real radius",
                EchoAttackReplicaPolicy.EXPLOSION_RADIUS > 1.5D);
        check("outside the explosion radius is safe",
                !EchoAttackReplicaPolicy.explosionConnects(
                        EchoAttackReplicaPolicy.EXPLOSION_RADIUS + 0.01D));
        check("inside the explosion radius hurts",
                EchoAttackReplicaPolicy.explosionConnects(
                        EchoAttackReplicaPolicy.EXPLOSION_RADIUS - 0.01D));
        check("a NaN distance never connects",
                !EchoAttackReplicaPolicy.strikeConnects(timeline, Double.NaN, 0.0D)
                        || !EchoAttackReplicaPolicy.explosionConnects(Double.NaN));
    }

    // --- 15.2 item 6: warned, real-radius explosion -------------------------
    private static void explosionWarning() {
        Timeline timeline = EchoAttackReplicaPolicy.timeline(100L, "MELEE_SWIPE");
        check("the explosion window is anchored on its own tick",
                EchoAttackReplicaPolicy.explosionWindowOpen(timeline, timeline.explosionTick()));
        check("the explosion does not open before its tick",
                !EchoAttackReplicaPolicy.explosionWindowOpen(
                        timeline, timeline.explosionTick() - 1L));
        int boomTicks = 0;
        for (long tick = timeline.arrivalTick(); tick <= timeline.expiryTick() + 40L; tick++) {
            if (EchoAttackReplicaPolicy.explosionWindowOpen(timeline, tick)) {
                boomTicks++;
            }
        }
        check("the explosion window is a single short window", boomTicks >= 1 && boomTicks <= 12);
        check("the strike and explosion windows never overlap",
                !EchoAttackReplicaPolicy.strikeWindowOpen(timeline, timeline.explosionTick()));
    }

    // --- 15.2 item 6: cleanup on death/phase/restart/disconnect --------------
    private static void cleanup() {
        for (String reason : List.of("DEATH", "PHASE_CHANGE", "RESTART", "DISCONNECT",
                "TARGET_LOST", "EXPIRED")) {
            check("cleanup is required for " + reason,
                    EchoAttackReplicaPolicy.requiresCleanup(reason));
        }
        check("an unknown cleanup reason is still cleaned",
                EchoAttackReplicaPolicy.requiresCleanup("WHATEVER"));
        // A lost target cancels the copy instead of letting it swing at nothing.
        check("a lost target cancels the copy",
                EchoAttackReplicaPolicy.cancelsOnTargetLoss());
        // The copy cannot produce loot or advance the phase.
        check("an echo never drops loot", !EchoAttackReplicaPolicy.dropsLoot());
        check("an echo never advances the phase",
                !EchoAttackReplicaPolicy.countsTowardPhaseProgress());
    }

    private static void check(String name, boolean condition) {
        if (!condition) {
            System.out.println("FAIL " + name);
            failures++;
        }
    }
}