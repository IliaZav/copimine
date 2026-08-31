import me.copimine.endevent.domain.ZoneVisualPolicy;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ZoneVisualPolicyTest {
    public static void main(String[] args) {
        Map<ZoneVisualPolicy.ZoneState, ZoneVisualPolicy.Profile> profiles = ZoneVisualPolicy.profiles();
        require(profiles.size() == ZoneVisualPolicy.ZoneState.values().length,
                "every zone state must have a visual profile");

        Set<Integer> colors = new LinkedHashSet<>();

        for (ZoneVisualPolicy.ZoneState state : ZoneVisualPolicy.ZoneState.values()) {
            ZoneVisualPolicy.Profile profile = ZoneVisualPolicy.profile(state);
            require(profile != null, state + " profile must exist");
            require(!profile.id().isBlank(), state + " profile id must be stable");
            require(!profile.primaryParticle().isBlank(), state + " primary particle must exist");
            require(!profile.accentParticle().isBlank(), state + " accent particle must exist");
            require(!profile.primaryParticle().equals(profile.accentParticle()),
                    state + " needs distinct particles");
            require(profile.colorRgb() >= 0 && profile.colorRgb() <= 0xFFFFFF,
                    state + " color must be valid RGB");
            require(profile.floorY() >= 0.04D && profile.floorY() <= 0.12D,
                    state + " floor height must stay anchored to the floor");
            require(profile.ringPoints() > 0 && profile.ringPoints() <= 96,
                    state + " ring points must stay bounded");
            require(profile.particleBudget() > 0 && profile.particleBudget() <= 96,
                    state + " particle budget must stay bounded");
            require(colors.add(profile.colorRgb()), state + " colors must all be distinct");
        }

        requireThrows(UnsupportedOperationException.class, () -> profiles.put(ZoneVisualPolicy.ZoneState.FREE, profiles.get(ZoneVisualPolicy.ZoneState.FREE)),
                "zone profile catalog must be immutable");
        System.out.println("ZoneVisualPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireThrows(Class<? extends Throwable> expected, ThrowingRunnable action, String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + " (unexpected " + thrown.getClass().getSimpleName() + ")", thrown);
        }
        throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
