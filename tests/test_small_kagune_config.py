"""Execute small-Kagune configuration parsing and validation from production."""

from pathlib import Path
import subprocess

from tests.test_boss_phase_damage_transaction_adapter import _method


ROOT = Path(__file__).resolve().parents[1]


def test_small_kagune_config_preserves_legacy_defaults_and_rejects_unsafe_values(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventConfig.java").read_text(encoding="utf-8")
    parser = _method(source, "private static SmallKaguneTuning smallKaguneTuning(")
    record = _method(source, "public record SmallKaguneTuning(")
    probe = tmp_path / "SmallKaguneConfigProbe.java"
    probe.write_text(r'''
import java.util.*;
public class SmallKaguneConfigProbe {
    static final class ConfigurationSection {
        final Map<String, Object> values;
        ConfigurationSection(Map<String, Object> values) { this.values = values; }
        boolean getBoolean(String key, boolean fallback) { return (boolean) values.getOrDefault(key, fallback); }
        int getInt(String key, int fallback) { return ((Number) values.getOrDefault(key, fallback)).intValue(); }
        double getDouble(String key, double fallback) { return ((Number) values.getOrDefault(key, fallback)).doubleValue(); }
    }
''' + record + "\n" + parser + r'''
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void invalid(String key, Object value) {
        try {
            smallKaguneTuning(new ConfigurationSection(Map.of(key, value)));
            throw new AssertionError("accepted unsafe config " + key + "=" + value);
        } catch (IllegalArgumentException | IllegalStateException expected) { }
    }
    public static void main(String[] args) {
        var defaults = smallKaguneTuning(null);
        check(defaults.enabled() && defaults.maxActive() == 4 && defaults.health() == 20.0
                && defaults.damage() == 6.0 && defaults.range() == 3.0
                && defaults.lifetimeTicks() == 600 && defaults.scale() == 0.6,
                "an existing config without the new block changed the approved small Kagune defaults");
        var empty = smallKaguneTuning(new ConfigurationSection(Map.of()));
        check(defaults.equals(empty), "an empty optional block must use the same defaults");
        var custom = smallKaguneTuning(new ConfigurationSection(Map.of(
                "enabled", false, "max-active", 2, "health", 24.0, "damage", 6.0,
                "range", 2.5, "lifetime-ticks", 300, "scale", 0.5)));
        check(!custom.enabled() && custom.maxActive() == 2 && custom.health() == 24.0
                && custom.damage() == 6.0 && custom.range() == 2.5
                && custom.lifetimeTicks() == 300 && custom.scale() == 0.5,
                "configured gameplay tuning was ignored or swapped");
        for (String key : new String[]{"health", "damage", "range", "scale"}) {
            invalid(key, Double.NaN); invalid(key, Double.POSITIVE_INFINITY);
            invalid(key, Double.NEGATIVE_INFINITY);
        }
        invalid("max-active", 0); invalid("max-active", 7);
        invalid("health", 0.0); invalid("health", 337.0);
        invalid("damage", -1.0); invalid("damage", 21.0);
        invalid("range", 0.49); invalid("range", 5.01);
        invalid("lifetime-ticks", 19); invalid("lifetime-ticks", 1201);
        invalid("scale", 0.19); invalid("scale", 0.91);
        var lower = smallKaguneTuning(new ConfigurationSection(Map.of(
                "max-active", 1, "health", 1.0, "damage", 0.0,
                "range", 0.5, "lifetime-ticks", 20, "scale", 0.2)));
        var upper = smallKaguneTuning(new ConfigurationSection(Map.of(
                "max-active", 6, "health", 336.0, "damage", 20.0,
                "range", 5.0, "lifetime-ticks", 1200, "scale", 0.9)));
        check(lower.maxActive() == 1 && upper.maxActive() == 6,
                "inclusive controller budget limits were rejected");
        try {
            new SmallKaguneTuning(true, 7, 12, 4, 3, 600, 0.6);
            throw new AssertionError("direct record construction bypassed safety validation");
        } catch (IllegalArgumentException expected) { }
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(probe)],
                              capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), "SmallKaguneConfigProbe"],
                              capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
