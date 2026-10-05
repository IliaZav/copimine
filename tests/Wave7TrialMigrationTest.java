import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.runtime.RealitySplitTrialController;
import me.copimine.endevent.runtime.RealitySplitTrialSnapshot;

/** Legacy identities remain legacy; foreign versions cannot be silently resumed. */
public final class Wave7TrialMigrationTest {
    public static void main(String[] args) {
        var source = new RealitySplitTrialController();
        source.begin(73L, ChamberIsolationPolicy.assign(List.of(
                new UUID(0, 1), new UUID(0, 2), new UUID(0, 3), new UUID(0, 4))));
        source.hitReflectionSeal(73L, 1, 0, true);
        var encoded = new LinkedHashMap<>(RealitySplitTrialSnapshot.encode(73L, source.snapshot()));
        if (args.length > 0 && args[0].equals("schema")) {
            encoded.put("reality-split-trial.schema", "999");
            rejected(encoded, "foreign schema must enter recovery instead of decoding as old trials");
        } else if (args.length > 0 && args[0].equals("layout")) {
            encoded.put("reality-split-trial.schema", "1");
            encoded.put("reality-split-trial.layout", "warden-echo-marksman-archmage");
            rejected(encoded, "named layout must not reinterpret legacy room outcomes");
        } else {
            check("1".equals(encoded.get("reality-split-trial.schema")),
                    "new checkpoints explicitly record their trial schema");
            check("legacy-four-trials".equals(encoded.get("reality-split-trial.layout")),
                    "legacy actor runtime must identify its actual layout");
            var legacy = new LinkedHashMap<>(encoded);
            legacy.remove("reality-split-trial.schema");
            legacy.remove("reality-split-trial.layout");
            var decoded = RealitySplitTrialSnapshot.decode(legacy, 73L);
            check(decoded.trials().get(1).trial() == RealitySplitTrialController.Trial.RIFT_REFLECTION
                    && decoded.trials().get(1).progress() == 1,
                    "unversioned legacy seals stay Reflection seals, never Echo completions");
            check(decoded.trials().get(2).trial() == RealitySplitTrialController.Trial.JUGGERNAUT
                    && decoded.trials().get(3).trial() == RealitySplitTrialController.Trial.RIFT_HUNTER,
                    "old identities must not be remapped through enum ordinals");
            legacy.put("reality-split-trial.schema", "1");
            rejected(legacy, "partial schema metadata must not be treated as an unversioned save");
            var invalid = new LinkedHashMap<>(encoded);
            invalid.put("reality-split-trial.schema", "invalid");
            rejected(invalid, "invalid schema value must be rejected");
            check(RealitySplitTrialSnapshot.decode(Map.of("wave", "7"), 73L).trials().isEmpty(),
                    "absent trials remain distinct from an incompatible trial snapshot");
        }
        System.out.println("Wave7TrialMigrationTest OK");
    }
    private static void rejected(Map<String, String> state, String message) {
        try {
            RealitySplitTrialSnapshot.decode(state, 73L);
        } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
