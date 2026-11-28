package me.copimine.endevent.domain;

import java.util.List;
import java.util.Locale;

/**
 * The directed RAGE → LAST SEAL arena-merge scene (master OV4.6 "Directed
 * 12–16 second 5→6 scene — not an unbounded boss freeze").
 *
 * <p>The old code opened the final phase with a single ~5 s last-stand intro and
 * an ascension burst: the square-to-round reveal, the falling walls, the two
 * colliding worlds and the overhead sigil were never staged. This policy owns
 * the exact, ordered five-shot timeline the runtime plays: the shot boundaries,
 * the authored world-space geometry signature each shot draws, its banner, its
 * real animation clip and its own audio key.</p>
 *
 * <p>It is pure. It screens nobody, damages nobody and never extends the boss
 * presentation window past {@link #TOTAL_MILLIS}; the runtime reads the beats
 * and draws them. The physical block reveal is committed exactly once, at
 * {@link #revealCommitMillis()}, and only through the approved arena-plan
 * executor — this policy never names a coordinate.</p>
 */
public final class ArenaMergeCinematicPolicy {

    /** The scene is bounded to the master's directed 12–16 s window. */
    public static final long MIN_TOTAL_MILLIS = 12_000L;
    public static final long MAX_TOTAL_MILLIS = 16_000L;
    public static final long TOTAL_MILLIS = 14_000L;

    /** World-space geometry signatures the merge rig understands. */
    public static final String SIGNATURE_MERGE_FRACTURE = "merge-fracture";
    public static final String SIGNATURE_MERGE_COLLAPSE = "merge-collapse";
    public static final String SIGNATURE_MERGE_COLLIDE = "merge-collide";
    public static final String SIGNATURE_MERGE_ASCEND = "merge-ascend";
    public static final String SIGNATURE_MERGE_IGNITE = "merge-ignite";

    /** The five directed shots, in order. */
    public enum Shot {
        /** 0–2 s: the Guardian sheds its outer plates and the surge leaves the chest. */
        BOSS_FRACTURE,
        /** 2–5 s: the square boundary breaks and its slabs tilt outward. */
        WALL_COLLAPSE,
        /** 5–8 s: the round hall appears through the cracks; two worlds collide. */
        TWO_WORLDS,
        /** 8–12 s: the Guardian actually rises above the Core as the cocoon assembles. */
        GUARDIAN_ASCENDS,
        /** 12–14 s: the overhead sigil ignites as the physical reveal finishes. */
        LAST_SEAL_IGNITES
    }

