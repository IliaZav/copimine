"""Run the actual particle adapter for reachable boss cues on both API contracts."""
from pathlib import Path
import subprocess

import pytest
from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]


@pytest.mark.parametrize("modern", [True, False], ids=["paper26_3", "legacy1_21_1"])
def test_boss_inferno_warning_particles_have_the_declared_api_payload(tmp_path, modern):
    # Missing FLASH Color aborts Inferno's actual warning path in 26.3.
    # Supplying modern Float to legacy DRAGON_BREATH violates its Void payload.
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    production = _method(source, "private void spawnTypedParticle(")
    particles = "DRAGON_BREATH(Float.class), FLASH(Color.class)" if modern else "DRAGON_BREATH(Void.class), FLASH(Void.class)"
    java = r'''
import me.copimine.endevent.domain.BossVisualCuePolicy;
public final class EndRiftTypedParticleProbe {
    static final class Color { static final Color WHITE = new Color(); }
    enum Particle {
        ''' + particles + r''', END_ROD(Void.class);
        final Class<?> dataType;
        Particle(Class<?> dataType) { this.dataType = dataType; }
        Class<?> getDataType() { return dataType; }
    }
    static final class Location {}
    static final class Player {
        int packets; Object lastData;
        void spawnParticle(Particle p, Location at, int count,
                           double x, double y, double z, double extra) {
            spawnParticle(p, at, count, x, y, z, extra, null);
        }
        <T> void spawnParticle(Particle p, Location at, int count,
                              double x, double y, double z, double extra, T data) {
            if (p.getDataType() == Void.class ? data != null : !p.getDataType().isInstance(data))
                throw new AssertionError("Wrong runtime particle payload: " + p
                        + " requires " + p.getDataType().getSimpleName() + ", got " + data);
            packets++;
            lastData = data;
        }
    }
''' + production + r'''
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var adapter = new EndRiftTypedParticleProbe();
        var viewer = new Player();
        var point = new Location();
        adapter.spawnTypedParticle(viewer, Particle.DRAGON_BREATH, point, 4, 0.1, 0.2, 0.3, 0.01);
        require(viewer.packets == 1, "Dragon breath packet was skipped or duplicated");
        int cuePackets = 0;
        // Every cue the table holds, not a hand-picked few: the table is the
        // contract, and picking three spells let a broken bank hide.
        for (var spell : BossVisualCuePolicy.cues().keySet()) {
          for (var stage : BossVisualCuePolicy.CueStage.values()) {
            var cue = BossVisualCuePolicy.cue(spell, stage);
            require(cue != null, "Expected a real configured boss cue");
            // The probe models a handful of real particles. The cue table names
            // many more (FLAME, SMOKE, dust...), and this test is about the
            // typed adapter, not the full palette: a cue that names a particle
            // the probe does not model is skipped, but its PRIMARY is a real
            // particle type that must still flow through the adapter.
            Particle particle;
            try {
                particle = Particle.valueOf(cue.primaryParticle());
            } catch (IllegalArgumentException notModelled) {
                continue;
            }
            int before = viewer.packets;
            adapter.spawnTypedParticle(viewer, particle, point, 1, 0, 0, 0, 0);
            require(viewer.packets == before + 1, "A boss cue was skipped or duplicated: " + spell + " " + stage);
            cuePackets++;
          }
        }
        require(cuePackets >= 1, "No cue particle was both modelled and reachable");
        adapter.spawnTypedParticle(viewer, Particle.END_ROD, point, 2, 0, 0, 0, 0);
        int afterCues = viewer.packets;
        require(viewer.lastData == null, "Untyped particle gained an incompatible payload");
        adapter.spawnTypedParticle(null, Particle.FLASH, point, 1, 0, 0, 0, 0);
        adapter.spawnTypedParticle(viewer, null, point, 1, 0, 0, 0, 0);
        adapter.spawnTypedParticle(viewer, Particle.FLASH, null, 1, 0, 0, 0, 0);
        require(viewer.packets == afterCues, "Invalid particle request emitted a packet");
    }
}
'''
    probe = tmp_path / "EndRiftTypedParticleProbe.java"
    probe.write_text(java, encoding="utf-8")
    compile_result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"),
         "-d", str(tmp_path), str(probe)],
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr
    run = subprocess.run(["java", "-cp", str(tmp_path), "EndRiftTypedParticleProbe"],
                         capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20)
    assert run.returncode == 0, run.stdout + run.stderr
