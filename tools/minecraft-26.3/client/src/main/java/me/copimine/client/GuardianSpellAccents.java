package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

/** Local-space energy around the authored Guardian bodies. The entity owns the world transform. */
final class GuardianSpellAccents {
    private enum Shape { ORBIT, WAKE, ROOT, ARCH, BARB, FRACTURE }

    private record Profile(Shape shape, int count, double width, double height, double twist,
                           double thickness, int shadow, int core) { }

    private static final double TAU = Math.PI * 2.0;
    private static final java.util.Map<String, Profile> PROFILES = java.util.Map.ofEntries(
            entry("void_core", Shape.ORBIT, 3, .87, .85, 3.0, .08, 0x441968, 0xf9abff),
            entry("inferno_core", Shape.WAKE, 5, .35, 2.65, 2.7, .09, 0xee3916, 0xffdb73),
            entry("rift_lance", Shape.WAKE, 3, .18, 1.9, -4.9, .045, 0x315bff, 0xc4faff),
            entry("echo_shard", Shape.ORBIT, 3, .69, .59, -2.0, .05, 0x017687, 0xb0fff8),
            entry("final_seal", Shape.ARCH, 5, 1.15, 1.76, .5, .09, 0xc31e7b, 0xffc7ef),
            entry("chest_iris", Shape.ARCH, 4, .88, .9, 1.3, .09, 0x9635d9, 0xffeda8),
            entry("kagune_splinter", Shape.ROOT, 4, .66, 1.43, 1.0, .10, 0x572268, 0xf4a2ff),
            entry("fault_fragment", Shape.FRACTURE, 4, 1.20, .4, .8, .10, 0xba3919, 0xffdc9c),
            entry("void_rune", Shape.ROOT, 5, .94, .63, -1.8, .055, 0x7518a8, 0xffaafb),
            entry("inferno_crown", Shape.ROOT, 5, .93, 1.61, 1.8, .10, 0xd83313, 0xffc657),
            entry("summon_arch", Shape.ARCH, 3, .86, 1.25, -.8, .075, 0x147d8f, 0xa1ffed),
            entry("arrow_rack", Shape.BARB, 5, 1.08, .68, .25, .035, 0x755bff, 0xffedc8),
            entry("rift_gate", Shape.ARCH, 2, .75, 1.1, .2, .047, 0x328bbd, 0xb7efff),
            entry("snare_chains", Shape.ROOT, 3, .78, .85, -1.2, .042, 0x6642b2, 0xe9cdff),
            entry("echo_bloom", Shape.ORBIT, 2, .77, .45, .2, .049, 0x077eab, 0xc7ffee),
            entry("euphoria_helix", Shape.ROOT, 2, .5, 1.05, 3.7, .046, 0xa0219f, 0xffbcf1),
            // §9H CHEST BEAM — dramatic arch with core strands, hot cross-section
            entry("chest_beam", Shape.ARCH, 5, .85, .82, 1.75, .07, 0xb42ed8, 0xfff0f6),
            // §9D GEYSER — fracture eruption with rising tongues, lively ember scatter
            entry("geyser", Shape.FRACTURE, 6, 1.10, 1.40, -.85, .09, 0xe04a19, 0xffd791),
            entry("rift_fracture", Shape.FRACTURE, 4, 1.21, .62, .52, .072, 0xae241b, 0xffa971),
            entry("inferno_geyser", Shape.ROOT, 4, .68, 1.96, 2.3, .085, 0xd42a11, 0xffcf72),
            entry("kagune_stab", Shape.BARB, 5, .57, 1.45, .6, .089, 0x6e139b, 0xffb2f2),
            entry("kagune_eruption", Shape.ROOT, 5, .94, 1.67, -.92, .098, 0x6b20aa, 0xafd6ff),
            entry("kagune_small", Shape.ROOT, 3, .83, .70, .64, .061, 0x5e2a92, 0xf4bdff),
            // V5.1 REMASTER — void_crush: ground-level expanding fracture ring
            entry("void_crush", Shape.FRACTURE, 8, 1.25, .55, .35, .075, 0x442299, 0xd8b0ff),
            // claw_chain: fast barb-like slash ribbons
            entry("claw_chain", Shape.BARB, 4, .65, 1.80, 1.2, .055, 0x992255, 0xffc0e0),
            // dimensional_carve: arch-like portal tear
            entry("dimensional_carve", Shape.ARCH, 3, .95, 1.55, .45, .060, 0x226699, 0xa0d8ff),
            // V7 — shoulder_slam: compact burst accent
            entry("shoulder_slam", Shape.FRACTURE, 4, .75, .65, .50, .055, 0xcc6622, 0xffc8a0),
            // V7 — hunt_dash: long wake trail
            entry("hunt_dash", Shape.WAKE, 3, .45, 2.1, 2.5, .050, 0x5533aa, 0xc0a0ff),
            // V7 — grab_shove: close barb burst
            entry("grab_shove", Shape.BARB, 3, .50, .85, .60, .048, 0x992244, 0xff80a0),
            // V7 — seal_lance: vertical arch volley
            entry("seal_lance", Shape.ARCH, 4, .70, 2.0, .30, .062, 0x551188, 0xc080ff),
            // V7 — ring_of_kagune: wide orbit ring
            entry("ring_of_kagune", Shape.ORBIT, 6, 1.4, .60, .80, .065, 0x6600aa, 0xcc66ff),
            // V7 — ambush_grab: root emergence
            entry("ambush_grab", Shape.ROOT, 4, .72, 1.3, .90, .058, 0x881166, 0xcc44aa),
            // V7 — infernal_surge: fracture wall
            entry("infernal_surge", Shape.FRACTURE, 5, 1.3, .80, .40, .070, 0xcc3300, 0xff6633),
            // V7 — mirror_carnage: wake shatter
            entry("mirror_carnage", Shape.WAKE, 4, .60, 1.5, .70, .052, 0x334488, 0x88aaff),
            // V7 — inferno_curtain: root fall
            entry("inferno_curtain", Shape.ROOT, 5, .90, 1.8, -.60, .068, 0xaa2200, 0xff4400),
            // V7 — earthquake_wave: fracture ground
            entry("earthquake_wave", Shape.FRACTURE, 6, 1.5, .35, .30, .072, 0x886633, 0xcc9944),
            // V7 — spire_harbinger: root tall
            entry("spire_harbinger", Shape.ROOT, 3, .55, 2.2, .80, .055, 0x336633, 0x88cc88));