    /**
     * One directed shot.
     *
     * @param shot        which beat this is
     * @param startMillis inclusive start offset from the scene start
     * @param endMillis   exclusive end offset
     * @param signature   the authored geometry rig this shot draws
     * @param title       the Russian banner headline
     * @param subtitle    the Russian banner subtitle
     * @param pose        the real {@link BossAnimationId} clip the client ships
     * @param cueId       {@code base:stage} voiced through {@code BossVisualCuePolicy}
     * @param rgb         accent colour, {@code 0xRRGGBB}
     * @param soundKey    the runtime's authored audio key for the shot's onset
     */
    public record Beat(Shot shot,
                       long startMillis,
                       long endMillis,
                       String signature,
                       String title,
                       String subtitle,
                       String pose,
                       String cueId,
                       int rgb,
                       String soundKey) {
        public Beat {
            if (shot == null) throw new IllegalArgumentException("merge shot is required");
            if (startMillis < 0L || endMillis <= startMillis || endMillis > TOTAL_MILLIS) {
                throw new IllegalArgumentException("merge shot window out of bounds: "
                        + startMillis + ".." + endMillis);
            }
            signature = require(signature, "merge signature");
            title = require(title, "merge title");
            subtitle = require(subtitle, "merge subtitle");
            pose = require(pose, "merge pose");
            cueId = require(cueId, "merge cue");
            soundKey = require(soundKey, "merge sound key");
            if (!BossAnimationId.isKnown(pose)) {
                throw new IllegalArgumentException("unknown merge pose: " + pose);
            }
            int split = cueId.indexOf(':');
            if (split <= 0 || split == cueId.length() - 1) {
                throw new IllegalArgumentException("merge cue must be base:stage: " + cueId);
            }
            try {
                BossVisualCuePolicy.CueStage.valueOf(
                        cueId.substring(split + 1).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalidStage) {
                throw new IllegalArgumentException("merge cue stage is not real: " + cueId);
            }
            if (rgb < 0 || rgb > 0xFFFFFF) {
                throw new IllegalArgumentException("merge rgb out of range: " + rgb);
            }
        }

        public long lengthMillis() {
            return endMillis - startMillis;
        }

        public String cueBase() {
            return cueId.substring(0, cueId.indexOf(':'));
        }

        public BossVisualCuePolicy.CueStage cueStage() {
            return BossVisualCuePolicy.CueStage.valueOf(
                    cueId.substring(cueId.indexOf(':') + 1).toUpperCase(Locale.ROOT));
        }

        /** Progress within this shot, 0 at its onset and approaching 1 at its end. */
        public double progress(long elapsedMillis) {
            long span = endMillis - startMillis;
            if (span <= 0L) return 1.0D;
            double raw = (elapsedMillis - startMillis) / (double) span;
            return raw < 0.0D ? 0.0D : Math.min(1.0D, raw);
        }
    }

    private static final List<Beat> BEATS = List.of(
            new Beat(Shot.BOSS_FRACTURE, 0L, 2_000L,
                    SIGNATURE_MERGE_FRACTURE,
                    "СТРАЖ РАЗЛОМА — ПЕРЕЛОМ",
                    "Броня раскалывается, разлом уходит в ядро",
                    BossAnimationId.LAST_STAND_INTRO.wireId(),
                    "phase_shift:release", 0xB464FF, "guardian_fracture"),
            new Beat(Shot.WALL_COLLAPSE, 2_000L, 5_000L,
                    SIGNATURE_MERGE_COLLAPSE,
                    "СТРАЖ РАЗЛОМА — ОБВАЛ",
                    "Стены квадрата расходятся наружу",
                    BossAnimationId.GROUND_SLAM.wireId(),
                    "arena_inferno:release", 0xFF8A3C, "guardian_wall_collapse"),
            new Beat(Shot.TWO_WORLDS, 5_000L, 8_000L,
                    SIGNATURE_MERGE_COLLIDE,
                    "СТРАЖ РАЗЛОМА — ДВА МИРА",
                    "Круглый зал проступает сквозь трещины",
                    BossAnimationId.CHEST_STRIKE.wireId(),
                    "rift_obelisks:release", 0x7B5CFF, "guardian_two_worlds"),
            new Beat(Shot.GUARDIAN_ASCENDS, 8_000L, 12_000L,
                    SIGNATURE_MERGE_ASCEND,
                    "СТРАЖ РАЗЛОМА — ВОЗНЕСЕНИЕ",
                    "Он поднимается над ядром, кокон собирается",
                    BossAnimationId.CAST_CHARGE.wireId(),
                    "summon_servants:release", 0x54E0FF, "guardian_ascend"),
            new Beat(Shot.LAST_SEAL_IGNITES, 12_000L, TOTAL_MILLIS,
                    SIGNATURE_MERGE_IGNITE,
                    "СТРАЖ РАЗЛОМА — ПОСЛЕДНЯЯ ПЕЧАТЬ",
                    "Печать вспыхивает: новой арены больше нет пути назад",
                    BossAnimationId.LAST_STAND_INTRO.wireId(),
                    "final_strike:release", 0xF6F2FF, "guardian_last_seal_ignite"));

    private ArenaMergeCinematicPolicy() {
    }

    public static List<Beat> beats() {
        return BEATS;
    }

    public static long totalMillis() {
        return TOTAL_MILLIS;
    }

    /** The beat that owns {@code elapsedMillis}; clamps to the first/last shot. */
    public static Beat beatAt(long elapsedMillis) {
        for (Beat beat : BEATS) {
            if (elapsedMillis < beat.endMillis()) {
                return beat;
            }
        }
        return BEATS.get(BEATS.size() - 1);
    }

    public static Shot shotAt(long elapsedMillis) {
        return beatAt(elapsedMillis).shot();
    }

    /** True once the whole directed scene has played. */
    public static boolean completed(long elapsedMillis) {
        return elapsedMillis >= TOTAL_MILLIS;
    }

    /** Overall progress of the scene, clamped to {@code [0, 1]}. */
    public static double progress(long elapsedMillis) {
        if (elapsedMillis <= 0L) return 0.0D;
        return Math.min(1.0D, elapsedMillis / (double) TOTAL_MILLIS);
    }

    /** The single, ordered physical-reveal commit point: the wall collapse onset. */
    public static long revealCommitMillis() {
        return BEATS.get(1).startMillis();
    }

    /** The point the physical Guardian actually leaves the ground for the cocoon. */
    public static long ascensionMillis() {
        return BEATS.get(3).startMillis();
    }

    /** The point the new play arena becomes active (sigil ignite). */
    public static long sealIgniteMillis() {
        return BEATS.get(4).startMillis();
    }

    /** The physical transform never commits before the wall-collapse shot begins. */
    public static boolean mayCommitReveal(long elapsedMillis) {
        return elapsedMillis >= revealCommitMillis() && elapsedMillis < TOTAL_MILLIS;
    }

    /** The scene never grants immunity; the body is only held by the caller's window. */
    public static boolean grantsInvulnerability() {
        return false;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    /**
     * Static self-check: the beats are contiguous, ordered, in bounds and
     * visually distinct. Called once at class load so a bad edit fails closed at
     * startup rather than mid-fight.
     */
    static {
        long cursor = 0L;
        java.util.Set<String> signatures = new java.util.HashSet<>();
        java.util.Set<String> titles = new java.util.HashSet<>();
        java.util.Set<Integer> colours = new java.util.HashSet<>();
        java.util.Set<String> sounds = new java.util.HashSet<>();
        if (BEATS.size() != Shot.values().length) {
            throw new IllegalStateException("arena merge must stage exactly one beat per shot");
        }
        for (int index = 0; index < BEATS.size(); index++) {
            Beat beat = BEATS.get(index);
            if (beat.shot() != Shot.values()[index]) {
                throw new IllegalStateException("arena merge beats are out of shot order at " + index);
            }
            if (beat.startMillis() != cursor) {
                throw new IllegalStateException("arena merge beats are not contiguous at " + beat.shot());
            }
            cursor = beat.endMillis();
            if (!signatures.add(beat.signature())) {
                throw new IllegalStateException("duplicate merge signature " + beat.signature());
            }
            if (!titles.add(beat.title())) {
                throw new IllegalStateException("duplicate merge title " + beat.title());
            }
            if (!colours.add(beat.rgb())) {
                throw new IllegalStateException("duplicate merge colour for " + beat.shot());
            }
            if (!sounds.add(beat.soundKey())) {
                throw new IllegalStateException("duplicate merge sound " + beat.soundKey());
            }
        }
        if (cursor != TOTAL_MILLIS || TOTAL_MILLIS < MIN_TOTAL_MILLIS || TOTAL_MILLIS > MAX_TOTAL_MILLIS) {
            throw new IllegalStateException("arena merge timeline does not close at the directed window");
        }
    }
}
