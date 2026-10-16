import me.copimine.endevent.domain.BossHitboxProfile;

public final class BossHitboxProfileTest {
    public static void main(String[] args) {
        BossHitboxProfile profile = BossHitboxProfile.canonical();

        check(!profile.parts().isEmpty(), "canonical profile must contain model parts");
        check(profile.part(BossHitboxProfile.PartId.HEAD) != null, "head part is required");
        check(profile.part(BossHitboxProfile.PartId.CHEST) != null, "chest part is required");
        check(profile.part(BossHitboxProfile.PartId.PELVIS) != null, "pelvis part is required");
        check(profile.part(BossHitboxProfile.PartId.LEFT_UPPER_ARM) != null,
                "left upper arm part is required");
        check(profile.part(BossHitboxProfile.PartId.LEFT_FOREARM) != null,
                "left forearm part is required");
        check(profile.part(BossHitboxProfile.PartId.RIGHT_UPPER_ARM) != null,
                "right upper arm part is required");
        check(profile.part(BossHitboxProfile.PartId.RIGHT_FOREARM) != null,
                "right forearm part is required");
        check(profile.part(BossHitboxProfile.PartId.LEFT_LEG) != null, "left leg part is required");
        check(profile.part(BossHitboxProfile.PartId.RIGHT_LEG) != null, "right leg part is required");
        check(profile.proxyCount() == 11,
                "canonical profile must keep one bounded proxy per articulated envelope");
        check(profile.proxyCount() <= BossHitboxProfile.MAX_PROXY_COUNT,
                "proxy count must stay bounded");

        for (BossHitboxProfile.Part part : profile.parts()) {
            check(part.widthModel() > 0.0D && part.depthModel() > 0.0D && part.heightModel() > 0.0D,
                    "part dimensions must be positive: " + part.id());
            check(Double.isFinite(part.centerModel().x())
                            && Double.isFinite(part.centerModel().y())
                            && Double.isFinite(part.centerModel().z()),
                    "part center must be finite: " + part.id());
            check(part.boneName() != null && !part.boneName().isBlank(),
                    "part must record its source bone: " + part.id());
        }

        BossHitboxProfile.Part left = profile.part(BossHitboxProfile.PartId.LEFT_UPPER_ARM);
        BossHitboxProfile.Part right = profile.part(BossHitboxProfile.PartId.RIGHT_UPPER_ARM);
        check(left.centerModel().x() < 0.0D, "left arm must remain on the negative model X side");
        check(right.centerModel().x() > 0.0D, "right arm must remain on the positive model X side");
        assertPart(profile.part(BossHitboxProfile.PartId.HEAD),
                0.0D, 70.6732992256D, 1.2842263817D,
                14.4644440107D, 10.4219158504D, 19.9603486544D,
                0.0D, 61.0D, 0.75D);
        assertPart(profile.part(BossHitboxProfile.PartId.CHEST),
                0.0D, 50.6138129700D, -0.2442505502D,
                11.5D, 7.8292277219D, 21.7138607270D,
                0.0D, 62.0D, 0.0D);
        assertPart(profile.part(BossHitboxProfile.PartId.PELVIS),
                0.0D, 36.6779816645D, -1.3445918024D,
                11.0D, 5.8276938105D, 7.1600929259D,
                0.0D, 62.0D, 0.0D);
        assertPart(profile.part(BossHitboxProfile.PartId.LEFT_UPPER_ARM),
                -5.2365464907D, 53.0348418104D, -3.3052346583D,
                8.1872555779D, 11.4787277965D, 21.4306459399D,
                -7.5D, 62.5D, 0.75D);
        assertPart(profile.part(BossHitboxProfile.PartId.RIGHT_UPPER_ARM),
                5.2365464907D, 53.0348418104D, -3.3052346583D,
                8.1872555779D, 11.4787277965D, 21.4306459399D,
                7.5D, 62.5D, 0.75D);
        assertPart(profile.part(BossHitboxProfile.PartId.LEFT_FOREARM),
                -5.7856480035D, 31.7140814232D, 2.2459368031D,
                7.5314943688D, 6.5299723365D, 20.2335698117D,
                -8.0D, 41.25D, 0.75D);
        assertPart(profile.part(BossHitboxProfile.PartId.RIGHT_FOREARM),
                5.7856480035D, 31.7140814232D, 2.2459368031D,
                7.5314943688D, 6.5299723365D, 20.2335698117D,
                8.0D, 41.25D, 0.75D);
        assertPart(profile.parts(BossHitboxProfile.PartId.LEFT_LEG).get(0),
                -3.0D, 29.6703755177D, -1.9648348425D,
                3.0D, 4.6059119869D, 12.7846393438D,
                -3.0D, 23.0D, 0.75D);
        assertPart(profile.parts(BossHitboxProfile.PartId.RIGHT_LEG).get(0),
                3.0D, 29.6703755177D, -1.9648348425D,
                3.0D, 4.6059119869D, 12.7846393438D,
                3.0D, 23.0D, 0.75D);
        BossHitboxProfile.Part leftLowerLeg = profile.parts(BossHitboxProfile.PartId.LEFT_LEG).get(1);
        BossHitboxProfile.Part rightLowerLeg = profile.parts(BossHitboxProfile.PartId.RIGHT_LEG).get(1);
        check(leftLowerLeg.boneName().equals("group2+group4"),
                "left lower leg must include its connector cube");
        check(rightLowerLeg.boneName().equals("group+group3"),
                "right lower leg must include its connector cube");
        assertPart(leftLowerLeg,
                -3.0D, 11.7451726525D, 0.0715123060D,
                3.0D, 7.6080144079D, 23.3920818671D,
                -3.0D, 23.5D, 1.75D);
        assertPart(rightLowerLeg,
                3.0D, 11.7451726525D, 0.0715123060D,
                3.0D, 7.6080144079D, 23.3920818671D,
                3.0D, 23.5D, 1.75D);
        check(profile.modelBounds().width() > 0.0D
                        && profile.modelBounds().height() > 0.0D
                        && profile.modelBounds().depth() > 0.0D,
                "profile bounds must be non-empty");
        check(profile.modelBounds().height() < 96.0D,
                "profile bounds must not contain a detached model island");
        System.out.println("BossHitboxProfileTest OK");
    }

    private static void assertPart(BossHitboxProfile.Part part,
                                   double centerX, double centerY, double centerZ,
                                   double width, double depth, double height,
                                   double pivotX, double pivotY, double pivotZ) {
        check(part != null, "expected canonical hitbox part is missing");
        check(close(part.centerModel().x(), centerX)
                        && close(part.centerModel().y(), centerY)
                        && close(part.centerModel().z(), centerZ),
                "model-space center drift for " + part.id() + ": " + part.centerModel());
        check(close(part.widthModel(), width)
                        && close(part.depthModel(), depth)
                        && close(part.heightModel(), height),
                "model-space size drift for " + part.id());
        check(close(part.posePivotModel().x(), pivotX)
                        && close(part.posePivotModel().y(), pivotY)
                        && close(part.posePivotModel().z(), pivotZ),
                "pose pivot drift for " + part.id());
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-6D;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
