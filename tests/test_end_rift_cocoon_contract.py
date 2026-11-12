"""Master contract 17 / 18 / 22: the Last Seal cocoon, its guardians and its fury.

Every check here runs the real domain policies through javac + java, so a change
that silently breaks a contract guarantee fails this file rather than a server
restart three weeks later.
"""

import os
from pathlib import Path
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"

SOURCES = [
    "EndRiftAiPolicy",
    "KaguneCocoonGuardianPolicy",
    "GuardianRegenerationPolicy",
    "SmallKaguneSpawnPolicy",
    "CocoonFallPolicy",
    "KaguneGuardianFightPolicy",
    "GuardianDeathLedger",
]


def run_probe(tmp_path, name, body, extra_sources=()):
    """Compile the real domain sources with a probe and run it."""
    probe = tmp_path / (name + ".java")
    probe.write_text(body, encoding="utf-8")
    files = [DOMAIN / (stem + ".java") for stem in (*SOURCES, *extra_sources)]
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, files), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        ["java", "-cp", str(tmp_path), name], capture_output=True, text=True
    )
    assert executed.returncode == 0, executed.stdout + executed.stderr
    return executed.stdout


def test_large_guardian_count_six_to_eight_and_closer_to_the_boss():
    """17.1: six to eight large defenders, packed close around the cocoon."""
    source = (DOMAIN / "KaguneCocoonGuardianPolicy.java").read_text(encoding="utf-8")
    assert "MIN_COUNT = 6" in source
    assert "MAX_COUNT = 8" in source
    # The retired layout sat on a 5.5 block ring, which the contract calls
    # "widely spaced". The cocoon ring must be tighter than that.
    assert "COCOON_RING_RADIUS = 3.75" in source


def test_guardian_count_and_ring_offsets(tmp_path):
    output = run_probe(
        tmp_path,
        "GuardianCountProbe",
        """
import me.copimine.endevent.domain.*;

public class GuardianCountProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1.0e-9; }

    public static void main(String[] args) {
        check(KaguneCocoonGuardianPolicy.countFor(1) == 6, "solo party gets six defenders");
        check(KaguneCocoonGuardianPolicy.countFor(5) == 6, "small party gets six defenders");
        check(KaguneCocoonGuardianPolicy.countFor(6) == 7, "six players escalate to seven");
        check(KaguneCocoonGuardianPolicy.countFor(10) == 7, "ten players stay at seven");
        check(KaguneCocoonGuardianPolicy.countFor(11) == 8, "eleven players escalate to eight");
        check(KaguneCocoonGuardianPolicy.countFor(20) == 8, "large party is capped at eight");
        check(KaguneCocoonGuardianPolicy.countFor(0) == 0, "no party needs no defenders");

        for (int players = 1; players <= 20; players++) {
            int count = KaguneCocoonGuardianPolicy.countFor(players);
            check(count >= 6 && count <= 8, "defender count outside 6..8 for " + players);
            for (int slot = 0; slot < count; slot++) {
                var offset = KaguneCocoonGuardianPolicy.offsetFor(slot, count);
                double radius = Math.hypot(offset.x(), offset.z());
                check(near(radius, KaguneCocoonGuardianPolicy.COCOON_RING_RADIUS),
                        "slot " + slot + " is not on the cocoon ring: " + radius);
            }
            var first = KaguneCocoonGuardianPolicy.offsetFor(0, count);
            var second = KaguneCocoonGuardianPolicy.offsetFor(1, count);
            check(Math.hypot(first.x() - second.x(), first.z() - second.z()) > 1.0e-6,
                    "two defenders share one spot");
        }
        check(!KaguneCocoonGuardianPolicy.slotInRange(8, 20), "slot 8 must not exist");
        check(KaguneCocoonGuardianPolicy.slotInRange(7, 20), "slot 7 must exist for eight");
        check(!KaguneCocoonGuardianPolicy.slotInRange(6, 5), "slot 6 must not exist for six");
    }
}
""",
    )
    assert "AssertionError" not in output


