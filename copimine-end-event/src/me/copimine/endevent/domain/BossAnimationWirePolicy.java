package me.copimine.endevent.domain;

import java.util.Locale;

/** Converts the plugin's legacy phase cue to the strict signed animation token. */
public final class BossAnimationWirePolicy {
    private BossAnimationWirePolicy() { }

    public static String canonicalPhaseCue(String cue) {
        if (cue == null || cue.isBlank() || cue.length() > 256) return null;
        String[] fields = cue.split("\\|", -1);
        if (fields.length < 1 || fields.length > 2) return null;
        if (fields.length == 2) {
            try {
                BossPhase.valueOf(fields[0].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalidPhase) {
                return null;
            }
        }
        String raw = fields[fields.length - 1];
        if (raw.isBlank()) return null;
        BossAnimationId animation = BossAnimationId.fromWire(raw);
        return animation == BossAnimationId.UNKNOWN || animation == BossAnimationId.NONE
                ? null : animation.wireId();
    }
}
