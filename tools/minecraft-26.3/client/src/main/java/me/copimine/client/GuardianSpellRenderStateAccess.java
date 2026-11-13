package me.copimine.client;

/** Immutable per-submission presentation, never a client-created combat projectile. */
public interface GuardianSpellRenderStateAccess {
    GuardianSpellRenderer.Pose copimine$spellPose();
    void copimine$spellPose(GuardianSpellRenderer.Pose pose);
}
