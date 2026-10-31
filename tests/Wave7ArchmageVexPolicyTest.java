import me.copimine.endevent.domain.Wave7ArchmageVexPolicy;

/**
 * MAGE7-003 / MAGE7-004: repeated Vex summoning is allowed, unbounded
 * accumulation is not, and every Vex stays inside the encounter.
 */
public final class Wave7ArchmageVexPolicyTest {
    public static void main(String[] args) {
        capsFollowTheParticipantBands();
        summonGraduallyInsteadOfDumpingTheWholeCap();
        refusesToExceedTheLiveCap();
        childrenExpireAndDieCleanly();
        vexDeathIsNotAStageCompletion();
        System.out.println("Wave7ArchmageVexPolicyTest OK");
    }

    private static void capsFollowTheParticipantBands() {
        for (int players = 2; players <= 4; players++) {
            check(Wave7ArchmageVexPolicy.cap(players) == 2, "2-4 participants cap at two Vexes");
        }
        for (int players = 5; players <= 9; players++) {
            check(Wave7ArchmageVexPolicy.cap(players) == 4, "5-9 participants cap at four Vexes");
        }
        for (int players = 10; players <= 20; players++) {
            check(Wave7ArchmageVexPolicy.cap(players) == 6, "10-20 participants cap at six Vexes");
        }
        check(Wave7ArchmageVexPolicy.cap(1) == 0, "a roster below the Wave 7 minimum gets no cap");
        check(Wave7ArchmageVexPolicy.cap(21) == 0, "a roster above the Wave 7 maximum gets no cap");
    }

    private static void summonGraduallyInsteadOfDumpingTheWholeCap() {
        check(Wave7ArchmageVexPolicy.SUMMON_GROUP_SIZE >= 1,
                "Vexes arrive in bounded small groups");
        int cap = Wave7ArchmageVexPolicy.cap(10);
        int granted = Wave7ArchmageVexPolicy.summonCount(cap, 0, Wave7ArchmageVexPolicy.NEVER_SUMMONED);
        check(granted == 1, "the first summon is one Vex, not the entire cap");
        check(Wave7ArchmageVexPolicy.summonCount(cap, cap, 0L) == 0,
                "a full live set blocks further summoning");
        check(Wave7ArchmageVexPolicy.SUMMON_INTERVAL_TICKS > 0L,
                "there is an explicit summon cadence");
        check(Wave7ArchmageVexPolicy.summonCount(cap, 0, 1L) == 0,
                "the cadence is actually enforced between summons");
    }

    private static void refusesToExceedTheLiveCap() {
        int cap = Wave7ArchmageVexPolicy.cap(6);
        int live = 0;
        long tick = 0L;
        int total = 0;
        for (int step = 0; step < 400; step++) {
            int granted = Wave7ArchmageVexPolicy.summonCount(cap, live, tick);
            live += granted;
            total += granted;
            if (live >= cap) break;
            tick += Wave7ArchmageVexPolicy.SUMMON_INTERVAL_TICKS;
        }
        check(live == cap, "summoning stops exactly at the live cap");
        check(total == cap, "no Vex is summoned beyond the cap");
        check(live <= Wave7ArchmageVexPolicy.HARD_CAP,
                "the live set never exceeds the hard cap");
    }

    private static void childrenExpireAndDieCleanly() {
        check(Wave7ArchmageVexPolicy.VEX_LIFETIME_TICKS > 0L,
                "Vexes have a finite lifetime instead of accumulating across the stage");
        check(Wave7ArchmageVexPolicy.expired(Wave7ArchmageVexPolicy.VEX_LIFETIME_TICKS),
                "a Vex expires at the end of its lifetime");
        check(!Wave7ArchmageVexPolicy.expired(Wave7ArchmageVexPolicy.VEX_LIFETIME_TICKS - 1L),
                "a Vex is still alive on its final tick");
        check(!Wave7ArchmageVexPolicy.expired(0L), "a fresh Vex has not expired");
        check(Wave7ArchmageVexPolicy.expireOnRootDeath() && Wave7ArchmageVexPolicy.expireOnReset(),
                "children are cleaned when the root dies or the stage resets");
        check(Wave7ArchmageVexPolicy.childHasOwnIdentity(),
                "a Vex carries its own identity so its death cannot be read as the root's");
    }

    private static void vexDeathIsNotAStageCompletion() {
        check(!Wave7ArchmageVexPolicy.vexDeathCompletesStage(),
                "W7-003: a Vex death must not complete the Archmage stage");
        check(Wave7ArchmageVexPolicy.confinedToArena(),
                "MAGE7-004: Vexes cannot drift outside the approved arena bounds");
        check(!Wave7ArchmageVexPolicy.mayTargetOutsideEncounter(),
                "Vexes target legal participants inside the encounter only");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}