    private GuardianSpellAccents() { }

    private static java.util.Map.Entry<String, Profile> entry(String mesh, Shape shape, int count,
            double width, double height, double twist, double thickness, int shadow, int core) {
        return java.util.Map.entry(mesh, new Profile(shape, count, width, height, twist, thickness, shadow, core));
    }

    static void draw(PoseStack.Pose transform, VertexConsumer buffer, GuardianSpellRenderer.Pose pose) {
        Profile profile = PROFILES.get(pose.mesh());
        if (profile == null) return;
        boolean hero = pose.stage() != 0;
        boolean calm = SpellVisualRenderer.reducedMotion();
        var quality = SpellVisualRenderer.quality();
        double[][] ring = SpellVisualRenderer.profile(quality, hero);
        int count = hero ? profile.count : Math.min(2, profile.count);
        if (quality == SpellVisualGeometry.Quality.REDUCED) count = Math.min(3, count);
        int nodes = Math.max(5, quality.samples());
        int alpha = hero ? 206 : 125;
        double p = Math.clamp(pose.age() / pose.duration(), 0, 1);
        double gain = switch (pose.stage()) {
            case 1 -> ease(p);
            case 2 -> 1 - Math.pow(p, 2.4);
            default -> 1;
        };
        double clock = calm ? 0 : pose.age() * .19;
        for (int strand = 0; strand < count; strand++) {
            double angle = TAU * strand / count;
            Vec3[] points = new Vec3[nodes];
            int[] color = new int[nodes];
            var radius = new ArrayList<Float>(nodes);
            for (int i = 0; i < nodes; i++) {
                double t = i / (double) (nodes - 1);
                double along = Math.sin(Math.PI * t);
                double width = profile.width * (pose.stage() == 1 ? 1.34 - .34 * gain
                        : pose.stage() == 2 ? .82 + .58 * p : 1);
                points[i] = point(profile, angle, width, t, gain, clock, strand);
                radius.add((float) (profile.thickness * (.18 + .82 * along) * (.56 + .44 * gain)));
                double flow = calm ? 1 : .78 + .22 * Math.sin(9 * t + clock * 2.1 + angle);
                int opacity = (int) Math.clamp(alpha * flow * (.22 + .78 * along) * gain, 0, 255);
                color[i] = opacity << 24 | mix(profile.shadow, profile.core, along * (.72 + .28 * flow));
            }
            SpellVisualRenderer.tube(buffer, transform, points, radius, color, ring);
        }
    }

