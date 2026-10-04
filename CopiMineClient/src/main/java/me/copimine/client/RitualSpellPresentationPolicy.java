package me.copimine.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Presentation of server-selected spell cues; this policy never selects targets or applies damage. */
public final class RitualSpellPresentationPolicy {
    public enum Channel { GLYPH, SCREEN }
    public enum Stage { WARNING, ACTIVE }
    public enum Orientation { HORIZONTAL, BILLBOARD }
    public enum PassKind { RIBBON, RITUAL, GLYPH }

    public enum Spell {
        RIFT_BARRAGE(4.7F, Orientation.HORIZONTAL, "barrage", "Разломный обстрел"),
        GRAVITY_WELL(8.0F, Orientation.HORIZONTAL, "gravity", "Гравитационный колодец"),
        SOUL_BRAND(1.5F, Orientation.BILLBOARD, "brand", "Метка души"),
        RIFT_CHAINS(1.1F, Orientation.BILLBOARD, "chains", "Цепи разлома");

        private final float diameter;
        private final Orientation orientation;
        private final String texture;
        private final String title;

        Spell(float diameter, Orientation orientation, String texture, String title) {
            this.diameter = diameter;
            this.orientation = orientation;
            this.texture = "textures/entity/ritual_spell_" + texture + ".png";
            this.title = title;
        }

        public float diameter() { return diameter; }
        public Orientation orientation() { return orientation; }
        public String texture() { return texture; }
        public String title() { return title; }
    }

    public record Cue(Spell spell, Channel channel, Stage stage) { }
    public record Point(float x, float y, float z, float u, float v) { }
    public record WorldPass(PassKind kind, Spell spell) { }
    public record ScreenLayout(PrisonerHudLayout.Rect panel, List<PrisonerHudLayout.Rect> edgeCues,
                               PrisonerHudLayout.Rect clearView, boolean visible) { }

    private RitualSpellPresentationPolicy() { }

    /** Only the complete allowlisted suffix of an actual server world instance enables a cue. */
    public static Cue parse(String instance) {
        String key = worldKey(instance);
        if (key == null) return null;
        for (Channel channel : Channel.values()) {
            String prefix = "wave6-spell-" + (channel == Channel.GLYPH ? "glyph-" : "screen-");
            for (Spell spell : Spell.values()) {
                for (Stage stage : Stage.values()) {
                    String suffix = stage == Stage.WARNING ? "warning" : "active";
                    if (key.equals(prefix + spell.name() + "-" + suffix)) return new Cue(spell, channel, stage);
                }
            }
        }
        return null;
    }

    private static String worldKey(String instance) {
        if (instance == null) return null;
        int marker = instance.indexOf(":world:");
        if (marker <= 0 || instance.indexOf(":world:", marker + 7) >= 0) return null;
        return instance.substring(marker + 7);
    }

    public static boolean glyphVisibleFrom(Cue cue, double cameraDistanceSquared) {
        if (cue == null || cue.channel() != Channel.GLYPH
                || !Double.isFinite(cameraDistanceSquared) || cameraDistanceSquared < 0) return false;
        // A victim receives the target-only HUD cue instead of a billboard filling their camera.
        return cue.spell().orientation() == Orientation.HORIZONTAL || cameraDistanceSquared >= 2.25;
    }

    public static List<Point> glyphQuad(Cue cue) {
        float radius = cue.spell().diameter() / 2;
        if (cue.spell().orientation() == Orientation.HORIZONTAL) {
            return List.of(new Point(-radius, 0, -radius, 0, 0), new Point(radius, 0, -radius, 1, 0),
                    new Point(radius, 0, radius, 1, 1), new Point(-radius, 0, radius, 0, 1));
        }
        return List.of(new Point(-radius, -radius, 0, 0, 1), new Point(radius, -radius, 0, 1, 1),
                new Point(radius, radius, 0, 1, 0), new Point(-radius, radius, 0, 0, 0));
    }

    /** Acquire one layer, write its entire pass, then switch. Immediate buffers cannot be reused after a switch. */
    public static void drawWorldPasses(List<EndEventWorldVfxManager.BeamSnapshot> beams,
                                      String dimension, long now,
                                      Function<WorldPass, Consumer<EndEventWorldVfxManager.BeamSnapshot>> acquire) {
        List<WorldPass> passes = new ArrayList<>();
        passes.add(new WorldPass(PassKind.RIBBON, null));
        passes.add(new WorldPass(PassKind.RITUAL, null));
        for (Spell spell : Spell.values()) passes.add(new WorldPass(PassKind.GLYPH, spell));
        for (WorldPass pass : passes) {
            Consumer<EndEventWorldVfxManager.BeamSnapshot> draw = null;
            for (var beam : beams) {
                if (!liveIn(beam, dimension, now) || !pass.equals(worldPass(beam.instanceId()))) continue;
                if (draw == null) draw = acquire.apply(pass);
                draw.accept(beam);
            }
        }
    }

