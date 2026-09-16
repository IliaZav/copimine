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
        check(profile.modelBounds().width() > 0.0D
                        && profile.modelBounds().height() > 0.0D
                        && profile.modelBounds().depth() > 0.0D,
                "profile bounds must be non-empty");
        check(profile.modelBounds().height() < 96.0D,
                "profile bounds must not contain a detached model island");
        System.out.println("BossHitboxProfileTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
