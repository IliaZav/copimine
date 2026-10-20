package me.copimine.client;

/** Per-instance event music gain; user options and unrelated sounds are never written. */
public final class WaveMusicMixPolicy {
    private static final float FOG_GAIN = .30F;
    private WaveMusicMixPolicy() { }

    public static float advance(float current, boolean dangerousFog) {
        float value = Float.isFinite(current) ? Math.max(FOG_GAIN, Math.min(1, current)) : 1;
        float target = dangerousFog ? FOG_GAIN : 1;
        float step = dangerousFog ? (1 - FOG_GAIN) / 16 : (1 - FOG_GAIN) / 24;
        return value > target ? Math.max(target, value - step) : Math.min(target, value + step);
    }

    public static float volume(String namespace, String path, boolean music, float base, float factor) {
        if (!music || !"copimine".equals(namespace) || !"end_rift/wave_5".equals(path)) return base;
        float gain = Float.isFinite(factor) ? Math.max(FOG_GAIN, Math.min(1, factor)) : 1;
        return base * gain;
    }
}