    private static WorldPass worldPass(String instance) {
        Cue cue = parse(instance);
        if (cue != null) return cue.channel() == Channel.GLYPH ? new WorldPass(PassKind.GLYPH, cue.spell()) : null;
        String key = worldKey(instance);
        // Malformed reserved presentation keys must not become beams or screen effects.
        if (key != null && (key.startsWith("wave6-spell-glyph-") || key.startsWith("wave6-spell-screen-"))) return null;
        if (key != null && (key.startsWith("wave6-ritual-") || key.startsWith("wave6-spell-"))) {
            return new WorldPass(PassKind.RITUAL, null);
        }
        return new WorldPass(PassKind.RIBBON, null);
    }

    public static EndEventWorldVfxManager.BeamSnapshot screenCue(
            List<EndEventWorldVfxManager.BeamSnapshot> beams, String dimension, long now) {
        EndEventWorldVfxManager.BeamSnapshot selected = null;
        for (var beam : beams) {
            Cue cue = parse(beam.instanceId());
            if (cue == null || cue.channel() != Channel.SCREEN || !liveIn(beam, dimension, now)) continue;
            if (selected == null || cue.stage().ordinal() > parse(selected.instanceId()).stage().ordinal()
                    || cue.stage() == parse(selected.instanceId()).stage()
                    && beam.startedAtMillis() > selected.startedAtMillis()) selected = beam;
        }
        return selected;
    }

    private static boolean liveIn(EndEventWorldVfxManager.BeamSnapshot beam, String dimension, long now) {
        return dimension != null && dimension.equals(beam.dimension())
                && now >= beam.startedAtMillis() && now < beam.expiresAtMillis();
    }

    public static int glyphAlpha(Cue cue, long started, long expires, long now) {
        return alpha(cue.stage() == Stage.ACTIVE ? 210 : 155, started, expires, now);
    }

    public static int screenAlpha(Stage stage, long started, long expires, long now) {
        return alpha(stage == Stage.ACTIVE ? 36 : 24, started, expires, now);
    }

    private static int alpha(int maximum, long started, long expires, long now) {
        if (now < started || now >= expires || expires <= started) return 0;
        double fade = Math.min(1, (now - started) / 120.0) * Math.min(1, (expires - now) / 180.0);
        double pulse = .9 + .1 * Math.cos((now - started) * Math.PI / 360);
        return (int) Math.round(maximum * fade * pulse);
    }

    /** A small corner label and narrow side cues keep the central arena cone and existing HUD clear. */
    public static ScreenLayout screenLayout(int width, int height, List<PrisonerHudLayout.Rect> reserved) {
        var clear = new PrisonerHudLayout.Rect(width * 3 / 10, height / 4, width * 7 / 10, height * 3 / 4);
        var hidden = new ScreenLayout(new PrisonerHudLayout.Rect(0, 0, 0, 0), List.of(), clear, false);
        if (width < 220 || height < 144) return hidden;
        int panelWidth = Math.min(182, width - 16);
        var panel = new PrisonerHudLayout.Rect(width - 8 - panelWidth, 6, width - 8, 32);
        if (overlaps(panel, clear, reserved)) {
            panel = new PrisonerHudLayout.Rect(8, 6, 8 + panelWidth, 32);
            if (overlaps(panel, clear, reserved)) return hidden;
        }
        List<PrisonerHudLayout.Rect> edges = new ArrayList<>();
        for (int x : new int[]{0, width - 3}) {
            var edge = new PrisonerHudLayout.Rect(x, 8, x + 3, height * 3 / 4);
            if (!overlaps(edge, clear, reserved)) edges.add(edge);
        }
        return new ScreenLayout(panel, List.copyOf(edges), clear, true);
    }

    private static boolean overlaps(PrisonerHudLayout.Rect area, PrisonerHudLayout.Rect clear,
                                    List<PrisonerHudLayout.Rect> reserved) {
        return area.intersects(clear) || reserved.stream().anyMatch(area::intersects);
    }
}
