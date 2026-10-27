"""Execute the production server's role/scale animation packet boundary."""

from pathlib import Path
import subprocess

from tests.test_boss_phase_damage_transaction_adapter import _method


ROOT = Path(__file__).resolve().parents[1]


def test_tentacle_packet_transmits_separate_role_and_configured_small_scale(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    method = _method(source, "private void sendTentacleAnimationVisualUpdate(Entity entity,")
    probe = tmp_path / "SmallKagunePresentationPacketProbe.java"
    probe.write_text(r'''
import java.util.*;
public class SmallKagunePresentationPacketProbe {
    enum Kind { PERMANENT, TEMPORARY, UNDER_PLAYER, SMALL }
    static class TentacleAnimationPolicy {
        enum State { READY }
        static class Kind { static final SmallKagunePresentationPacketProbe.Kind SMALL = SmallKagunePresentationPacketProbe.Kind.SMALL; }
        static int durationTicks(State ignored) { return 20; }
        static String wireName(State state) { return state.name(); }
    }
    static class Entity { final UUID id = UUID.randomUUID(); UUID getUniqueId() { return id; } }
    static class Player { }
    static class Bukkit { static List<Player> getOnlinePlayers() { return List.of(new Player()); } }
    static class TentacleController {
        record VisualState(Kind kind) { }
        VisualState visual;
        VisualState state(UUID ignored) { return visual; }
    }
    record Tuning(double scale) { }
    record Config(Tuning smallKaguneTuning) { }
    final TentacleController tentacleController = new TentacleController();
    final Map<UUID, String> entityBindingInstances = new HashMap<>();
    final String eventId = "presentation-probe";
    final long generation = 9, eventTickCounter = 34;
    final Object keyTentacleHealthState = new Object(), keyTentacleTarget = new Object();
    Config config = new Config(new Tuning(0.4));
    String received;
    String readString(Entity actor, Object key) { return key == keyTentacleHealthState ? "DAMAGED" : "target-id"; }
    boolean isEventVisualViewer(Player ignored) { return true; }
    void sendClientPacket(Player player, String type, String instance, long duration,
                          String subject, String cue) {
        if (!type.equals("END_ENTITY_PHASE") || duration != 1000L)
            throw new AssertionError("animation transport contract changed");
        received = cue;
    }
''' + method + r'''
    public static void main(String[] args) {
        var p = new SmallKagunePresentationPacketProbe(); var actor = new Entity();
        for (Kind kind : Kind.values()) {
            p.tentacleController.visual = new TentacleController.VisualState(kind);
            p.sendTentacleAnimationVisualUpdate(actor, TentacleAnimationPolicy.State.READY);
            String suffix = "|role=" + kind + "|scale=" + (kind == Kind.SMALL ? "0.4" : "1.0");
            if (!p.received.equals("READY|t=34|health=DAMAGED|target=target-id" + suffix))
                throw new AssertionError("role or config scale lost: " + p.received);
        }
        p.tentacleController.visual = null;
        p.sendTentacleAnimationVisualUpdate(actor, TentacleAnimationPolicy.State.READY);
        if (!p.received.endsWith("|role=PERMANENT|scale=1.0"))
            throw new AssertionError("missing role binding must retain full-size compatibility");
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(probe)],
                              capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), "SmallKagunePresentationPacketProbe"],
                              capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
