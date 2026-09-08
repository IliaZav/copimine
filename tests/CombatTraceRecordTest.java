import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.BossCastState;
import me.copimine.endevent.domain.CombatTraceRecord;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.runtime.CombatTraceService;

public final class CombatTraceRecordTest {
    public static void main(String[] args) {
        UUID attacker = UUID.nameUUIDFromBytes("trace-attacker".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID victim = UUID.nameUUIDFromBytes("trace-victim".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CombatTraceRecord record = CombatTraceRecord.open(
                742L, 1_700_000_000_000L, attacker, victim, "PLAYER", "ENTITY_ATTACK",
                7.0D, 5.0D, false, 9, 20, 3.0D, 40.0D,
                EventPhase.BOSS_ACTIVE, BossCastState.NONE, false, 18.75D)
                .close(false, 35.0D);

        check(record.tick() == 742L, "trace must retain the Paper tick");
        check(record.finalDamage() == 5.0D, "trace must retain final Bukkit damage");
        check(!record.cancelledBefore() && !record.cancelledAfter(),
                "accepted event must retain both cancellation boundaries");
        check(record.nextTickHealth() == 35.0D, "trace must retain the next-tick health observation");
        String line = record.toLogLine();
        check(line.contains("raw=7.0") && line.contains("final=5.0"),
                "trace serialization must expose raw and final damage");
        check(line.contains("cancelled_before=false") && line.contains("cancelled_after=false"),
                "trace serialization must expose both cancellation boundaries");
        check(line.contains("mspt=18.75"), "trace serialization must expose runtime MSPT");

        CombatTraceService service = new CombatTraceService(2);
        service.record(record);
        service.record(record.close(true, 35.0D));
        service.record(record.close(false, 30.0D));
        List<CombatTraceRecord> retained = service.snapshot();
        check(retained.size() == 2, "trace buffer must be bounded");
        check(retained.get(0).cancelledAfter(), "bounded buffer must retain ordered later entries");
        check(retained.get(1).nextTickHealth() == 30.0D,
                "bounded buffer must retain the final next-tick observation");
        System.out.println("CombatTraceRecordTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
