import me.copimine.endevent.domain.BlackFogCurtainPolicy;

public final class BlackFogCurtainPolicyTest {
    public static void main(String[] args) {
        var points = BlackFogCurtainPolicy.perimeter(18.0D, 5.0D);
        check(points.size() == BlackFogCurtainPolicy.MAX_PARTICLE_POINTS,
                "Wave 5 fog curtain must have a fixed, bounded point budget");
        check(BlackFogCurtainPolicy.POINTS_PER_RING >= 96
                        && BlackFogCurtainPolicy.VERTICAL_LAYERS >= 8
                        && points.size() >= 768,
                "fog wall fills the arena height with evenly spaced perimeter particles");
        for (var point : points) {
            double radius = Math.hypot(point.x(), point.z());
            check(Math.abs(radius - 18.0D) < 0.0001D,
                    "every fog particle stays on the arena perimeter");
            check(point.y() >= 0.0D && point.y() <= 5.0D,
                    "every fog particle stays within the arena height");
        }
        check(BlackFogCurtainPolicy.perimeter(0.0D, 5.0D).isEmpty()
                        && BlackFogCurtainPolicy.perimeter(18.0D, 0.0D).isEmpty(),
                "invalid wall dimensions fail closed");
        System.out.println("BlackFogCurtainPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
