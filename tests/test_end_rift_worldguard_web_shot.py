"""WorldGuard MOB_DAMAGE protection contract for direct Spider Web Shot effects."""

from pathlib import Path
import subprocess

import yaml


ROOT = Path(__file__).resolve().parents[1]
GUARD = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/compat/WorldGuardMobDamageGuard.java"
PLUGIN_YML = ROOT / "copimine-end-event/plugin.yml"


FIXTURES = {
    "org/bukkit/Location.java": """
package org.bukkit;
public final class Location {}
""",
    "org/bukkit/entity/Player.java": """
package org.bukkit.entity;
import org.bukkit.Location;
public interface Player { Location getLocation(); }
""",
    "org/bukkit/plugin/Plugin.java": """
package org.bukkit.plugin;
public interface Plugin { boolean isEnabled(); }
""",
    "org/bukkit/plugin/PluginManager.java": """
package org.bukkit.plugin;
public interface PluginManager { Plugin getPlugin(String name); }
""",
    "com/sk89q/worldedit/util/Location.java": """
package com.sk89q.worldedit.util;
public final class Location {}
""",
    "com/sk89q/worldguard/LocalPlayer.java": """
package com.sk89q.worldguard;
public interface LocalPlayer extends com.sk89q.worldguard.protection.association.RegionAssociable {}
""",
    "com/sk89q/worldguard/protection/association/RegionAssociable.java": """
package com.sk89q.worldguard.protection.association;
public interface RegionAssociable {}
""",
    "com/sk89q/worldguard/protection/flags/StateFlag.java": """
package com.sk89q.worldguard.protection.flags;
public final class StateFlag {}
""",
    "com/sk89q/worldguard/protection/flags/Flags.java": """
package com.sk89q.worldguard.protection.flags;
public final class Flags {
  public static final StateFlag MOB_DAMAGE = new StateFlag();
}
""",
    "com/sk89q/worldedit/bukkit/BukkitAdapter.java": """
package com.sk89q.worldedit.bukkit;
import org.bukkit.Location;
public final class BukkitAdapter {
  public static com.sk89q.worldedit.util.Location adapt(Location location) { return new com.sk89q.worldedit.util.Location(); }
}
""",
    "com/sk89q/worldguard/protection/regions/RegionContainer.java": """
package com.sk89q.worldguard.protection.regions;
public abstract class RegionContainer {
  public abstract RegionQuery createQuery();
}
""",
    "com/sk89q/worldguard/protection/regions/RegionQuery.java": """
package com.sk89q.worldguard.protection.regions;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.StateFlag;
public class RegionQuery {
  public static boolean allowed = true;
  public static boolean broken = false;
  public boolean testState(Location location, RegionAssociable player, StateFlag... flags) {
    if (broken) throw new IllegalStateException("simulated WorldGuard query failure");
    if (flags.length != 1 || flags[0] != com.sk89q.worldguard.protection.flags.Flags.MOB_DAMAGE) {
      throw new IllegalArgumentException("expected MOB_DAMAGE");
    }
    return allowed;
  }
}
""",
    "com/sk89q/worldguard/internal/platform/WorldGuardPlatform.java": """
package com.sk89q.worldguard.internal.platform;
import com.sk89q.worldguard.protection.regions.RegionContainer;
public interface WorldGuardPlatform {
  RegionContainer getRegionContainer();
}
""",
    "com/sk89q/worldguard/bukkit/WorldGuardPlugin.java": """
package com.sk89q.worldguard.bukkit;
import com.sk89q.worldguard.LocalPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
public class WorldGuardPlugin implements Plugin {
  public static int wrappedPlayerCalls = 0;
  private final boolean enabled;
  private final boolean broken;
  public WorldGuardPlugin(boolean enabled) { this(enabled, false); }
  public WorldGuardPlugin(boolean enabled, boolean broken) { this.enabled = enabled; this.broken = broken; }
  public boolean isEnabled() {
    if (broken) throw new IllegalStateException("simulated plugin state failure");
    return enabled;
  }
  public LocalPlayer wrapPlayer(Player player) {
    wrappedPlayerCalls++;
    return new LocalPlayer() {};
  }
}
""",
    "com/sk89q/worldguard/WorldGuard.java": """
package com.sk89q.worldguard;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
public final class WorldGuard {
  private static final WorldGuard INSTANCE = new WorldGuard();
  private final WorldGuardPlatform platform = new WorldGuardPlatform() {
    public RegionContainer getRegionContainer() { return new HiddenRegionContainer(); }
  };
  private static final class HiddenRegionContainer extends RegionContainer {
    public RegionQuery createQuery() { return new RegionQuery(); }
  }
  public static WorldGuard getInstance() { return INSTANCE; }
  public WorldGuardPlatform getPlatform() { return platform; }
}
""",
    "WorldGuardGuardProbe.java": """
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import me.copimine.endevent.runtime.compat.WorldGuardMobDamageGuard;
import com.sk89q.worldguard.protection.regions.RegionQuery;

public final class WorldGuardGuardProbe {
  private static final Player PLAYER = new Player() { public Location getLocation() { return new Location(); } };
  private static PluginManager manager(Plugin plugin) { return name -> "WorldGuard".equals(name) ? plugin : null; }
  private static void check(boolean condition, String scenario) {
    if (!condition) throw new AssertionError(scenario);
  }
  public static void main(String[] args) {
    AtomicInteger warnings = new AtomicInteger();
    check(WorldGuardMobDamageGuard.isMobDamageAllowed(PLAYER, manager(null), ignored -> {}),
        "missing optional WorldGuard must preserve current behavior");
    RegionQuery.allowed = true;
    RegionQuery.broken = false;
    check(WorldGuardMobDamageGuard.isMobDamageAllowed(PLAYER, manager(new WorldGuardPlugin(true)), ignored -> {}),
        "allowed MOB_DAMAGE region must permit web effects");
    check(WorldGuardPlugin.wrappedPlayerCalls == 1,
        "WorldGuardPlugin.wrapPlayer must adapt Bukkit players to LocalPlayer");
    RegionQuery.allowed = false;
    check(!WorldGuardMobDamageGuard.isMobDamageAllowed(PLAYER, manager(new WorldGuardPlugin(true)), ignored -> {}),
        "denied MOB_DAMAGE region must block web effects");
    RegionQuery.allowed = true;
    RegionQuery.broken = true;
    check(!WorldGuardMobDamageGuard.isMobDamageAllowed(PLAYER, manager(new WorldGuardPlugin(true)), ignored -> warnings.incrementAndGet()),
        "broken installed WorldGuard API must fail closed");
    RegionQuery.broken = false;
    check(!WorldGuardMobDamageGuard.isMobDamageAllowed(PLAYER, manager(new WorldGuardPlugin(false)), ignored -> {}),
        "present but disabled WorldGuard must fail closed");
    check(!WorldGuardMobDamageGuard.isMobDamageAllowed(PLAYER, manager(new WorldGuardPlugin(true, true)), ignored -> {}),
        "broken plugin state inspection must fail closed");
    check(warnings.get() == 1, "broken API must emit at most one warning");
    System.out.println("PASS WorldGuardMobDamageGuard");
  }
}
""",
}


