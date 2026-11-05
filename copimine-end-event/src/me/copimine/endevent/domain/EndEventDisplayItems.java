package me.copimine.endevent.domain;

/** Display geometry must be resolvable before an ItemDisplay is published. */
public final class EndEventDisplayItems {
    /** The item definition every event overlay keys off (custom_model_data ranges). */
    public static final int FIRST_MODEL_DATA = 830001;
    public static final int LAST_MODEL_DATA = 830099;

    private EndEventDisplayItems() {
    }

    /**
     * An ItemDisplay whose custom_model_data has no authored model renders as the
     * missing-model placeholder. That placeholder is exactly what players report as
     * "the portal / core / sphere is not there", so the runtime must not publish it:
     * a layer that cannot resolve is a defect to log, never a picture to freeze.
     */
    public static boolean hasResolvableModel(int customModelData) {
        return customModelData >= FIRST_MODEL_DATA && customModelData <= LAST_MODEL_DATA;
    }
}
