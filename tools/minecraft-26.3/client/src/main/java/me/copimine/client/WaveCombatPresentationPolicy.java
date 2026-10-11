package me.copimine.client;

/** Authored wave cues on the existing generation-fenced world VFX channel. */
public final class WaveCombatPresentationPolicy {
    public enum Shape { LANE, FLOOR, BILLBOARD }
    public record Cue(String stage, String ability, int tile, Shape shape) { }
    private WaveCombatPresentationPolicy() { }

    public static Cue parse(String instance) {
        if (instance == null) return null;
        int marker = instance.indexOf(":world:");
        if (marker <= 0 || instance.indexOf(":world:", marker + 7) >= 0) return null;
        String key = instance.substring(marker + 7);
        if (!key.matches("wave-ai-[a-f0-9]{8}-(charge|release|impact|recover|frozen)-(dash|snare|pulse|salvo|web|freeze|reflect)")) return null;
        String[] parts = key.split("-");
        String stage = parts[3], ability = parts[4];
        int tile = switch (ability) {
            case "dash" -> 0;
            case "snare" -> 1;
            case "pulse" -> 2;
            case "salvo" -> 3;
            case "web" -> 4;
            case "freeze" -> 5;
            default -> 7;
        };
        if (stage.equals("recover")) tile = 6;
        Shape shape = stage.equals("recover") || stage.equals("frozen") || stage.equals("release") || ability.equals("reflect")
                ? Shape.BILLBOARD : ability.equals("dash") || ability.equals("salvo")
                ? Shape.LANE : ability.equals("web") ? Shape.BILLBOARD : Shape.FLOOR;
        return new Cue(stage, ability, tile, shape);
    }
    public static int alpha(Cue cue, long started, long expires, long now) {
        if (cue == null || now < started || now >= expires || expires <= started) return 0;
        double fade = Math.min(1, (now-started)/80.0) * Math.min(1,(expires-now)/150.0);
        return (int)Math.round((cue.stage().equals("charge") ? 175 : 225)*fade);
    }
    public static boolean visible(double distanceSquared) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 2.25 && distanceSquared <= 4096;
    }
    public static boolean visible(Cue cue, double distanceSquared) {
        return cue != null && Double.isFinite(distanceSquared) && distanceSquared >= 0
                && distanceSquared <= 4096
                && (cue.shape() != Shape.BILLBOARD || visible(distanceSquared));
    }
    public static double laneLength(double value) { return Double.isFinite(value) ? Math.max(0,Math.min(12,value)) : 0; }
    public static float impactHalfWidth(Cue cue) { return cue != null && cue.ability().equals("dash") ? 1.8F : 0; }
    public static double laneHalfWidth(double width) { return Double.isFinite(width) ? Math.max(.45,Math.min(1.35,width*3)) : .45; }
}
