import java.util.List;

import me.copimine.endevent.domain.BossEdgeVolleyPolicy;
import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.BossPhasePolicy;
import me.copimine.endevent.domain.EndRiftAiPolicy;
import me.copimine.endevent.domain.EndRiftAiPolicy.BossSpell;

/**
 * Asserts the approved phase progression through the real selection path
 * ({@link EndRiftAiPolicy#chooseBossSpell}) instead of enum membership alone.
 */
public final class BossPhaseGatingTest {
    public static void main(String[] args) {
        for (BossPhase phase : BossPhase.values()) {
            List<BossSpell> pool = BossPhasePolicy.spellPool(phase);
            require(!pool.isEmpty(), phase + " must expose an authored spell pool");
            for (int cursor = 0; cursor < 64; cursor++) {
                BossSpell chosen = EndRiftAiPolicy.chooseBossSpell(pool, null, cursor);
                require(chosen != null && pool.contains(chosen),
                        phase + " selected " + chosen + " outside its pool");
            }
            if (pool.size() > 1) {
                for (int cursor = 0; cursor < 64; cursor++) {
                    BossSpell previous = pool.get(cursor % pool.size());
                    BossSpell chosen = EndRiftAiPolicy.chooseBossSpell(pool, previous, cursor);
                    require(chosen != previous,
                            phase + " repeated " + previous + " back to back");
                }
            }
        }

        List<BossSpell> awakening = BossPhasePolicy.spellPool(BossPhase.AWAKENING);
        for (BossSpell late : new BossSpell[]{
                BossSpell.RIFT_ARROWS, BossSpell.VOID_MARK, BossSpell.SUMMON_SERVANTS,
                BossSpell.BOSS_FIREBALL, BossSpell.ARENA_INFERNO, BossSpell.FINAL_STRIKE}) {
            require(!awakening.contains(late), "Awakening must not offer " + late);
        }

        List<BossSpell> hunt = BossPhasePolicy.spellPool(BossPhase.HUNT);
        for (BossSpell late : new BossSpell[]{
                BossSpell.SUMMON_SERVANTS, BossSpell.BOSS_FIREBALL,
                BossSpell.ARENA_INFERNO, BossSpell.FINAL_STRIKE}) {
            require(!hunt.contains(late), "Hunt must not offer " + late);
        }

        require(!BossPhasePolicy.spellPool(BossPhase.RIFT).contains(BossSpell.ARENA_INFERNO),
                "Inferno unlocks in Overload, not Rift");

        for (BossPhase phase : new BossPhase[]{BossPhase.OVERLOAD, BossPhase.RAGE, BossPhase.LAST_SEAL}) {
            require(BossPhasePolicy.spellPool(phase).contains(BossSpell.ARENA_INFERNO),
                    phase + " must offer the completed Inferno");
        }

        for (BossPhase phase : new BossPhase[]{BossPhase.AWAKENING, BossPhase.HUNT,
                BossPhase.RIFT, BossPhase.OVERLOAD, BossPhase.RAGE}) {
            require(!BossPhasePolicy.spellPool(phase).contains(BossSpell.FINAL_STRIKE),
                    "Final Strike belongs to the Last Seal only (" + phase + ")");
        }
        require(BossPhasePolicy.spellPool(BossPhase.LAST_SEAL).contains(BossSpell.FINAL_STRIKE),
                "Last Seal must offer the Final Strike");
        require(BossPhasePolicy.spellPool(BossPhase.LAST_SEAL).contains(BossSpell.SMALL_KAGUNE),
                "Last Seal must expose the small melee Kagune summon");
        require(!BossPhasePolicy.spellPool(BossPhase.RIFT).contains(BossSpell.SMALL_KAGUNE),
                "the small Kagune summon belongs to LAST_SEAL only");

        for (BossPhase phase : new BossPhase[]{BossPhase.AWAKENING, BossPhase.HUNT,
                BossPhase.RIFT, BossPhase.OVERLOAD, BossPhase.RAGE}) {
            List<BossSpell> pool = BossPhasePolicy.spellPool(phase);
            require(pool.contains(BossSpell.MELEE_SWIPE), phase + " must keep the basic swipe");
        }
        require(!awakening.contains(BossSpell.CHEST_STRIKE) && !awakening.contains(BossSpell.GROUND_SLAM),
                "Awakening must not unlock the later grounded contact attacks");
        require(hunt.contains(BossSpell.CHEST_STRIKE) && !hunt.contains(BossSpell.GROUND_SLAM),
                "Hunt unlocks chest strike; ground slam starts in Rift");
        for (BossPhase phase : new BossPhase[]{BossPhase.RIFT, BossPhase.OVERLOAD, BossPhase.RAGE}) {
            List<BossSpell> pool = BossPhasePolicy.spellPool(phase);
            require(pool.contains(BossSpell.CHEST_STRIKE) && pool.contains(BossSpell.GROUND_SLAM),
                    phase + " must keep both later grounded contact attacks");
        }
        for (BossSpell contact : new BossSpell[]{BossSpell.MELEE_SWIPE,
                BossSpell.CHEST_STRIKE, BossSpell.GROUND_SLAM}) {
            require(!BossPhasePolicy.spellPool(BossPhase.LAST_SEAL).contains(contact),
                    "the hovering Last Seal must not use grounded contact ability " + contact);
        }

        require(BossPhasePolicy.spellPool(BossPhase.HUNT).contains(BossSpell.RIFT_ARROWS)
                        && !BossEdgeVolleyPolicy.allowsPhase(BossPhase.HUNT),
                "Hunt keeps the plain arrow volley; the corner volley unlocks later");
        // The edge-volley trigger lives in the RIFT_ARROWS branch, so phase
        // three must actually offer that spell or the ability is unreachable.
        require(BossPhasePolicy.spellPool(BossPhase.RIFT).contains(BossSpell.RIFT_ARROWS),
                "Rift must offer the arrow volley so the corner volley is reachable");
        require(!BossPhasePolicy.spellPool(BossPhase.LAST_SEAL).contains(BossSpell.RIFT_ARROWS),
                "the hovering Last Seal must not offer the edge volley");
        for (BossPhase phase : new BossPhase[]{BossPhase.RIFT, BossPhase.OVERLOAD, BossPhase.RAGE}) {
            require(BossEdgeVolleyPolicy.allowsPhase(phase),
                    phase + " must allow the corner volley");
        }
        long deadline = 1_000L + BossEdgeVolleyPolicy.COOLDOWN_MILLIS;
        require(BossEdgeVolleyPolicy.onCooldown(deadline - 1L, deadline),
                "the corner volley must stay gated for the whole cooldown");
        require(!BossEdgeVolleyPolicy.onCooldown(deadline, deadline),
                "the corner volley must unlock exactly at the deadline");

        System.out.println("BossPhaseGatingTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
