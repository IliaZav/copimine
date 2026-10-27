import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import me.copimine.endevent.domain.GravityAnchorPolicy.AnchorPoint;
import me.copimine.endevent.domain.GravityAnchorPolicy.Phase;
import me.copimine.endevent.runtime.ritual.RitualSpellController;
import me.copimine.endevent.runtime.ritual.Wave6RitualRuntimeAdapter;
import me.copimine.endevent.runtime.ritual.Wave6RitualRuntimeAdapter.Effects;

public class Wave6CrystalLifecycleTest {
    static final UUID CASTER = new UUID(0, 1), TARGET = new UUID(0, 2), CRYSTAL = new UUID(0, 3);
    static final class Port implements java.lang.reflect.InvocationHandler {
        String progress = "PENDING";
        boolean valid = true;
        boolean launchAccepted = true, invalidateOnLaunch;
        int spawned, pulls, cleanups, launches;
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            switch (method.getName()) {
                case "spawnEndCrystal": spawned++; return CRYSTAL;
                case "fireRiftBarrageIntoCrystal":
                    launches++;
                    if (invalidateOnLaunch) valid = false;
                    return launchAccepted;
                case "gravityAnchorBarrageProgress": {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Object value = Enum.valueOf((Class) method.getReturnType(), progress);
                    return value;
                }
                case "isGravityAnchorCastValid": return valid;
                case "captureGravityAnchorCohort": return Optional.of(List.of(TARGET));
                case "beginGravityAnchorPull": pulls++; return true;
                case "cleanupGravityAnchor": cleanups++; return true;
                default: return method.getReturnType() == boolean.class ? true : null;
            }
        }
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        if (args.length == 0) {
            for (String scenario : List.of("pending", "miss", "invalid", "timeout", "clear",
                    "invalid-after-charge", "no-barrage", "launch-fallback", "invalid-launch")) {
                main(new String[]{scenario});
            }
            return;
        }
        Port port = new Port();
        port.launchAccepted = !args[0].equals("launch-fallback") && !args[0].equals("invalid-launch");
        port.invalidateOnLaunch = args[0].equals("invalid-launch");
        Effects effects = (Effects) Proxy.newProxyInstance(Effects.class.getClassLoader(),
                new Class<?>[]{Effects.class}, port);
        var adapter = new Wave6RitualRuntimeAdapter(new RitualSpellController(7), effects);
        int shots = args[0].equals("no-barrage") ? 0 : 1;
        var started = adapter.startGravityAnchor(7, CASTER, TARGET, new AnchorPoint(2, 70, 2),
                shots, true, true, true, true, 0);
        if (args[0].equals("invalid-launch")) {
            check(!started.accepted() && port.cleanups == 1 && port.pulls == 0,
                    "invalid cast must not become a standalone anchor after failed launch");
            return;
        }
        check(started.accepted(), "start failed: " + args[0]);
        switch (args[0]) {
            case "pending" -> {
                var state = adapter.tick(7, 40);
                check(port.pulls == 0 && state.stage() == RitualSpellController.Stage.EXECUTE,
                        "pull started before a real projectile impact");
                port.progress = "CHARGED";
                check(adapter.tick(7, 45).gravityAnchorState().phase() == Phase.PULL && port.pulls == 1,
                        "charged crystal must begin exactly one pull after warning");
            }
            case "miss" -> {
                port.progress = "FAILED";
                var state = adapter.tick(7, 5);
                check(state.stage() == RitualSpellController.Stage.IDLE && port.cleanups == 1 && port.pulls == 0,
                        "a missed or rejected projectile must cancel and clean up the cast");
            }
            case "invalid" -> {
                port.valid = false;
                var state = adapter.tick(7, 5);
                check(state.stage() == RitualSpellController.Stage.IDLE && port.cleanups == 1 && port.pulls == 0,
                        "an invalid caster, target, crystal, event or generation must cancel the cast");
            }
            case "timeout" -> {
                var state = adapter.tick(7, Wave6RitualRuntimeAdapter.GRAVITY_ANCHOR_BARRAGE_TIMEOUT_TICKS);
                check(state.stage() == RitualSpellController.Stage.IDLE && port.cleanups == 1 && port.pulls == 0,
                        "an unconfirmed projectile must time out and release the cast");
            }
            case "clear" -> {
                adapter.clear(7);
                port.progress = "CHARGED";
                var state = adapter.tick(7, 40);
                check(state.stage() == RitualSpellController.Stage.IDLE && port.cleanups == 1 && port.pulls == 0,
                        "reset while shots are in flight must never revive a stale cast");
            }
            case "invalid-after-charge" -> {
                port.progress = "CHARGED";
                check(adapter.tick(7, 40).gravityAnchorState().phase() == Phase.PULL, "pull did not start");
                port.valid = false;
                var state = adapter.tick(7, 45);
                check(state.stage() == RitualSpellController.Stage.IDLE && port.cleanups == 1,
                        "an invalid active cast must be cleaned up");
            }
            case "no-barrage" -> {
                var state = adapter.tick(7, 40);
                check(state.gravityAnchorState().phase() == Phase.PULL && port.launches == 0,
                        "a standalone anchor must not wait for nonexistent projectiles");
            }
            case "launch-fallback" -> {
                check(port.spawned == 1 && port.cleanups == 0 && port.launches == 1
                                && started.snapshot().combinedRiftBarrageProjectiles() == 0,
                        "failed combo prerequisites must retain one crystal as a standalone anchor");
                check(adapter.tick(7, 39).stage() == RitualSpellController.Stage.TELEGRAPH
                                && port.pulls == 0,
                        "fallback skipped the full anchor warning");
                check(adapter.tick(7, 40).gravityAnchorState().phase() == Phase.PULL
                                && port.pulls == 1,
                        "fallback waited for projectiles that were never launched");
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
        System.out.println("WAVE6_CRYSTAL_LIFECYCLE_PASS " + args[0]);
    }
}
