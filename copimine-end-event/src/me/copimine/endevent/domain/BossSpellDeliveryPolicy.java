package me.copimine.endevent.domain;

/** A real projectile owns its flight and impact; a cast cue cannot impersonate either. */
public final class BossSpellDeliveryPolicy {
    private BossSpellDeliveryPolicy() { }

    public static boolean physicalProjectile(EndRiftAiPolicy.BossSpell spell) {
        return spell == EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE
                || spell == EndRiftAiPolicy.BossSpell.RIFT_ARROWS
                || spell == EndRiftAiPolicy.BossSpell.BOSS_FIREBALL;
    }
}