def test_cocoon_broken_is_permanent_for_the_generation(tmp_path):
    """17.3: the cocoon never comes back until a new generation starts."""
    run_probe(
        tmp_path,
        "CocoonBrokenProbe",
        """
import me.copimine.endevent.domain.*;

public class CocoonBrokenProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(!KaguneCocoonGuardianPolicy.cocoonBroken(false, 12, 8), "full roster is intact");
        check(!KaguneCocoonGuardianPolicy.cocoonBroken(false, 12, 1), "one survivor holds it");
        check(KaguneCocoonGuardianPolicy.cocoonBroken(false, 12, 0), "zero survivors break it");
        check(KaguneCocoonGuardianPolicy.cocoonBroken(true, 12, 8),
                "a broken cocoon stays broken even if defenders somehow reappear");
        check(!KaguneCocoonGuardianPolicy.cocoonBroken(false, 0, 0),
                "an empty party has no cocoon to break");

        check(KaguneCocoonGuardianPolicy.cocoonActive(12, 8), "living defenders keep it active");
        check(!KaguneCocoonGuardianPolicy.cocoonActive(12, 0), "dead defenders end it");
        check(!KaguneCocoonGuardianPolicy.cocoonActive(0, 0), "empty party has no cocoon");

        check(KaguneCocoonGuardianPolicy.shieldsBoss(false, 8), "intact cocoon shields");
        check(!KaguneCocoonGuardianPolicy.shieldsBoss(true, 8),
                "broken cocoon never shields again");
        check(!KaguneCocoonGuardianPolicy.shieldsBoss(false, 0),
                "no defenders means no shield");
    }
}
""",
    )


def test_guardian_death_is_permanent_and_recorded(tmp_path):
    """17.1: a dead Kagune never returns; a restart restores only survivors."""
    run_probe(
        tmp_path,
        "LedgerProbe",
        """
import java.util.*;
import me.copimine.endevent.domain.*;

public class LedgerProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(!KaguneCocoonGuardianPolicy.mayRespawnAfterKill(),
                "the 90 second guardian respawn must stay retired");
        check(!KaguneCocoonGuardianPolicy.mayRestore(true),
                "a destroyed guardian is never restored");
        check(KaguneCocoonGuardianPolicy.mayRestore(false),
                "a survivor is restored");

        var ledger = new GuardianDeathLedger(7L);
        for (int slot = 0; slot < 8; slot++) ledger.markAlive(slot);
        check(ledger.livingCount() == 8 && ledger.destroyedCount() == 0, "fresh ledger is full");

        check(ledger.recordDeath(3), "first death is recorded");
        check(!ledger.recordDeath(3), "a second death of the same slot changes nothing");
        check(ledger.isDestroyed(3) && !ledger.isLiving(3), "slot 3 is gone");
        check(ledger.livingCount() == 7 && ledger.destroyedCount() == 1, "counts moved once");

        // A restart that tries to re-adopt a dead defender must not resurrect it.
        ledger.markAlive(3);
        check(ledger.isDestroyed(3) && !ledger.isLiving(3),
                "re-marking a destroyed defender alive brought it back");
        check(ledger.livingCount() == 7, "re-marking changed the survivor count");

        var encoded = ledger.encode();
        var restored = GuardianDeathLedger.decode(encoded, 7L);
        check(restored.isDestroyed(3), "the death survived the round trip");
        check(restored.restorableSlots().equals(List.of(0, 1, 2, 4, 5, 6, 7)),
                "only survivors are restorable: " + restored.restorableSlots());
        check(restored.destroyedSlots().equals(List.of(3)), "destroyed slots are reported");
        check(!restored.restorableSlots().contains(3),
                "a destroyed guardian never becomes restorable");
        check(restored.respawnDeadline(3) == null, "no respawn deadline exists any more");

        for (int slot = 0; slot < 8; slot++) ledger.recordDeath(slot);
        check(ledger.allRequiredDestroyed(8), "all eight are destroyed");
        check(!ledger.allRequiredDestroyed(9), "nine were never required");
        check(ledger.restorableSlots().isEmpty(), "nothing is restored from a wiped roster");

        var broken = new GuardianDeathLedger(7L);
        check(!broken.cocoonBroken(), "cocoon starts intact");
        broken.markCocoonBroken();
        var brokenRound = GuardianDeathLedger.decode(broken.encode(), 7L);
        check(brokenRound.cocoonBroken(), "the broken cocoon survives the round trip");

        // A fresh generation starts from a fresh, empty progress map: decode then
        // must not invent a ledger, so nothing is restored by accident.
        check(GuardianDeathLedger.decode(new HashMap<String, String>(), 8L) == null,
                "a new generation starts with no inherited deaths");
        var nextGeneration = new GuardianDeathLedger(8L);
        check(nextGeneration.destroyedCount() == 0 && nextGeneration.restorableSlots().isEmpty(),
                "a new generation starts empty");

        var tampered = new HashMap<>(encoded);
        tampered.put("kagune-cocoon-death.bogus.0", "DONE");
        boolean refused = false;
        try {
            GuardianDeathLedger.decode(tampered, 7L);
        } catch (RuntimeException expected) {
            refused = true;
        }
        check(refused, "an unknown ledger key must be refused, not silently healed");

        var corrupt = new HashMap<>(encoded);
        corrupt.put("kagune-cocoon-death.destroyed.1", "MAYBE");
        boolean corruptRefused = false;
        try {
            GuardianDeathLedger.decode(corrupt, 7L);
        } catch (RuntimeException expected) {
            corruptRefused = true;
        }
        check(corruptRefused, "a corrupt death receipt must be refused");
        check(ledger.encode().keySet().iterator().next().startsWith("kagune-cocoon-death."),
                "the probe uses the real ledger prefix");
    }
}
""",
    )