    private static Vec3 point(Profile profile, double angle, double width,
                              double t, double gain, double clock, int strand) {
        double wave = Math.sin(Math.PI * t);
        double twist = profile.twist;
        double h = profile.height;
        return switch (profile.shape) {
            case ORBIT -> {
                double sweep = t * TAU * .89 + clock * .55;
                double tilt = (strand % 2 == 0 ? .62 : -.70);
                double x = Math.cos(sweep) * width;
                double z = Math.sin(sweep) * width;
                yield new Vec3(Math.cos(angle) * x - Math.sin(angle) * z * .42, z * h * tilt / width,
                        Math.sin(angle) * x + Math.cos(angle) * z * .42);
            }
            case WAKE -> {
                double spiral = angle + twist * t - clock * .8;
                double r = width * (.2 + .8 * wave);
                yield new Vec3(Math.cos(spiral) * r, Math.sin(spiral) * r, .55 - h * t);
            }
            case ROOT -> {
                double turn = angle + twist * t + clock * .22 * wave;
                double radial = width * (.29 + .71 * Math.pow(t, .66));
                double lift = h * (t + .33 * wave) * (.49 + .51 * gain);
                yield new Vec3(Math.cos(turn) * radial, -.32 + lift, Math.sin(turn) * radial);
            }
            case ARCH -> {
                double turn = Math.PI * t;
                double shift = clock * .18 * wave;
                yield new Vec3(Math.cos(turn) * width, -.43 + h * Math.sin(turn),
                        Math.cos(angle + shift) * width * .62 * wave + Math.sin(angle) * .27);
            }
            case BARB -> {
                double profileWidth = width * (strand - (profile.count - 1) * .5) / profile.count;
                yield new Vec3(profileWidth * (1.6 - .72 * t), h * .46 * wave - .14,
                        h * t - .65 + wave * twist);
            }
            case FRACTURE -> {
                double fork = angle + twist * Math.sin(t * TAU) * .52;
                double travel = width * t;
                yield new Vec3(Math.cos(fork) * travel, -.33 + h * wave * (.25 + .75 * gain),
                        Math.sin(fork) * travel * (strand % 2 == 0 ? 1.15 : .58));
            }
        };
    }

    private static double ease(double t) {
        return .26 + .74 * t * t * (3 - 2 * t);
    }

    private static int mix(int a, int b, double t) {
        int red = blend(a >> 16 & 255, b >> 16 & 255, t);
        int green = blend(a >> 8 & 255, b >> 8 & 255, t);
        int blue = blend(a & 255, b & 255, t);
        return red << 16 | green << 8 | blue;
    }

    private static int blend(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }
}
