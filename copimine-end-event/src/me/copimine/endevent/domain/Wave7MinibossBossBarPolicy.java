package me.copimine.endevent.domain;

/**
 * Identity of a Wave 7 miniboss boss bar.
 *
 * <p>Wave 7 is four named trials, and until now the party had no readable
 * status for the thing it was actually fighting: the Warden, the Marksman
 * pair and the Archmage all died silently inside the chamber while the HUD
 * showed nothing at all. A player could not tell whether the trial was
 * progressing, whether the miniboss was at half health, or whether the thing
 * in front of them was even the objective.</p>
 *
 * <p>Every miniboss therefore gets its own bar, and the bar carries the
 * miniboss's own identity instead of a shared template: a bone bar with
 * End-corrupted marrow for the skeleton marksmen, a sculk bar for the Warden,
 * a rune bar for the Archmage and a mirrored bar for the personal Echo. The
 * server owns the title, progress, colour and lifetime; the optional client
 * HUD only repaints the same numbers with authored art, so a player without
 * the client mod still sees a correct vanilla bar.</p>
 *
 * <p>Pure: no Bukkit, no domain state, no clocks. The plugin maps the colour
 * and style names onto Bukkit enums, and the client matches the same titles
 * with its own copy of the literals, so this class is the single source of
 * truth for the identity strings.</p>
 */
public final class Wave7MinibossBossBarPolicy {
    private Wave7MinibossBossBarPolicy() {
    }

    /** One authored miniboss bar identity. */
    public enum Kind {
        /**
         * Skeleton marksman pair. Bone, but the marrow is End-corrupted, so the
         * bar is bone-white split by violet fractures rather than a clean skull.
         */
        MARKSMAN("Костяной Стрелок Разлома", "bone", "marksman",
                0xFFE9E2CE, 0xFF9A62FF, "PURPLE", "SEGMENTED_10"),
        /** Warden: sculk-teal body with the sonic core glowing through the ribs. */
        WARDEN("Хранитель Бездны", "sculk", "warden",
                0xFF37E0D0, 0xFF0C6B7A, "BLUE", "SEGMENTED_10"),
        /** Rift Archmage: hooded caster whose runes burn pink. */
        ARCHMAGE("Архимаг Разлома", "rune", "archmage",
                0xFFF34CDB, 0xFF7A2BD6, "PINK", "SEGMENTED_20"),
        /** Personal Echo: a mirrored copy, so the bar is silver cut by cyan. */
        ECHO("Эхо Разлома", "mirror", "echo",
                0xFFBFE9FF, 0xFF42C9FF, "WHITE", "SEGMENTED_10");

        private final String title;
        private final String frame;
        private final String portrait;
        private final int accentRgb;
        private final int glowRgb;
        private final String barColorName;
        private final String barStyleName;

        Kind(String title, String frame, String portrait, int accentRgb, int glowRgb,
             String barColorName, String barStyleName) {
            this.title = title;
            this.frame = frame;
            this.portrait = portrait;
            this.accentRgb = accentRgb;
            this.glowRgb = glowRgb;
            this.barColorName = barColorName;
            this.barStyleName = barStyleName;
        }

        /** Name shown on the vanilla fallback bar and matched by the client HUD. */
        public String title() {
            return title;
        }

        /** Frame atlas stem, resolved to {@code wave7_bossbar_<frame>.png}. */
        public String frameStem() {
            return frame;
        }

        /** Portrait stem, resolved to {@code wave7_portrait_<portrait>.png}. */
        public String portraitStem() {
            return portrait;
        }

        /** Fill colour used by the authored HUD. */
        public int accentRgb() {
            return accentRgb;
        }

        /** Deep colour behind the fill, so the bar reads at a distance. */
        public int glowRgb() {
            return glowRgb;
        }

        /** Bukkit {@code BarColor} constant name. */
        public String barColorName() {
            return barColorName;
        }

        /** Bukkit {@code BarStyle} constant name. */
        public String barStyleName() {
            return barStyleName;
        }
    }

    /**
     * Which bar a trial role owns.
     *
     * <p>The legacy {@code Trial} enum and the newer {@code NamedTrial} enum use
     * different spellings for the same fights, so both are accepted and
     * anything else returns {@code null} instead of guessing a bar: a trial
     * without an authored identity must not borrow someone else's.</p>
     */
    public static Kind kindForTrialRole(String role) {
        if (role == null) {
            return null;
        }
        return switch (role.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "WARDEN" -> Kind.WARDEN;
            case "MOBILE_MARKSMAN", "MARKSMAN", "SKELETON", "SKELETON_PAIR" -> Kind.MARKSMAN;
            case "RIFT_ARCHMAGE", "ARCHMAGE" -> Kind.ARCHMAGE;
            case "PERSONAL_ECHO", "ECHO" -> Kind.ECHO;
            default -> null;
        };
    }

    /** True when the role is a Wave 7 miniboss that owns a bar. */
    public static boolean ownsBar(String role) {
        return kindForTrialRole(role) != null;
    }

    /** Title for a kind, or an empty string when the kind is unknown. */
    public static String title(Kind kind) {
        return kind == null ? "" : kind.title();
    }

    /**
     * Resolve a displayed bar title back to its kind.
     *
     * <p>Prefix matching is deliberate: the server may append a live counter to
     * the title (for example a joint-volley charge) without breaking the
     * client's ability to pick the right art.</p>
     */
    public static Kind kindForTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String trimmed = title.trim();
        for (Kind kind : Kind.values()) {
            if (trimmed.startsWith(kind.title())) {
                return kind;
            }
        }
        return null;
    }

    /**
     * Bar progress from real health.
     *
     * <p>Clamped, and a non-finite or non-positive maximum returns zero rather
     * than NaN: a bar that renders NaN progress is the one state the client
     * cannot recover from.</p>
     */
    public static float progress(double health, double maximumHealth) {
        if (!Double.isFinite(health) || !Double.isFinite(maximumHealth) || maximumHealth <= 0.0D) {
            return 0.0F;
        }
        double ratio = health / maximumHealth;
        if (ratio <= 0.0D) {
            return 0.0F;
        }
        return ratio >= 1.0D ? 1.0F : (float) ratio;
    }

    /**
     * Below this fraction the authored HUD pulses the fill, which is the cue
     * that survives a player watching the miniboss instead of the bar.
     */
    public static final float CRITICAL_FRACTION = 0.25F;

    /** True when the bar should pulse at the given fraction. */
    public static boolean critical(float progress) {
        return progress > 0.0F && progress <= CRITICAL_FRACTION;
    }

    /**
     * Which bar owns the screen when two minibosses are alive at once.
     *
     * <p>Trials overlap in a 11+ player party, and two full-size authored bars
     * would cover the crosshair. The Warden is the fight that can kill the
     * party, then the Archmage, then the marksmen, then an Echo; equal kinds
     * fall back to the older spawn so a pair of skeletons keeps one bar.</p>
     */
    public static int priority(Kind kind) {
        if (kind == null) {
            return -1;
        }
        return switch (kind) {
            case WARDEN -> 3;
            case ARCHMAGE -> 2;
            case MARKSMAN -> 1;
            case ECHO -> 0;
        };
    }

    /** Highest priority among the given kinds, ignoring unknown ones. */
    public static Kind dominant(Iterable<Kind> alive) {
        Kind best = null;
        for (Kind kind : alive) {
            if (kind == null) {
                continue;
            }
            if (best == null || priority(kind) > priority(best)) {
                best = kind;
            }
        }
        return best;
    }
}