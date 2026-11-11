package me.copimine.client;

/** Finite luminous refuge cues: shared timing contract with BlackFogTimingPolicy. */
public final class BlackFogGuidePolicy {
    public static final int MAX_GUIDES = 8;
    public static final float MAX_DISTANCE = 128.0F;
    private static final java.util.regex.Pattern KEY = java.util.regex.Pattern.compile("wave5-safe-zone-(?:[0-9]|1[0-9])");
    private BlackFogGuidePolicy() { }
    public static boolean isGuide(String instanceId) {
        if (instanceId == null) return false;
        int marker = instanceId.lastIndexOf(":world:");
        String key = marker < 0 ? instanceId : instanceId.substring(marker + 7);
        return KEY.matcher(key).matches();
    }
    public static float strength(long ageMillis) {
        if (ageMillis < 0L || ageMillis >= 5_000L) return 0.0F;
        if (ageMillis <= 3_500L) return 1.0F;
        float t = (ageMillis - 3_500L) / 1_500.0F;
        return 1.0F - t * t * (3.0F - 2.0F * t);
    }
}