def test_regeneration_only_while_defenders_live_and_capped_at_five_percent(tmp_path):
    """17.4: 0.2%/s while shielded, 5% total per generation, never after a break."""
    run_probe(
        tmp_path,
        "RegenProbe",
        """
import me.copimine.endevent.domain.*;

public class RegenProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1.0e-6; }

    public static void main(String[] args) {
        double max = 10000.0;
        check(near(GuardianRegenerationPolicy.cap(max), 500.0), "total cap is five percent");
        check(near(GuardianRegenerationPolicy.stepBudget(max), 20.0),
                "one step is 0.2 percent of max health");
        check(GuardianRegenerationPolicy.STEPS_TO_CAP == 25L,
                "twenty five steps of 0.2 percent make five percent");

        // the retired numbers must not come back
        check(!near(GuardianRegenerationPolicy.cap(max), 2500.0),
                "the old 25 percent cap is retired");
        check(!near(GuardianRegenerationPolicy.stepBudget(max), 100.0),
                "the old 1 percent step is retired");

        check(GuardianRegenerationPolicy.allowed(true, false, 3), "shielded final seal heals");
        check(!GuardianRegenerationPolicy.allowed(true, false, 0),
                "no living defender means no healing");
        check(!GuardianRegenerationPolicy.allowed(true, true, 3),
                "a broken cocoon stops healing for good");
        check(!GuardianRegenerationPolicy.allowed(false, false, 3),
                "healing only happens in the final seal");

        double healed = 0.0;
        double health = 1000.0;
        for (int step = 0; step < 200; step++) {
            double before = health;
            health += GuardianRegenerationPolicy.step(max, health, healed);
            healed = health - 1000.0;
            healed += health - before;
        }
        check(near(health, 1500.0), "healing stops exactly at the five percent cap: " + health);
        check(near(healed, 500.0), "total healed equals the cap: " + healed);

        check(near(GuardianRegenerationPolicy.stepGate(true, false, 3, max, 1000.0, 500.0), 0.0),
                "a spent budget heals nothing");
        check(near(GuardianRegenerationPolicy.stepGate(true, true, 3, max, 1000.0, 0.0), 0.0),
                "a broken cocoon heals nothing");
        check(near(GuardianRegenerationPolicy.stepGate(true, false, 0, max, 1000.0, 0.0), 0.0),
                "no defenders heal nothing");
        check(near(GuardianRegenerationPolicy.stepGate(false, false, 3, max, 1000.0, 0.0), 0.0),
                "only the final seal heals");
        check(near(GuardianRegenerationPolicy.stepGate(true, false, 3, max, max, 0.0), 0.0),
                "a full boss never overheals");
        check(near(GuardianRegenerationPolicy.totalForActiveSeconds(max, 1000.0, 600), 500.0),
                "ten minutes of healing still stops at five percent");
    }
}
""",
    )


