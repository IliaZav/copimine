package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import me.copimine.protocol.KaguneSpellFrame;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

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
            pose.popPose();
        }
    }

    static void energy(PoseStack pose, VertexConsumer buffer, KaguneSpellFrame frame,
                       double progress, int plates) {
        if (frame.stage() == KaguneSpellFrame.WARNING) return;
        boolean spear = frame.kind() == KaguneSpellFrame.STAB;
        boolean calm = SpellVisualRenderer.reducedMotion();
        var quality = SpellVisualRenderer.quality();
        double[][] section = SpellVisualRenderer.profile(quality, frame.stage() == KaguneSpellFrame.STRIKING);
        int samples = quality.samples() + 2;
        double length = KaguneSpellFrame.HEIGHT;
        double buried = length * (1 - frame.rise(progress));
        double pitch = frame.pitch(progress);
        double firstVisible = Math.min(length, buried / Math.max(.2D, Math.cos(pitch)) + .1D);
        double visible = length - firstVisible;
        if (visible < .12D) return;
        double fade = frame.stage() == KaguneSpellFrame.RETRACTING ? 1 - progress : 1;
        int strands = plates >= 8 && quality != SpellVisualGeometry.Quality.REDUCED ? 3 : 2;
        for (var point : frame.points()) {
            pose.pushPose();
            pose.translate(point.x(), point.y() - buried, point.z());
            pose.rotate(Axis.YP, (float) (Math.PI / 2D - frame.heading()));
            pose.rotate(Axis.XP, (float) pitch);
            mantle(pose.last(), buffer, frame.stage(), spear, firstVisible, visible, fade, section, samples);
            for (int strand = 0; strand < strands; strand++) {
                Vec3[] centers = new Vec3[samples];
                int[] colors = new int[samples];
                var radii = new ArrayList<Float>(samples);
                for (int i = 0; i < samples; i++) {
                    double t = i / (double) (samples - 1);
                    double y = firstVisible + visible * t;
                    double contact = frame.stage() == KaguneSpellFrame.STRIKING ? 1.25 : .75;
                    double turn = strand * (Math.PI * 2 / strands) + (spear ? 3.25 : 1.8) * y;
                    if (!calm) turn += (spear ? -1 : 1) * progress * 2.8;
                    double distance = (spear ? .36 : .48) * (.25 + .75 * Math.sin(Math.PI * .87 * t));
                    centers[i] = new Vec3(Math.cos(turn) * distance, y, Math.sin(turn) * distance);
                    radii.add((float) (.025 + .09 * contact * Math.sin(Math.PI * t)));
                    double hot = Math.exp(-14 * Math.pow(t - (spear ? .86 : .54), 2));
                    int rgb = hot > .8 ? (spear ? 0xffd6a3 : 0xceefff) : (spear ? 0xff7435 : 0xa84df0);
                    int alpha = (int) (fade * (78 + 154 * hot));
                    colors[i] = alpha << 24 | rgb;
                }
                SpellVisualRenderer.tube(buffer, pose.last(), centers, radii, colors, section);
            }
            pose.popPose();
        }
    }

    private static void mantle(PoseStack.Pose pose, VertexConsumer buffer, int stage, boolean spear,
                               double base, double height, double fade, double[][] section, int samples) {
        Vec3[] centers = new Vec3[samples];
        int[] colors = new int[samples];
        var radius = new ArrayList<Float>(samples);
        for (int i = 0; i < samples; i++) {
            double t = i / (double) (samples - 1);
            double shape = (spear ? Math.pow(1 - t, .45) : Math.sin(Math.PI * (.18 + .82 * t)));
            double muscle = .82 + .18 * Math.cos(t * Math.PI * 6);
            double wake = height * (spear ? .09 : .17) * Math.sin(Math.PI * t) * t;
            centers[i] = new Vec3(spear ? 0 : wake, base + height * t, spear ? wake : -wake * .38);
            radius.add((float) (.014 + (spear ? .34 : .47) * shape * muscle));
            int hue = spear ? (t > .65D ? 0xffb578 : 0x773121) : (t > .7D ? 0xca8bff : 0x642589);
            int alpha = (int) ((stage == KaguneSpellFrame.STRIKING ? 139 : 96) * fade * Math.sin(Math.PI * t));
            colors[i] = alpha << 24 | hue;
        }
        SpellVisualRenderer.tube(buffer, pose, centers, radius, colors, section);
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
