import me.copimine.endevent.domain.BossAnimationId;

import java.util.Set;

public final class BossAnimationIdTest {
    public static void main(String[] args) {
        require(BossAnimationId.fromWire("Running2") == BossAnimationId.RUN,
                "artist Running2 alias must resolve to RUN");
        require(BossAnimationId.fromWire("Swipe2") == BossAnimationId.MELEE_SWIPE,
                "artist Swipe2 alias must resolve to MELEE_SWIPE");
        require(BossAnimationId.fromWire("Hurt2") == BossAnimationId.HURT,
                "artist Hurt2 alias must resolve to HURT");
        require(BossAnimationId.fromWire("Dying2") == BossAnimationId.DYING,
                "artist Dying2 alias must resolve to DYING");
        require(BossAnimationId.fromWire("udar_iz_grudi") == BossAnimationId.CHEST_STRIKE,
                "artist chest strike alias must resolve to CHEST_STRIKE");
        require(BossAnimationId.fromWire("udar_po_zemle2") == BossAnimationId.GROUND_SLAM,
                "artist ground slam alias must resolve to GROUND_SLAM");
        require(BossAnimationId.fromWire("TELEGRAPHING") == BossAnimationId.CAST_CHARGE,
                "legacy cast telegraph alias must remain readable");
        require(BossAnimationId.fromWire("EXECUTING") == BossAnimationId.CAST_RELEASE,
                "legacy cast release alias must remain readable");

        Set<String> required = Set.of(
                "IDLE_BREATH", "RUN", "MELEE_SWIPE", "CHEST_STRIKE", "GROUND_SLAM",
                "MARK_CONTROL", "SUMMON_CHANNEL", "HURT", "PHASE_TRANSITION", "FINAL_STRIKE",
                "DYING", "TELEPORT_RIP", "CAST_CHARGE", "CAST_RELEASE", "CAST_IMPACT",
                "RECOVERY", "SPELL_VOID_BLAST", "SPELL_RIFT_PROJECTILE", "SPELL_RIFT_ARROWS",
                "SPELL_ARROW_SALVO", "SPELL_VOID_MARK", "SPELL_SUMMON_SERVANTS", "SPELL_SUMMON",
                "SPELL_RIFT_OBELISKS", "SPELL_ARENA_INFERNO", "UNKNOWN", "NONE");
        for (String wireId : required) {
            require(BossAnimationId.isKnown(wireId), "missing animation catalog entry: " + wireId);
        }
        require(BossAnimationId.fromWire("not-a-production-animation") == BossAnimationId.UNKNOWN,
                "unknown animation must fail closed to explicit UNKNOWN");
        require(BossAnimationId.aliases().get("RUNNING2") == BossAnimationId.RUN,
                "alias index must expose canonical mapping");
        try {
            BossAnimationId.aliases().put("BROKEN", BossAnimationId.UNKNOWN);
            throw new AssertionError("animation alias index must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        System.out.println("BossAnimationIdTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