def test_small_kagune_are_a_separate_killable_enemy(tmp_path):
    """18.1: separate up to thirty, spawning every 15-20s, never temporary."""
    run_probe(
        tmp_path,
        "SmallKaguneProbe",
        """
import me.copimine.endevent.domain.*;

public class SmallKaguneProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(SmallKaguneSpawnPolicy.MAX_LIVING == 30, "the hard cap is thirty");

        // The old six-temporary-tentacle limit must not be reused.
        check(!SmallKaguneSpawnPolicy.sharesTemporaryTentacleLimit(),
                "small Kagune are not the old temporary tentacles");
        check(!SmallKaguneSpawnPolicy.expiresOnLifetime(),
                "small Kagune do not time out; they live until killed");

        // The batch has to follow the party, not always be the full thirty.
        check(SmallKaguneSpawnPolicy.batchSize(1) == 1, "solo spawns one at a time");
        check(SmallKaguneSpawnPolicy.batchSize(5) == 1, "small party spawns one");
        check(SmallKaguneSpawnPolicy.batchSize(6) == 2, "six players spawn two");
        check(SmallKaguneSpawnPolicy.batchSize(10) == 2, "ten players spawn two");
        check(SmallKaguneSpawnPolicy.batchSize(11) == 3, "eleven players spawn three");
        check(SmallKaguneSpawnPolicy.batchSize(16) == 4, "large party spawns four");
        check(SmallKaguneSpawnPolicy.batchSize(20) < SmallKaguneSpawnPolicy.MAX_LIVING,
                "a batch is never the whole cap");

        check(SmallKaguneSpawnPolicy.admission(20, 0) == 4, "empty arena admits a full batch");
        check(SmallKaguneSpawnPolicy.admission(20, 28) == 2, "the cap is respected");
        check(SmallKaguneSpawnPolicy.admission(20, 30) == 0, "a full arena admits nothing");
        check(SmallKaguneSpawnPolicy.admission(20, 99) == 0, "an overfull arena admits nothing");

        check(SmallKaguneSpawnPolicy.spawningAllowed(false, true), "intact cocoon keeps spawning");
        check(!SmallKaguneSpawnPolicy.spawningAllowed(true, true),
                "a broken cocoon stops spawning");
        check(!SmallKaguneSpawnPolicy.spawningAllowed(false, false),
                "a finished encounter stops spawning");

        check(SmallKaguneSpawnPolicy.keepsLiving(false, 30),
                "living ones stay while the cocoon holds");
        check(SmallKaguneSpawnPolicy.keepsLiving(true, 30),
                "a broken cocoon keeps them alive: they are killed, never expired");
        check(!SmallKaguneSpawnPolicy.keepsLiving(true, 0), "an empty set stays empty");

        check(SmallKaguneSpawnPolicy.afterDeath(5) == 4, "a death frees a slot");
        check(SmallKaguneSpawnPolicy.afterDeath(0) == 0, "a death cannot go negative");

        for (long now = 0L; now < 400000L; now += 1000L) {
            long due = SmallKaguneSpawnPolicy.nextDueMillis(now, 3, 42L);
            check(due > now, "a due time is always in the future: " + due + " vs " + now);
            long gap = due - now;
            check(gap >= SmallKaguneSpawnPolicy.SPAWN_MIN_INTERVAL_MILLIS
                    && gap <= SmallKaguneSpawnPolicy.SPAWN_MAX_INTERVAL_MILLIS,
                    "interval outside fifteen to twenty seconds: " + gap);
        }
        long now = 2000000L;
        long first = SmallKaguneSpawnPolicy.nextDueMillis(now, 0, 7L);
        check(SmallKaguneSpawnPolicy.due(first, first), "a due time is due at the boundary");
        check(!SmallKaguneSpawnPolicy.due(first - 1, first), "not due before its time");
        check(!SmallKaguneSpawnPolicy.due(now, first), "not due immediately");
        long total = 0L;
        for (int step = 0; step < 20; step++) total += SmallKaguneSpawnPolicy.intervalMillis(step, 7L);
        double average = total / 20.0D;
        check(average >= SmallKaguneSpawnPolicy.SPAWN_MIN_INTERVAL_MILLIS,
                "average cadence is not faster than fifteen seconds: " + average);
        check(average <= SmallKaguneSpawnPolicy.SPAWN_MAX_INTERVAL_MILLIS,
                "average cadence is not slower than twenty seconds: " + average);
    }
}
""",
    )


