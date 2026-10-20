package me.copimine.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Render-only definition for a Last Seal guardian-shield carrier.  Position,
 * yaw, lifecycle, and hit resolution remain entirely server authoritative;
 * this class only describes the visible violet shield plate at that carrier.
 */
public final class EndRiftGuardianShieldModel {
    public static final String VISUAL_ID = "END_RIFT_GUARDIAN_SHIELD_V1";
    /** Must remain in lockstep with CopiMineEndEvent.MODEL_RIFT_GUARDIAN_SHIELD. */
    public static final int SERVER_CUSTOM_MODEL_DATA = 830020;
    public static final int TEXTURE_SIZE = 512;
    private static final float TEXTURE_U_MIN = 96.0F / TEXTURE_SIZE;
    private static final float TEXTURE_U_MAX = 416.0F / TEXTURE_SIZE;
    private static final float TEXTURE_V_MIN = 20.0F / TEXTURE_SIZE;
    private static final float TEXTURE_V_MAX = 492.0F / TEXTURE_SIZE;
    private static final int TRANSLUCENT_WHITE_TINT = 0xA6FFFFFF;
    private static final int DARK_VIOLET_SIDE_TINT = 0xA0181748;
    /** Kept in sync with ShieldOrbitPolicy.BOB_HEIGHT. */
    private static final double ORBIT_BOB_HEIGHT = 0.18D;
    private static final List<Point> OUTLINE = List.of(
            new Point(-6.0F, -14.0F),
            new Point(6.0F, -14.0F),
            new Point(8.0F, -11.0F),
            new Point(8.0F, -2.0F),
            new Point(5.0F, 6.0F),
            new Point(0.0F, 10.0F),
            new Point(-5.0F, 6.0F),
            new Point(-8.0F, -2.0F),
            new Point(-8.0F, -11.0F));
    private static final List<Triangle> FRONT_TRIANGLES = triangulate(OUTLINE);
    private static final List<Quad> FRONT_QUADS = FRONT_TRIANGLES.stream()
            .map(triangle -> new Quad(triangle.first(), triangle.second(),
                    triangle.third(), triangle.third()))
            .toList();

    private EndRiftGuardianShieldModel() {
    }

    public static boolean isServerCustomModelData(int value) {
        return value == SERVER_CUSTOM_MODEL_DATA;
    }

    /** A bounded visual pulse, independent of all shield gameplay decisions. */
    public static Pose pose(float phase) {
        float safe = Float.isFinite(phase) ? phase : 0.0F;
        float pulse = (float) Math.sin(safe * Math.PI * 2.0F) * 0.035F;
        return new Pose(-0.12F + pulse, 0.68F + Math.abs(pulse), 1);
    }

    /** ModelPart has already converted its authored pixel cuboids to blocks. */
    public static float worldRenderScale(Pose pose) {
        return pose == null || !pose.isFinite() ? 0.0F : pose.scale();
    }

    /** Atlas Y grows down; Minecraft world Y grows up. */
    public static float worldY(Point point) {
        return -point.y() / 16.0F;
    }

