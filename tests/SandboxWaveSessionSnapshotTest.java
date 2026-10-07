import me.copimine.endevent.domain.SandboxWaveSessionSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SandboxWaveSessionSnapshotTest {
    public static void main(String[] args) {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000031");
        UUID offlineAlly = UUID.fromString("00000000-0000-0000-0000-000000000032");
        Set<UUID> roster = Set.of(player, offlineAlly);
        Map<String, String> saved = SandboxWaveSessionSnapshot.encode(77, 6, roster, true);
        var resumed = SandboxWaveSessionSnapshot.decode(saved, 77);
        check(resumed.wave() == 6 && resumed.combatMode(), "combat mode must survive restart");
        check(resumed.roster().equals(roster), "offline ally must remain in the frozen roster");
        var capture = SandboxWaveSessionSnapshot.decode(
                SandboxWaveSessionSnapshot.encode(78, 6, Set.of(player), false), 78);
        check(!capture.combatMode(), "capture is distinct from combat after restart");
        check(SandboxWaveSessionSnapshot.decode(Map.of(), 78).wave() == 0,
                "official snapshot must not create a sandbox session");
        rejects(() -> SandboxWaveSessionSnapshot.decode(saved, 78), "stale session generation");
        Map<String, String> duplicate = new HashMap<>(saved);
        duplicate.put("test-wave-roster", player + "," + player);
        rejects(() -> SandboxWaveSessionSnapshot.decode(duplicate, 77), "duplicate roster");
        Map<String, String> absent = new HashMap<>(saved);
        absent.remove("test-wave-roster");
        rejects(() -> SandboxWaveSessionSnapshot.decode(absent, 77), "missing sandbox roster");
        Map<String, String> invalidMode = new HashMap<>(saved);
        invalidMode.put("test-wave", "7");
        rejects(() -> SandboxWaveSessionSnapshot.decode(invalidMode, 77), "combat is only wave six");
        var seventh = SandboxWaveSessionSnapshot.decode(
                SandboxWaveSessionSnapshot.encode(79, 7, roster, false), 79);
        check(seventh.roster().equals(roster) && seventh.wave() == 7,
                "wave seven retains the exact room roster");
        System.out.println("SandboxWaveSessionSnapshotTest OK");
    }

    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }

    private static void rejects(Runnable action, String reason) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Accepted " + reason);
    }
}