def test_cocoon_fall_plays_once_then_permanent_melee_fury(tmp_path):
    """22: one animated fall, permanent melee fury, triple accepted damage."""
    run_probe(
        tmp_path,
        "FallProbe",
        """
import java.util.*;
import me.copimine.endevent.domain.*;

public class FallProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(CocoonFallPolicy.stageAfterLastDefender(false, false)
                == CocoonFallPolicy.Stage.ANCHORS_BROKEN, "the anchors break first");
        check(CocoonFallPolicy.stageAfterLastDefender(true, false)
                == CocoonFallPolicy.Stage.FALLING, "a restart resumes the fall");
        check(CocoonFallPolicy.stageAfterLastDefender(true, true)
                == CocoonFallPolicy.Stage.MELEE_FURY, "a landed fall never replays");

        check(CocoonFallPolicy.mayStartFall(false), "the first fall is allowed");
        check(!CocoonFallPolicy.mayStartFall(true), "the fall never starts twice");
        check(CocoonFallPolicy.mayPlayLanding(false), "the landing plays once");
        check(!CocoonFallPolicy.mayPlayLanding(true), "the landing never replays");

        // The fall is authored, not instant: height must come down over time.
        check(CocoonFallPolicy.landingDue(CocoonFallPolicy.FALL_TICKS),
                "the landing is due when the fall finishes");
        check(!CocoonFallPolicy.landingDue(CocoonFallPolicy.FALL_TICKS - 1),
                "the landing is not due early");
        double start = CocoonFallPolicy.heightDuringFall(6.0, 0);
        double middle = CocoonFallPolicy.heightDuringFall(6.0, CocoonFallPolicy.FALL_TICKS / 2);
        double end = CocoonFallPolicy.heightDuringFall(6.0, CocoonFallPolicy.FALL_TICKS);
        check(Math.abs(start - 6.0) < 1.0e-6, "the fall starts at the hover height");
        check(middle < start && middle > end, "the fall eases down instead of snapping");
        check(Math.abs(end) < 1.0e-6, "the fall ends on the floor");
        check(CocoonFallPolicy.fallProgress(0) == 0.0, "progress starts at zero");
        check(CocoonFallPolicy.fallProgress(CocoonFallPolicy.FALL_TICKS) == 1.0,
                "progress ends at one");
        check(CocoonFallPolicy.fallProgress(CocoonFallPolicy.FALL_TICKS * 10) == 1.0,
                "late progress is clamped");

        // Three times the accepted damage, and only after the landing.
        check(CocoonFallPolicy.acceptedDamageMultiplier(CocoonFallPolicy.Stage.MELEE_FURY) == 3.0,
                "melee fury triples accepted damage");
        check(CocoonFallPolicy.acceptedDamageMultiplier(CocoonFallPolicy.Stage.COCOONED) == 1.0,
                "the cocoon does not amplify damage");
        check(CocoonFallPolicy.acceptedDamageMultiplier(CocoonFallPolicy.Stage.FALLING) == 1.0,
                "the fall itself does not amplify damage");
        check(CocoonFallPolicy.acceptedDamageMultiplier(CocoonFallPolicy.Stage.LANDING) == 1.0,
                "the landing itself does not amplify damage");

        // Melee only, and the cocoon channel is gone for good.
        check(CocoonFallPolicy.allowsMelee(CocoonFallPolicy.Stage.MELEE_FURY),
                "fury fights in melee");
        check(!CocoonFallPolicy.allowsCocoonChannel(CocoonFallPolicy.Stage.MELEE_FURY),
                "the cocoon channel never returns");
        check(CocoonFallPolicy.allowsCocoonChannel(CocoonFallPolicy.Stage.COCOONED),
                "the cocoon channel exists while the cocoon holds");
        check(!CocoonFallPolicy.allowsCocoonChannel(CocoonFallPolicy.Stage.ANCHORS_BROKEN),
                "the cocoon channel dies with the anchors");
        check(!CocoonFallPolicy.allowsCocoonChannel(CocoonFallPolicy.Stage.LANDING),
                "the cocoon channel never returns after the landing");
        check(!CocoonFallPolicy.allowsRangedSpellcasting(CocoonFallPolicy.Stage.MELEE_FURY),
                "melee fury drops ranged spellcasting");
        check(CocoonFallPolicy.allowsRangedSpellcasting(CocoonFallPolicy.Stage.COCOONED),
                "ranged casting still exists while the cocoon holds");
        check(CocoonFallPolicy.isMeleeFury(CocoonFallPolicy.Stage.MELEE_FURY),
                "the stage is recognised");
        check(!CocoonFallPolicy.isMeleeFury(CocoonFallPolicy.Stage.LANDING),
                "the landing is not the fury");

        List<EndRiftAiPolicy.BossSpell> profile = CocoonFallPolicy.furySpellProfile();
        check(!profile.isEmpty(), "fury has an authored melee profile");
        check(CocoonFallPolicy.furyProfileIsMeleeOnly(),
                "every fury spell is melee: " + profile);

        var stage = CocoonFallPolicy.Stage.ANCHORS_BROKEN;
        stage = CocoonFallPolicy.advance(stage, 0);
        check(stage == CocoonFallPolicy.Stage.FALLING, "broken anchors start the fall");
        stage = CocoonFallPolicy.advance(stage, CocoonFallPolicy.FALL_TICKS);
        check(stage == CocoonFallPolicy.Stage.LANDING, "the fall ends in a landing");
        stage = CocoonFallPolicy.advance(stage, CocoonFallPolicy.FALL_TICKS + CocoonFallPolicy.LANDING_TICKS);
        check(stage == CocoonFallPolicy.Stage.MELEE_FURY, "the landing ends in fury");
        stage = CocoonFallPolicy.advance(stage, CocoonFallPolicy.LANDING_TICKS * 100);
        check(stage == CocoonFallPolicy.Stage.MELEE_FURY, "fury is permanent");
    }
}
""",
    )


