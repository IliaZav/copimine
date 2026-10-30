"""Exercise the production geyser adapter with native, legacy and incompatible APIs.

The packet sink validates the particle's actual declared data type. The same Java
probe is also runnable against the pinned Paper APIs with their real data classes.
"""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/ArenaInfernoGeyserEffects.java"
PROBE = ROOT / "tests/fixtures/InfernoGeyserEffectsProbe.java"


@pytest.mark.parametrize("api", ["native", "legacy", "incompatible"])
def test_inferno_eruption_uses_typed_native_components_or_legacy_fallback(tmp_path, api):
    # Wrong particle data, emitter fan-out, duplicate audio, swallowed send errors,
    # or sending to an offline/other-world viewer must fail this real adapter probe.
    geysers = "" if api == "legacy" else ", GEYSER_PLUME(Geyser.class), GEYSER_BASE(GeyserBase.class)"
    if api == "incompatible":
        geysers = ", GEYSER_PLUME(String.class), GEYSER_BASE(GeyserBase.class)"
    sources = {
        "org/bukkit/Particle.java": """
package org.bukkit;
public enum Particle {
    CLOUD(Void.class)""" + geysers + """;
    private final Class<?> dataType;
    Particle(Class<?> type) { dataType = type; }
    public Class<?> getDataType() { return dataType; }
    public static final class Geyser {
        private final int waterBlocks;
        public Geyser(int blocks) { if (blocks < 1) throw new IllegalArgumentException(); waterBlocks=blocks; }
        public int getWaterBlocks() { return waterBlocks; }
    }
    public static final class GeyserBase {
        private final int waterBlocks; private final float burstImpulse;
        public GeyserBase(int blocks, float impulse) {
            if (blocks < 1) throw new IllegalArgumentException();
            waterBlocks=blocks; burstImpulse=impulse;
        }
        public int getWaterBlocks() { return waterBlocks; }
        public float getBurstImpulse() { return burstImpulse; }
    }
}
""",
        "org/bukkit/World.java": "package org.bukkit; public interface World {}",
        "org/bukkit/Location.java": """
package org.bukkit;
public class Location {
    private final World world;
    public Location(World world, double x, double y, double z) { this.world = world; }
    public World getWorld() { return world; }
}
""",
        "org/bukkit/SoundCategory.java": "package org.bukkit; public enum SoundCategory { HOSTILE }",
        "org/bukkit/entity/Player.java": """
package org.bukkit.entity;
import org.bukkit.*;
public interface Player {
    boolean isOnline();
    World getWorld();
    <T> void spawnParticle(Particle particle, Location point, int count,
        double x, double y, double z, double extra, T data);
    void spawnParticle(Particle particle, Location point, int count,
        double x, double y, double z, double extra);
    void playSound(Location point, String id, SoundCategory category, float volume, float pitch);
}
""",
    }
    paths = []
    for name, content in sources.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        paths.append(path)
    paths.append(PROBE)
    if SOURCE.exists():
        paths.append(SOURCE)
    compile_result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, paths)],
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr
    result = subprocess.run(
        ["java", "-cp", str(tmp_path), "me.copimine.endevent.InfernoGeyserEffectsProbe", api],
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    if api == "incompatible":
        assert "geyser" in result.stderr.lower() and "WARNING" in result.stderr, result.stderr
