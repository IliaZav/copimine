package me.copimine.endevent.domain;

import java.util.Locale;

/** Canonical ability vocabulary shared by the server brain and diagnostics. */
public enum BossAbilityId {
    VOID_BLAST("void_blast", BossHazardBudget.MechanicKind.VOID_BLAST, false, false),
    RIFT_PROJECTILE("rift_projectile", BossHazardBudget.MechanicKind.RIFT_PROJECTILE, false, true),
    VOID_MARK("void_mark", BossHazardBudget.MechanicKind.VOID_MARK, true, true),
    RIFT_ARROWS("rift_arrows", BossHazardBudget.MechanicKind.RIFT_ARROWS, false, true),
    SUMMON_SERVANTS("summon_servants", BossHazardBudget.MechanicKind.SUMMON_SERVANTS, false, false),
    ARENA_INFERNO("arena_inferno", BossHazardBudget.MechanicKind.ARENA_INFERNO, false, false),
    BOSS_FIREBALL("boss_fireball", BossHazardBudget.MechanicKind.BOSS_FIREBALL, false, false),
    MELEE_SWIPE("melee_swipe", BossHazardBudget.MechanicKind.MELEE_SWIPE, false, false),
    CHEST_STRIKE("chest_strike", BossHazardBudget.MechanicKind.CHEST_STRIKE, false, false),
    GROUND_SLAM("ground_slam", BossHazardBudget.MechanicKind.GROUND_SLAM, false, false),
    // The boss hovers above the Core during LAST_SEAL, so a direct ray to a
    // grounded player is frequently blocked by the seal itself.  Requiring a
    // direct ray through the authored wall makes the long cast disappear even
    // though the player is a valid target.  The server still validates target
    // range, phase and hazard budget before committing the timeline.
    FINAL_STRIKE("final_strike", BossHazardBudget.MechanicKind.FINAL_STRIKE, true, false),
    REPOSITION("reposition", BossHazardBudget.MechanicKind.REPOSITION, false, false),
    RECOVER("recover", BossHazardBudget.MechanicKind.RECOVER, false, false);

    private final String id;
    private final BossHazardBudget.MechanicKind mechanic;
    private final boolean hardControl;
    private final boolean requiresLineOfSight;

    BossAbilityId(String id, BossHazardBudget.MechanicKind mechanic,
                  boolean hardControl, boolean requiresLineOfSight) {
        this.id = id;
        this.mechanic = mechanic;
        this.hardControl = hardControl;
        this.requiresLineOfSight = requiresLineOfSight;
    }

    public String id() {
        return id;
    }

    public BossHazardBudget.MechanicKind mechanic() {
        return mechanic;
    }

    public boolean hardControl() {
        return hardControl;
    }

    public boolean requiresLineOfSight() {
        return requiresLineOfSight;
    }

    public static BossAbilityId fromSpell(EndRiftAiPolicy.BossSpell spell) {
        if (spell == null) {
            return null;
        }
        try {
            return valueOf(spell.name().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