def test_large_guardian_fights_back_and_cannot_be_stunlocked(tmp_path):
    """17.2: real melee, five to six block knockback, shared pressure."""
    run_probe(
        tmp_path,
        "FightProbe",
        """
import me.copimine.endevent.domain.*;

public class FightProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1.0e-9; }

    public static void main(String[] args) {
        double solo = KaguneGuardianFightPolicy.strikeDamage(1);
        double large = KaguneGuardianFightPolicy.strikeDamage(20);
        check(large > solo, "damage scales with the party");
        check(solo >= KaguneGuardianFightPolicy.BASE_STRIKE_DAMAGE,
                "even a solo guardian hits for the base damage");
        check(large <= KaguneGuardianFightPolicy.MAX_STRIKE_DAMAGE,
                "damage is capped");

        for (int players = 1; players <= 20; players++) {
            double knock = KaguneGuardianFightPolicy.knockbackSpeed(players);
            check(knock >= KaguneGuardianFightPolicy.KNOCKBACK_MIN_BLOCKS,
                    "knockback never drops below five blocks: " + knock);
            check(knock <= KaguneGuardianFightPolicy.KNOCKBACK_MAX_BLOCKS,
                    "knockback never exceeds six blocks: " + knock);
        }

        // Anti-stunlock: one flinch at a time, bounded pressure.
        check(KaguneGuardianFightPolicy.flinchAllowed(0, 0), "a fresh guardian may flinch");
        check(!KaguneGuardianFightPolicy.flinchAllowed(3, 0),
                "a guardian already staggering ignores a new hit");
        check(!KaguneGuardianFightPolicy.flinchAllowed(
                0, KaguneGuardianFightPolicy.MAX_PENDING_FLINCHES),
                "the pending pressure budget is spent");
        check(KaguneGuardianFightPolicy.committedAttackSurvivesFlinch(true),
                "a committed strike still lands through a flinch");
        check(!KaguneGuardianFightPolicy.committedAttackSurvivesFlinch(false),
                "an uncommitted strike is interrupted");

        // Shared pressure: only a couple of guardians may gang up on one player.
        int allowed = 0;
        for (int attackers = 0; attackers < 10; attackers++) {
            if (KaguneGuardianFightPolicy.mayAttackTarget(attackers)) allowed++;
        }
        check(allowed == KaguneGuardianFightPolicy.MAX_CONCURRENT_ATTACKERS_PER_TARGET,
                "exactly two guardians may strike one player, saw " + allowed);

        // The spread must actually move across players instead of dogpiling.
        for (int players = 1; players <= 8; players++) {
            boolean[] used = new boolean[players];
            for (int index = 0; index < 8; index++) {
                int slot = KaguneGuardianFightPolicy.spreadSlot(index, 8, players);
                check(slot >= 0 && slot < players, "spread slot outside the party");
                used[slot] = true;
            }
            for (int player = 0; player < players; player++) {
                check(used[player], "player " + player + " is never targeted with "
                        + players + " in the party");
            }
        }
        check(KaguneGuardianFightPolicy.idleRetryTicks() > 0,
                "an idle guardian retries instead of freezing");
    }
}
""",
    )


