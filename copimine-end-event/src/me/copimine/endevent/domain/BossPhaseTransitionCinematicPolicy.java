package me.copimine.endevent.domain;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * N23-P1-11: one authored transition cinematic per Guardian phase.
 *
 * <p>The Guardian opened every health phase with the same ~2.2 s interruption,
 * the same banner, the same cue and the same impact; only LAST_SEAL had a
 * dedicated intro. Six different rules of the fight therefore read as one
 * repeated event. Each phase now names its own short cinematic: a stable wire
 * id, a Russian banner, the authored pose clip the client actually ships, its
 * own cue, its own world-space geometry signature, its own accent colour and a
 * bounded presentation window.</p>
 *
 * <p>This policy is pure and grants no gameplay power. It never makes the
 * Guardian invulnerable, never holds the body longer than
 * {@link #MAX_DURATION_MILLIS}, and never touches player control or movement.
 * The runtime keeps its existing cast interruption; the only displacement a
 * phase change ever performs is the already-approved, telegraphed half-health
 * repel, which this policy does not alter.</p>
 */
public final class BossPhaseTransitionCinematicPolicy {

    /** Short enough that the fight never idles behind the tell. */
    public static final long MIN_DURATION_MILLIS = 1_600L;
    public static final long MAX_DURATION_MILLIS = 2_600L;

    /** Geometry signatures an authored transition rig understands. */
    public static final String SIGNATURE_ASCENSION = "ascension";
    public static final String SIGNATURE_STALKER = "stalker";
    public static final String SIGNATURE_FRACTURE = "fracture";
    public static final String SIGNATURE_EMBER = "ember";
    public static final String SIGNATURE_ROAR = "roar";
    public static final String SIGNATURE_LAST_STAND = "last-stand";

    private static final Map<BossPhase, Cinematic> CINEMATICS = build();

    private BossPhaseTransitionCinematicPolicy() {
    }

    public record Cinematic(String id,
                            String title,
                            String subtitle,
                            long durationMillis,
                            String poseAnimation,
                            String cueId,
                            String signature,
                            int rgb) {
        public Cinematic {
            id = require(id, "transition id");
            title = require(title, "transition title");
            subtitle = require(subtitle, "transition subtitle");
            poseAnimation = require(poseAnimation, "transition pose");
            cueId = require(cueId, "transition cue");
            signature = require(signature, "transition signature");
            if (durationMillis < MIN_DURATION_MILLIS || durationMillis > MAX_DURATION_MILLIS) {
                throw new IllegalArgumentException(
                        "transition duration out of bounds: " + durationMillis);
            }
            if (!BossAnimationId.isKnown(poseAnimation)) {
                throw new IllegalArgumentException("unknown transition pose: " + poseAnimation);
            }
            int split = cueId.indexOf(':');
            if (split <= 0 || split == cueId.length() - 1) {
                throw new IllegalArgumentException("transition cue must be base:stage: " + cueId);
            }
            try {
                BossVisualCuePolicy.CueStage.valueOf(
                        cueId.substring(split + 1).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalidStage) {
                throw new IllegalArgumentException("transition cue stage is not real: " + cueId);
            }
            if (rgb < 0 || rgb > 0xFFFFFF) {
                throw new IllegalArgumentException("transition rgb out of range: " + rgb);
            }
        }

        /** The authored cue base id, e.g. {@code rift_obelisks}. */
        public String cueBase() {
            return cueId.substring(0, cueId.indexOf(':'));
        }

        /** The authored cue stage, always a real {@link BossVisualCuePolicy.CueStage}. */
        public BossVisualCuePolicy.CueStage cueStage() {
            return BossVisualCuePolicy.CueStage.valueOf(
                    cueId.substring(cueId.indexOf(':') + 1).toUpperCase(Locale.ROOT));
        }
    }

    /**
     * The cinematic for a phase. An unknown/absent phase is a diagnostics
     * condition, never a licence to invent one, so it reuses AWAKENING's.
     */
    public static Cinematic forPhase(BossPhase phase) {
        Cinematic cinematic = phase == null ? null : CINEMATICS.get(phase);
        return cinematic != null ? cinematic : CINEMATICS.get(BossPhase.AWAKENING);
    }

    /** A transition is a moment of threat, never a shield. Always false. */
    public static boolean grantsInvulnerability(BossPhase phase) {
        return false;
    }

    public static Map<BossPhase, Cinematic> cinematics() {
        return CINEMATICS;
    }

    private static Map<BossPhase, Cinematic> build() {
        EnumMap<BossPhase, Cinematic> table = new EnumMap<>(BossPhase.class);
        // Awakening: the seal cracks open and the arena answers. The slowest
        // tell of the six because it is the first thing anyone ever sees.
        table.put(BossPhase.AWAKENING, new Cinematic(
                "phase_awakening",
                "СТРАЖ РАЗЛОМА — ПРОБУЖДЕНИЕ",
                "Разлом открывает глаза",
                2_000L,
                BossAnimationId.CAST_CHARGE.wireId(),
                "phase_shift:release",
                SIGNATURE_ASCENSION,
                0x9A62FF));
        // Hunt: the guardian stops performing and starts chasing. Short and
        // sharp; the body already knows how to run.
        table.put(BossPhase.HUNT, new Cinematic(
                "phase_hunt",
                "СТРАЖ РАЗЛОМА — ОХОТА",
                "Он почуял добычу и ускоряется",
                1_600L,
                BossAnimationId.CAST_RELEASE.wireId(),
                "rift_projectile:release",
                SIGNATURE_STALKER,
                0x42C9FF));
        // Rift: the floor splits. Tied to the real fracture snapshot the phase
        // already drives; the cinematic only frames it.
        table.put(BossPhase.RIFT, new Cinematic(
                "phase_rift",
                "СТРАЖ РАЗЛОМА — РАЗЛОМ",
                "Трещины расходятся по арене",
                2_200L,
                BossAnimationId.GROUND_SLAM.wireId(),
                "rift_obelisks:release",
                SIGNATURE_FRACTURE,
                0xF34CDB));
        // Overload: every vent lights at once. Longest generic tell, so the
        // heat warning is readable before the floor bites.
        table.put(BossPhase.OVERLOAD, new Cinematic(
                "phase_overload",
                "СТРАЖ РАЗЛОМА — ПЕРЕГРУЗКА",
                "Пол под ногами раскаляется",
                2_400L,
                BossAnimationId.CHEST_STRIKE.wireId(),
                "arena_inferno:release",
                SIGNATURE_EMBER,
                0xFFC857));
        // Rage: the chest opens for a roar and the knock-back answers. Short,
        // because rage is pressure, not a pause.
        table.put(BossPhase.RAGE, new Cinematic(
                "phase_rage",
                "СТРАЖ РАЗЛОМА — ЯРОСТЬ",
                "Страх больше не сдерживает его",
                1_800L,
                BossAnimationId.MELEE_SWIPE.wireId(),
                "void_blast:release",
                SIGNATURE_ROAR,
                0xFF4F61));
        // Last seal: the dedicated last-stand clip owns this one; the policy
        // names it so the generic block no longer has to special-case it.
        table.put(BossPhase.LAST_SEAL, new Cinematic(
                "phase_last_seal",
                "СТРАЖ РАЗЛОМА — ПОСЛЕДНЯЯ ПЕЧАТЬ",
                "Последняя печать: назад дороги нет",
                2_600L,
                BossAnimationId.LAST_STAND_INTRO.wireId(),
                "final_strike:release",
                SIGNATURE_LAST_STAND,
                0xF4F4FF));
        requireDistinct(table);
        return Map.copyOf(table);
    }

    private static void requireDistinct(EnumMap<BossPhase, Cinematic> table) {
        Set<String> ids = new HashSet<>();
        Set<String> titles = new HashSet<>();
        Set<String> subtitles = new HashSet<>();
        Set<String> poses = new HashSet<>();
        Set<String> cues = new HashSet<>();
        Set<String> signatures = new HashSet<>();
        Set<Integer> colours = new HashSet<>();
        for (BossPhase phase : BossPhase.values()) {
            Cinematic cinematic = table.get(phase);
            if (cinematic == null) {
                throw new IllegalStateException("missing transition cinematic for " + phase);
            }
            requireUnique(ids, cinematic.id(), "id", phase);
            requireUnique(titles, cinematic.title(), "title", phase);
            requireUnique(subtitles, cinematic.subtitle(), "subtitle", phase);
            requireUnique(poses, cinematic.poseAnimation(), "pose", phase);
            requireUnique(cues, cinematic.cueId(), "cue", phase);
            requireUnique(signatures, cinematic.signature(), "signature", phase);
            requireUnique(colours, cinematic.rgb(), "rgb", phase);
        }
    }

    private static void requireUnique(Set<String> seen, String value, String field, BossPhase phase) {
        if (!seen.add(value)) {
            throw new IllegalStateException(
                    "duplicate phase-transition " + field + " for " + phase + ": " + value);
        }
    }

    private static void requireUnique(Set<Integer> seen, int value, String field, BossPhase phase) {
        if (!seen.add(value)) {
            throw new IllegalStateException(
                    "duplicate phase-transition " + field + " for " + phase + ": " + value);
        }
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("transition " + field + " is required");
        }
        return value.trim();
    }
}