def test_worldguard_guard_preserves_missing_plugin_and_fails_closed_when_installed(tmp_path):
    javac = __import__("shutil").which("javac")
    java = __import__("shutil").which("java")
    if not javac or not java:
        import pytest
        pytest.skip("a local Java runtime is required for the isolated WorldGuard adapter check")

    fixture_sources = []
    for relative_path, contents in FIXTURES.items():
        path = tmp_path / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding="utf-8")
        fixture_sources.append(str(path))

    result = subprocess.run(
        [javac, "-encoding", "UTF-8", "-d", str(tmp_path), str(GUARD), *fixture_sources],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr

    result = subprocess.run([java, "-cp", str(tmp_path), "WorldGuardGuardProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert "PASS WorldGuardMobDamageGuard" in result.stdout


def test_end_event_declares_worldguard_as_an_optional_load_order_dependency():
    plugin = yaml.safe_load(PLUGIN_YML.read_text(encoding="utf-8"))
    dependencies = plugin.get("depend", [])
    optional_dependencies = plugin.get("softdepend", [])

    assert isinstance(dependencies, list)
    assert isinstance(optional_dependencies, list)
    assert "WorldGuard" not in dependencies
    assert "WorldGuard" in optional_dependencies


def test_spider_web_checks_worldguard_before_applying_player_effects():
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8"
    )
    signature = "private void applyEliteSpiderWeb("
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                method = source[opening + 1:index]
                break
    else:
        raise AssertionError("unterminated Spider Web Shot method")

    guard = "WorldGuardMobDamageGuard.isMobDamageAllowed("
    assert guard in method
    assert method.index(guard) < method.index("currentSpiderEffectOwner()")
    assert method.index(guard) < method.index("rootedPlayersUntilTick.put(")