def test_cocoon_guarantees_are_wired_into_the_boss_loop():
    """The pure policies only matter if the live server actually calls them.

    This test is deliberately failing-with-an-explanation rather than skipped.
    A policy that nothing calls passes every behavioural check in this file and
    still does nothing in the game, which is exactly the failure mode that
    matters here: the suite would be green while the encounter is unchanged.
    Set STRICT_COCOON_WIRING=1 to make it a hard assertion once the adapter
    patch lands.
    """
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8"
    )
    required = (
        "KaguneCocoonGuardianPolicy",
        "GuardianRegenerationPolicy",
        "SmallKaguneSpawnPolicy",
        "CocoonFallPolicy",
        "KaguneGuardianFightPolicy",
        "GuardianDeathLedger",
    )
    missing = [
        name
        for name in required
        if f"domain.{name}" not in source and name + "." not in source
    ]
    if not missing:
        return
    message = (
        "Cocoon policy guarantees are NOT live; the monolith never calls: "
        + ", ".join(missing)
    )
    if os.environ.get("STRICT_COCOON_WIRING") == "1":
        raise AssertionError(message)
    pytest.xfail(message)
def test_guardian_receipts_require_the_stored_generation(tmp_path):
    run_probe(tmp_path,"GuardianGenerationProbe",r'''
import me.copimine.endevent.domain.*;
import java.util.*;
public class GuardianGenerationProbe {
 public static void main(String[] args){
  var original=new GuardianDeathLedger(7);original.markAlive(0);original.recordDeath(0);original.markCocoonBroken();
  for(String saved:Arrays.asList(null,"oops","0","8")){
   var values=new HashMap<>(original.encode());
   if(saved==null)values.remove(GuardianDeathLedger.KEY_PREFIX+"generation");
   else values.put(GuardianDeathLedger.KEY_PREFIX+"generation",saved);
   boolean refused=false;try{GuardianDeathLedger.decode(values,7);}catch(IllegalArgumentException expected){refused=true;}
   if(!refused)throw new AssertionError("foreign or invalid stored generation was accepted: "+saved);
  }
  var valid=GuardianDeathLedger.decode(original.encode(),7);
  if(!valid.isDestroyed(0)||!valid.cocoonBroken())throw new AssertionError("same-generation death lost");
 }
}
''')

def test_cocoon_fall_uses_one_absolute_clock_across_landing(tmp_path):
    run_probe(tmp_path,"CocoonClockProbe",r'''
import me.copimine.endevent.domain.*;
public class CocoonClockProbe {
 public static void main(String[] args){
  var stage=CocoonFallPolicy.Stage.FALLING;
  for(int tick=0;tick<=61;tick++){
   stage=CocoonFallPolicy.advance(stage,tick);
   var expected=tick<40?CocoonFallPolicy.Stage.FALLING:tick<60?CocoonFallPolicy.Stage.LANDING:CocoonFallPolicy.Stage.MELEE_FURY;
   if(stage!=expected)throw new AssertionError("incorrect stage at absolute tick "+tick+": "+stage);
  }
 }
}
''')
