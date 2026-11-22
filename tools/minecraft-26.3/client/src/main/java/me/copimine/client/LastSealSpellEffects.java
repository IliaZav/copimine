package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import me.copimine.protocol.KaguneSpellFrame;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Textured fracture plates and rift ribbons, sampled from the construct's own frame. */
final class LastSealSpellEffects {
    static final Identifier MATERIALS = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/world/guardian_spell_materials.png");

    private LastSealSpellEffects() {}

    static void render(PoseStack pose, VertexConsumer buffer, KaguneSpellFrame frame,
                       double progress, int plateCount) {
        boolean spear = frame.kind() == KaguneSpellFrame.STAB;
        boolean warning = frame.stage() == KaguneSpellFrame.WARNING;
        double fade = frame.stage() == KaguneSpellFrame.RETRACTING ? 1D - progress : 1D;
        for (var point : frame.points()) {
            pose.pushPose();
            pose.translate(point.x(), point.y(), point.z());
            if (spear) {
                double angle = frame.heading();
                for (int i = 0; i < 5; i++) {
                    double distance = i * .55D;
                    float width = (float) (.65D + progress * .30D);
                    mesh(pose, buffer, "fault_fragment", Math.cos(angle) * distance, .10D,
                            Math.sin(angle) * distance, angle, 0F, width, .14F, .54F,
                            0xFFAD71, (int) ((warning ? 95 + 130 * progress : 209) * fade));
                }
            } else {
                for (int index = 0; index < plateCount; index++) {
                    double direction = index * (Math.PI * 2D / plateCount);
                    double radial = .56D + (index % 3) * .10D;
                    double lift = 0D;
                    if (!warning) {
                        radial += progress * .46D;
                        lift = frame.stage() == KaguneSpellFrame.EMERGING
                                ? progress * .9D : Math.max(0D, 1D - progress * progress) * .85D;
                    }
                    mesh(pose, buffer, "fault_fragment", Math.cos(direction) * radial, .08D + lift,
                            Math.sin(direction) * radial, direction, (float) (lift * 35D), .95F,
                            warning ? .12F : .29F, .52F,
                            index % 2 == 0 ? 0xD184FF : 0x7CF3ED,
                            (int) ((warning ? 110 + 110 * progress : 232) * fade));
                }
            }
            if (!warning) {
                pose.translate(0D, -KaguneSpellFrame.HEIGHT * (1D - frame.rise(progress)), 0D);
                pose.rotate(Axis.YP, (float) (Math.PI / 2D - frame.heading()));
                pose.rotate(Axis.XP, (float) frame.pitch(progress));
                double buried = KaguneSpellFrame.HEIGHT * (1D - frame.rise(progress));
                for (int strand = 0; strand < 2; strand++) {
                    ribbon(pose, buffer, strand * Math.PI + progress * 3.4D, buried,
                            spear ? .32D : .40D, spear ? 0xFFD3A3 : 0xB87CFF,
                            (int) (fade * (spear ? 209D : 176D)));
                }
            }
            pose.popPose();
        }
    }

    private static void ribbon(PoseStack pose, VertexConsumer buffer, double spin, double buried,
                               double radius, int rgb, int alpha) {
        double length = KaguneSpellFrame.HEIGHT;
        for (int i = 0; i < 12; i++) {
            double y0 = Math.max(buried + .08D, i * length / 12D);
            double y1 = (i + 1) * length / 12D;
            if (y1 <= y0) continue;
            double a = spin + y0 * 2.8D, b = spin + y1 * 2.8D;
            double x0 = Math.cos(a) * radius, z0 = Math.sin(a) * radius;
            double x1 = Math.cos(b) * radius, z1 = Math.sin(b) * radius;
            var transform = pose.last();
            vertex(transform, buffer, x0, y0, z0, .503F, .747F, rgb, alpha);
            vertex(transform, buffer, x1, y1, z1, .747F, .747F, rgb, alpha);
            vertex(transform, buffer, x1 * 1.25D, y1 + .09D, z1 * 1.25D, .747F, .503F, rgb, alpha);
            vertex(transform, buffer, x0 * 1.25D, y0 + .09D, z0 * 1.25D, .503F, .503F, rgb, alpha);
        }
    }

    private static void mesh(PoseStack pose, VertexConsumer buffer, String mesh,
                             double x, double y, double z, double yaw, float roll,
                             float sx, float sy, float sz, int rgb, int alpha) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.rotate(Axis.YP, (float) (Math.PI / 2 - yaw));
        pose.rotateDegrees(Axis.ZP, roll);
        pose.scale(sx, sy, sz);
        var transform = pose.last();
        for (var part : GuardianSpellMesh.vertices(mesh)) {
            buffer.addVertex(transform, part.x(), part.y(), part.z())
                    .setColor((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, alpha)
                    .setUv(part.u(), part.v()).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0x00F000F0).setNormal(transform, part.nx(), part.ny(), part.nz());
        }
        pose.popPose();
    }

    private static void vertex(PoseStack.Pose transform, VertexConsumer buffer, double x, double y, double z,
                               float u, float v, int rgb, int alpha) {
        buffer.addVertex(transform, (float) x, (float) y, (float) z)
                .setColor((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, alpha)
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0).setNormal(transform, 0F, 1F, 0F);
    }
}
