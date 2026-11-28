package me.copimine.endevent.domain;

/** A real projectile owns its flight and impact; a cast cue cannot impersonate either. */
public final class BossSpellDeliveryPolicy {
    private BossSpellDeliveryPolicy() { }

    /** The target can move during the charge, then has seven ticks to dodge the locked shot. */
    public static int projectileAimLockTick(int windupTicks) {
        return Math.max(1, windupTicks - 7);
    }

    public static boolean physicalProjectile(EndRiftAiPolicy.BossSpell spell) {
        return spell == EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE
                || spell == EndRiftAiPolicy.BossSpell.RIFT_ARROWS
                || spell == EndRiftAiPolicy.BossSpell.BOSS_FIREBALL;
    }
}
