"""Execute the production server's role/scale animation packet boundary.

The shipped code had two nested per-viewer loops: binding an entity ran once per
viewer and each of those broadcasts re-looped every other viewer, so a full party
paid O(V squared) packets for a single state change. The boundary is now a single
broadcast on a state CHANGE plus a targeted per-viewer send on a late bind. These
probes compile both production overloads and count packets.
"""

from pathlib import Path
import subprocess

from tests.test_boss_phase_damage_transaction_adapter import _method


ROOT = Path(__file__).resolve().parents[1]


def test_tentacle_packet_is_one_per_viewer_and_never_quadratic(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    broadcast = _method(source, "private void sendTentacleAnimationVisualUpdate(Entity entity,")
    per_viewer = _method(source, "private void sendTentacleAnimationVisualUpdate(Player player,")
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
    static class World { final String name; World(String name){this.name=name;}
        public boolean equals(Object o){return o instanceof World w && w.name.equals(name);}
        public int hashCode(){return name.hashCode();} }
    static final World WORLD = new World("arena");
    static class Entity { final UUID id = UUID.randomUUID(); UUID getUniqueId() { return id; }
        World getWorld() { return WORLD; } }
    static class Player { final String name; Player(String name){this.name=name;}
        World getWorld() { return WORLD; } }
    static class Bukkit {
        static List<Player> online = new ArrayList<>();
        static List<Player> getOnlinePlayers() { return online; }
    }
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
    final Object keyTentacleHealthState = new Object(), keyTentacleTarget = new Object(), keyTentacleHealth = new Object();
    Config config = new Config(new Tuning(0.4));
    String received;
    int totalPackets;
    final Map<String, Integer> perViewer = new HashMap<>();
    double readDouble(Entity actor,Object key,double fallback){return 17.0;}
    String readString(Entity actor, Object key) { return key == keyTentacleHealthState ? "DAMAGED" : "target-id"; }
    boolean isEventVisualViewer(Player ignored) { return true; }
    void sendClientPacket(Player player, String type, String instance, long duration,
                          String subject, String cue) {
        if (!type.equals("END_ENTITY_PHASE") || duration != 1000L)
            throw new AssertionError("animation transport contract changed");
        totalPackets++;
        perViewer.merge(player.name, 1, Integer::sum);
        received = cue;
    }
''' + broadcast + "\n" + per_viewer + r'''
    public static void main(String[] args) {
        var p = new SmallKagunePresentationPacketProbe(); var actor = new Entity();
        Bukkit.online = List.of(new Player("a"), new Player("b"), new Player("c"));
        for (Kind kind : Kind.values()) {
            p.tentacleController.visual = new TentacleController.VisualState(kind);
            // One state CHANGE must cost exactly one packet per viewer: the
            // nested-loop storm would make this 9 for three viewers.
            p.totalPackets = 0; p.perViewer.clear();
            p.sendTentacleAnimationVisualUpdate(actor, TentacleAnimationPolicy.State.READY);
            String suffix = "|role=" + kind + "|scale=" + (switch(kind){case SMALL -> "0.4";case UNDER_PLAYER -> "0.55";case TEMPORARY -> "0.75";default -> "1.0";});
            if (!p.received.equals("READY|t=34|health=DAMAGED|target=target-id" + suffix + "|hp=17.0"))
                throw new AssertionError("role or config scale lost: " + p.received);
            if (p.totalPackets != Bukkit.online.size())
                throw new AssertionError("burst storm: " + p.totalPackets + " packets for " + Bukkit.online.size() + " viewers");
            for (var player : Bukkit.online)
                if (p.perViewer.get(player.name) == null)
                    throw new AssertionError("a viewer was skipped");
        }
        // A late bind is ONE targeted packet, not a broadcast to every viewer.
        p.totalPackets = 0; p.perViewer.clear();
        p.sendTentacleAnimationVisualUpdate(Bukkit.online.get(0), actor, TentacleAnimationPolicy.State.READY);
        if (p.totalPackets != 1 || p.perViewer.get("a") != 1)
            throw new AssertionError("late bind broadcast to every viewer: " + p.totalPackets);
        p.tentacleController.visual = null;
        p.totalPackets = 0; p.perViewer.clear();
        p.sendTentacleAnimationVisualUpdate(actor, TentacleAnimationPolicy.State.READY);
        if (!p.received.endsWith("|role=PERMANENT|scale=1.0|hp=17.0"))
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
