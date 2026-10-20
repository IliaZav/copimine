package me.copimine.client;

import net.minecraft.client.model.EntityModel;

/** Owns the client-only named Wave 7 actor models. */
public final class Wave7NamedTrialModelRenderer {
    private final Wave7WardenModel warden = new Wave7WardenModel(
            Wave7WardenModel.createLayer().bakeRoot());
    private final Wave7ArchmageModel archmage = new Wave7ArchmageModel(
            Wave7ArchmageModel.createLayer().bakeRoot());

    public EntityModel<?> modelFor(Wave7NamedTrialVisualPolicy.ActorModel actor) {
        if (actor == null) return null;
        return switch (actor) {
            case WARDEN -> warden;
            case ARCHMAGE -> archmage;
            case MARKSMAN -> null;
        };
    }

    public boolean supportsState(String visualId, Class<?> renderStateType) {
        return Wave7NamedTrialVisualPolicy.matchesRenderState(visualId, renderStateType);
    }

}
