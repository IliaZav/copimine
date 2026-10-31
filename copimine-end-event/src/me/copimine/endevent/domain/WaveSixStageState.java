package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequential Wave 6 stage machine.
 *
 * <p>The specification's 14.2 lifecycle, expressed as pure data: exactly one mage
 * stage may be hostile at a time, and no later stage may become hostile until the
 * previous one is fully retired. The states are deliberately explicit rather than
 * booleans, because the audit's failure mode was an endless wave caused by an
 * ambiguous "is it done?" flag.</p>
 */
public final class WaveSixStageState {
    /** Encoded schema marker. A saved run with any other marker is rejected. */
    public static final String SCHEMA = "2";

    public static final int MAX_FIELD_LENGTH = 256;

    private WaveSixStageState() {
    }

    /**
     * The ordered stage lifecycle.
     *
     * <p>{@link #TRANSFER} is the moment between a mage's defeat and the next
     * stage's first hostile action. It exists so a spell released by the dying
     * mage can never be attributed to the next one.</p>
     */
    public enum Stage {
        PREPARE,
        GUARDED,
        EXPOSED,
        TRANSFER,
        DRAIN,
        NEXT_STAGE,
        FINAL_RELEASE,
        FINAL_CLEAR,
        COMPLETE
    }

    /**
     * Legal forward transitions. Cycles are returned as a fresh list because the
     * chain is a declared contract, not a graph search.
     */
    public static List<Stage> nextStages(Stage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("stage is required");
        }
        return switch (stage) {
            case PREPARE -> List.of(Stage.GUARDED);
            case GUARDED -> List.of(Stage.EXPOSED);
            case EXPOSED -> List.of(Stage.TRANSFER);
            case TRANSFER -> List.of(Stage.DRAIN);
            case DRAIN -> List.of(Stage.NEXT_STAGE, Stage.FINAL_RELEASE);
            case NEXT_STAGE -> List.of(Stage.GUARDED);
            case FINAL_RELEASE -> List.of(Stage.FINAL_CLEAR);
            case FINAL_CLEAR -> List.of(Stage.COMPLETE);
            case COMPLETE -> List.of();
        };
    }

    public static boolean canAdvance(Stage from, Stage to) {
        return from != null && to != null && nextStages(from).contains(to);
    }

    /** Terminal state; a completed wave never re-enters a hostile stage. */
    public static boolean isComplete(Stage stage) {
        return stage == Stage.COMPLETE;
    }

    /** True while the current mage is a live hostile combatant. */
    public static boolean isHostile(Stage stage) {
        return stage == Stage.GUARDED || stage == Stage.EXPOSED;
    }

    /**
     * A stage that must block the next wave from starting.
     *
     * <p>{@code FINAL_CLEAR} is included on purpose: ordinary cleanup residue is
     * not progress, so a wave that has not finished cleaning up cannot hand over.</p>
     */
    public static boolean blocksTransition(Stage stage) {
        return stage != Stage.COMPLETE;
    }

    /**
     * Persisted stage state.
     *
     * <p>{@code stageIndex} is the count of already-defeated identities and
     * therefore also the index of the currently required mage. It is bounded by
     * the identity count so a truncated save cannot invent a sixth stage.</p>
     */
    public record Snapshot(Stage stage,
                           int stageIndex,
                           List<MageSignaturePolicy.MageId> order,
                           List<MageSignaturePolicy.MageId> defeated,
                           boolean prisonerCaptured,
                           boolean prisonBroken) {
        public Snapshot {
            if (stage == null) {
                throw new IllegalArgumentException("stage is required");
            }
            MageSignaturePolicy.requireValidOrder(order);
            if (defeated == null) {
                throw new IllegalArgumentException("defeated identity list is required");
            }
            if (!order.containsAll(defeated)) {
                throw new IllegalArgumentException("defeated identities must belong to the run order");
            }
            if (stageIndex != defeated.size()) {
                throw new IllegalArgumentException(
                        "stage index must equal the defeated count: " + stageIndex);
            }
            if (stageIndex < 0 || stageIndex > MageSignaturePolicy.MAGE_COUNT) {
                throw new IllegalArgumentException("stage index outside 0-5: " + stageIndex);
            }
            if (defeated.size() == MageSignaturePolicy.MAGE_COUNT && !prisonBroken) {
                throw new IllegalArgumentException(
                        "the fifth defeat must break the prison, not wait for it");
            }
            order = List.copyOf(order);
            defeated = List.copyOf(defeated);
        }

        /** The mage that must be defeated now, or {@code null} on completion. */
        public MageSignaturePolicy.MageId currentMage() {
            if (stageIndex >= MageSignaturePolicy.MAGE_COUNT) {
                return null;
            }
            return order.get(stageIndex);
        }

        /** Only one identity may ever be the current stage. */
        public boolean hasSingleActiveStage() {
            return stageIndex <= MageSignaturePolicy.MAGE_COUNT;
        }

        public Snapshot withStage(Stage next) {
            if (!canAdvance(stage, next)) {
                throw new IllegalArgumentException("illegal Wave 6 stage change " + stage + " -> " + next);
            }
            return new Snapshot(next, stageIndex, order, defeated, prisonerCaptured, prisonBroken);
        }

        /** Initial state for a freshly prepared wave. */
        public static Snapshot fresh(List<MageSignaturePolicy.MageId> order) {
            return new Snapshot(Stage.PREPARE, 0, order, List.of(), false, false);
        }

        public String encode() {
            return String.join(";",
                    "schema=" + SCHEMA,
                    "stage=" + stage.name(),
                    "index=" + stageIndex,
                    "order=" + join(order),
                    "defeated=" + join(defeated),
                    "captured=" + (prisonerCaptured ? "1" : "0"),
                    "broken=" + (prisonBroken ? "1" : "0"));
        }

        public static Snapshot decode(String encoded, String expectedEventId, long expectedGeneration) {
            if (encoded == null || encoded.isBlank() || encoded.length() > 4 * MAX_FIELD_LENGTH) {
                throw new IllegalArgumentException("stage snapshot is missing or oversized");
            }
            String schema = null;
            String stageText = null;
            String indexText = null;
            String orderText = null;
            String defeatedText = null;
            String capturedText = null;
            String brokenText = null;
            for (String field : encoded.split(";", -1)) {
                int split = field.indexOf('=');
                if (split <= 0) {
                    throw new IllegalArgumentException("malformed stage snapshot field: " + field);
                }
                String key = field.substring(0, split);
                String value = field.substring(split + 1);
                switch (key) {
                    case "schema" -> schema = value;
                    case "stage" -> stageText = value;
                    case "index" -> indexText = value;
                    case "order" -> orderText = value;
                    case "defeated" -> defeatedText = value;
                    case "captured" -> capturedText = value;
                    case "broken" -> brokenText = value;
                    default -> throw new IllegalArgumentException("unknown stage snapshot key: " + key);
                }
            }
            if (!SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("unsupported Wave 6 stage schema: " + schema);
            }
            if (expectedEventId != null && expectedGeneration >= 0L
                    && (orderText == null || defeatedText == null)) {
                throw new IllegalArgumentException("stage snapshot lost its identity lists");
            }
            Stage stage = parseStage(stageText);
            int index = Integer.parseInt(requireText(indexText, "index"));
            List<MageSignaturePolicy.MageId> order = parseIdentities(orderText, true);
            List<MageSignaturePolicy.MageId> defeated = parseIdentities(defeatedText, false);
            boolean captured = "1".equals(requireText(capturedText, "captured"));
            boolean broken = "1".equals(requireText(brokenText, "broken"));
            return new Snapshot(stage, index, order, defeated, captured, broken);
        }

        private static String join(List<MageSignaturePolicy.MageId> values) {
            List<String> names = new ArrayList<>(values.size());
            for (MageSignaturePolicy.MageId value : values) {
                names.add(value.name());
            }
            return String.join(",", names);
        }

        private static List<MageSignaturePolicy.MageId> parseIdentities(String raw, boolean complete) {
            String text = requireText(raw, "identities");
            if (text.isEmpty()) {
                if (complete) {
                    throw new IllegalArgumentException("run order must not be empty");
                }
                return List.of();
            }
            List<MageSignaturePolicy.MageId> parsed = new ArrayList<>();
            for (String token : text.split(",", -1)) {
                parsed.add(MageSignaturePolicy.parseMage(token));
            }
            if (complete) {
                MageSignaturePolicy.requireValidOrder(parsed);
            }
            return List.copyOf(parsed);
        }

        private static Stage parseStage(String raw) {
            String text = requireText(raw, "stage");
            try {
                return Stage.valueOf(text);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("unknown Wave 6 stage: " + text, invalid);
            }
        }

        private static String requireText(String value, String label) {
            if (value == null || value.length() > MAX_FIELD_LENGTH) {
                throw new IllegalArgumentException("stage snapshot field is missing: " + label);
            }
            return value;
        }
    }
}