    /** Seed a render-only orbit from the real server carrier and boss positions. */
    public static Orbit orbitFromCarrier(double bossX, double bossY, double bossZ,
                                         double carrierX, double carrierY, double carrierZ,
                                         double clientTick) {
        double dx = carrierX - bossX;
        double dz = carrierZ - bossZ;
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0.0D) {
            angle += 360.0D;
        }
        double verticalOffset = carrierY - bossY
                - Math.sin(Math.toRadians(angle * 2.0D)) * ORBIT_BOB_HEIGHT;
        return new Orbit(angle, Math.hypot(dx, dz), clientTick, verticalOffset);
    }

    /** The server yaw is orbit angle + 90 degrees; the mesh's -Z face must face radially. */
    public static float faceRotationDegrees(float previousCarrierYaw, float carrierYaw,
                                            float tickDelta) {
        if (!Float.isFinite(previousCarrierYaw) || !Float.isFinite(carrierYaw)) {
            return 0.0F;
        }
        float progress = Float.isFinite(tickDelta)
                ? Math.max(0.0F, Math.min(1.0F, tickDelta)) : 1.0F;
        float delta = (carrierYaw - previousCarrierYaw) % 360.0F;
        if (delta > 180.0F) {
            delta -= 360.0F;
        } else if (delta < -180.0F) {
            delta += 360.0F;
        }
        return -(previousCarrierYaw + delta * progress);
    }

    /** Keeps the violet atlas color while blending each physical shield plate. */
    public static int renderTint() {
        return TRANSLUCENT_WHITE_TINT;
    }

    public static int sideTint() {
        return DARK_VIOLET_SIDE_TINT;
    }

    /** Pixel-space convex shield boundary: broad shoulders taper to one point. */
    public static List<Point> outline() {
        return OUTLINE;
    }

    /** Source triangulation used to construct the native render-layer quads. */
    public static List<Triangle> frontTriangles() {
        return FRONT_TRIANGLES;
    }

    /** Minecraft's entity translucent layer consumes quads, including triangular faces. */
    public static List<Quad> frontQuads() {
        return FRONT_QUADS;
    }

    public static float textureU(Point point) {
        return TEXTURE_U_MIN + (point.x() + 8.0F) / 16.0F
                * (TEXTURE_U_MAX - TEXTURE_U_MIN);
    }

    public static float textureV(Point point) {
        return TEXTURE_V_MIN + (point.y() + 14.0F) / 24.0F
                * (TEXTURE_V_MAX - TEXTURE_V_MIN);
    }

    private static List<Triangle> triangulate(List<Point> outline) {
        List<Triangle> triangles = new ArrayList<>(outline.size() - 2);
        Point first = outline.get(0);
        for (int index = 1; index < outline.size() - 1; index++) {
            triangles.add(new Triangle(first, outline.get(index), outline.get(index + 1)));
        }
        return List.copyOf(triangles);
    }

    public record Point(float x, float y) {
    }

    public record Triangle(Point first, Point second, Point third) {
    }

    public record Quad(Point first, Point second, Point third, Point fourth) {
    }

    public record Pose(float shieldRoll, float scale, int plateCount) {
        public boolean isFinite() {
            return Float.isFinite(shieldRoll)
                    && Float.isFinite(scale) && scale > 0.0F && plateCount == 1;
        }
    }

    /** The server owns carrier hitboxes; this clock only fills in frames between packets. */
    public record Orbit(double firstAngleDegrees, double radius, double firstClientTick,
                        double verticalOffsetFromBoss) {
        private static final double DEGREES_PER_TICK = 360.0D / 240.0D;

        public boolean isFinite() {
            return Double.isFinite(firstAngleDegrees) && Double.isFinite(radius)
                    && radius > 0.0D && Double.isFinite(firstClientTick)
                    && Double.isFinite(verticalOffsetFromBoss);
        }

        public double angleDegreesAt(double clientTick) {
            return firstAngleDegrees + (clientTick - firstClientTick) * DEGREES_PER_TICK;
        }

        public double xAt(double clientTick, double bossX) {
            return bossX + radius * Math.cos(Math.toRadians(angleDegreesAt(clientTick)));
        }

        public double yAt(double clientTick, double bossY) {
            double bob = Math.sin(Math.toRadians(angleDegreesAt(clientTick) * 2.0D))
                    * ORBIT_BOB_HEIGHT;
            return bossY + verticalOffsetFromBoss + bob;
        }

        public double zAt(double clientTick, double bossZ) {
            return bossZ + radius * Math.sin(Math.toRadians(angleDegreesAt(clientTick)));
        }

        public double faceRotationDegreesAt(double clientTick) {
            return -90.0D - angleDegreesAt(clientTick);
        }
    }
}
