import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.runtime.AttemptLifecycleController;
import me.copimine.endevent.runtime.AttemptLifecycleController.ReturnWindowStatus;

/** Real lifecycle transitions, including restart; no Bukkit or test-only return engine. */
public final class Wave7ReturnLifecycleTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), FOREIGN = new UUID(0, 3);
    private static final long GEN = 43, GRACE = 120_000, WALL = 1_800_000_000_000L;
    private static final Map<UUID, Integer> CLAIMS = Map.of(A, 0, B, 1);

    public static void main(String[] args) {
        var life = new AttemptLifecycleController();
        life.begin(GEN, Set.of(A, B));
        check(life.enableWave7Returns(GEN), "live generation enables personal return admission");
        long oldIncarnation = life.incarnation(A, GEN);
        check(life.isCombatAdmitted(A, GEN), "initial living owner is admitted");
        check(life.markDead(A, GEN), "committed death recorded");
        check(life.isReturnPending(A, GEN) && !life.isCombatAdmitted(A, GEN), "death preserves claim but removes admission");
        check(!life.incarnationMatches(A, GEN, oldIncarnation), "pre-death projectile incarnation is invalid");
        check(life.markAlive(A, GEN), "normal bed spawn records physical life");
        life.markOnline(A, GEN);
        check(!life.refreshObjectiveEligibility(A, GEN, true, true), "bed, join and physical arena refresh cannot admit owner");
        check(!life.activeLivingOnlineRoster().contains(A) && !life.living().contains(A), "pending owner cannot satisfy runes or keep an abandoned fight alive");
        check(life.beginReturn(FOREIGN, GEN, 0, 40) == null && life.beginReturn(A, GEN + 1, 0, 40) == null,
                "foreign and stale return denied");
        var token = life.beginReturn(A, GEN, 5, 40);
        check(token != null && life.isReturnProtected(A, GEN, 5), "explicit return stages with bounded protection");
        check(!life.isCombatAdmitted(A, GEN) && !life.refreshObjectiveEligibility(A, GEN, true, true), "staging never authorizes offense");
        check(!life.completeReturn(token, 44, 0), "countdown cannot complete early");
        check(life.completeReturn(token, 45, 0) && life.isCombatAdmitted(A, GEN), "validated final admission completes once");
        check(!life.isReturnProtected(A, GEN, 45) && !life.completeReturn(token, 45, 0), "protection ends before combat and completion is idempotent");
        life.markDead(A, GEN); life.markAlive(A, GEN);
        var offensive = life.beginReturn(A, GEN, 60, 40);
        check(life.cancelReturn(offensive) && !life.isReturnProtected(A, GEN, 60), "offensive attempt revokes protection before dispatch");
        check(!life.completeReturn(offensive, 100, 0), "stale staged attack callback cannot admit owner");
        var quitting = life.beginReturn(A, GEN, 110, 40);
        life.markOffline(A, GEN); life.markOnline(A, GEN); life.markAlive(A, GEN);
        check(!life.completeReturn(quitting, 150, 0) && life.isReturnPending(A, GEN), "quit/join invalidates staging without reinstating combat");
        check(life.beginReturn(A, GEN, 0, 61) == null, "protection cannot exceed sixty ticks");
        check(life.enableWave7Returns(GEN) && life.isReturnPending(A, GEN), "repeated wave setup cannot reset pending claims");

        check(life.observeWave7ReturnWindow(GEN, 0, WALL, GRACE) == ReturnWindowStatus.NONE, "other admitted owner keeps ordinary trial running");
        life.markDead(B, GEN);
        check(life.observeWave7ReturnWindow(GEN, 0, WALL, GRACE) == ReturnWindowStatus.STARTED, "last combat death opens original grace");
        life.markAlive(B, GEN); life.markOffline(B, GEN); life.markOnline(B, GEN); life.markAlive(B, GEN);
        check(life.observeWave7ReturnWindow(GEN, 119_000_000_000L, WALL - 1000, GRACE) == ReturnWindowStatus.WAITING,
                "physical respawn and reconnect do not reopen grace; runtime uses monotonic time");
        var late = life.beginReturn(B, GEN, 200, 40);
        check(!life.completeReturn(late, 240, 120_000_000_000L), "staged return cannot commit after original deadline");
        check(life.observeWave7ReturnWindow(GEN, 120_000_000_000L, WALL, GRACE) == ReturnWindowStatus.EXPIRED,
                "deadline expires exactly once without extending through wall clock changes");

        life.begin(GEN, Set.of(A, B)); life.enableWave7Returns(GEN); life.markDead(A, GEN); life.markDead(B, GEN);
        life.observeWave7ReturnWindow(GEN, 0, WALL, GRACE); life.markAlive(A, GEN);
        var onTime = life.beginReturn(A, GEN, 0, 40);
        check(life.completeReturn(onTime, 40, 20_000_000_000L), "valid admitted return resolves all-dead window");
        life.markDead(A, GEN);
        check(life.observeWave7ReturnWindow(GEN, 30_000_000_000L, WALL + 30_000, GRACE) == ReturnWindowStatus.STARTED,
                "a subsequent real death after admitted combat may start a new window");
        var encoded = life.encodeWave7Returns("event", CLAIMS, 90_000_000_000L, WALL + 90_000, GRACE);
        var restored = new AttemptLifecycleController();
        check(restored.restoreWave7Returns(encoded, "event", GEN, CLAIMS, Set.of(A, B), 5_000_000_000L, WALL + 100_000, GRACE),
                "strict current rights and original claims survive restart");
        check(restored.isReturnPending(A, GEN) && !restored.isCombatAdmitted(A, GEN), "restart never resumes admission or staged hostile effects");
        restored.markOnline(A, GEN); restored.markAlive(A, GEN);
        check(!restored.incarnationMatches(A, GEN, life.incarnation(A, GEN)), "pre-restart effects cannot hit new incarnation");
        check(restored.observeWave7ReturnWindow(GEN, 55_000_000_000L, WALL + 150_000, GRACE) == ReturnWindowStatus.EXPIRED,
                "restart retains fifty remaining seconds, not another full two minutes");
        check(!new AttemptLifecycleController().restoreWave7Returns(encoded, "other", GEN, CLAIMS, Set.of(A, B), 0, WALL + 100_000, GRACE),
                "foreign event rights refused");
        check(!new AttemptLifecycleController().restoreWave7Returns(encoded, "event", GEN + 1, CLAIMS, Set.of(A, B), 0, WALL + 100_000, GRACE),
                "stale generation rights refused");
        check(!new AttemptLifecycleController().restoreWave7Returns(encoded, "event", GEN, Map.of(A, 1, B, 0), Set.of(A, B), 0, WALL + 100_000, GRACE),
                "claim substitution refused");
        check(!new AttemptLifecycleController().restoreWave7Returns(encoded, "event", GEN, CLAIMS, Set.of(A), 0, WALL + 100_000, GRACE),
                "arbitrary saved UUID cannot become live registered protection");
        check(!new AttemptLifecycleController().restoreWave7Returns(encoded, "event", GEN, CLAIMS, Set.of(A, B), 0, WALL + 89_999, GRACE),
                "backwards clock restart fails closed rather than extending return rights");
        var malformed = new HashMap<>(encoded); malformed.put("wave7-return." + A + ".incarnation", "-1");
        check(!restored.restoreWave7Returns(malformed, "event", GEN, CLAIMS, Set.of(A, B), 0, WALL + 100_000, GRACE)
                && restored.isReturnPending(A, GEN), "invalid snapshot cannot replace a valid runtime");
        life.clear();
        check(!life.isReturnPending(A, GEN) && life.beginReturn(A, GEN, 0, 40) == null, "terminal cleanup revokes rights and stale actions");
        life.begin(GEN, Set.of(A, B)); life.setActive(B, GEN, false); life.enableWave7Returns(GEN);
        var activeOnly = Map.of(A, 0);
        var retiredSnapshot = life.encodeWave7Returns("event", activeOnly, 0, WALL, GRACE);
        var retiredRestore = new AttemptLifecycleController();
        check(retiredRestore.restoreWave7Returns(retiredSnapshot, "event", GEN, activeOnly, Set.of(A, B), 0, WALL, GRACE)
                && !retiredRestore.isRegistered(B, GEN), "retired prior-wave reward membership cannot acquire a new Wave 7 claim or death protection");
        life.markDead(A, GEN); life.markAlive(A, GEN); life.endWave7Returns(GEN);
        check(life.isCombatAdmitted(A, GEN) && !life.hasWave7Returns(GEN), "existing boss handoff leaves ordinary later-wave respawn rules usable");
        System.out.println("Wave7ReturnLifecycleTest OK");
    }

    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
