import me.copimine.endevent.domain.BossContactStrikePolicy;
import me.copimine.endevent.domain.EndRiftAiPolicy.BossSpell;

public final class BossContactStrikePolicyTest {
    public static void main(String[] args) {
        require(BossContactStrikePolicy.damage(BossSpell.MELEE_SWIPE) == 8.0D,
                "the melee swipe keeps its authored 8 damage");
        require(BossContactStrikePolicy.damage(BossSpell.CHEST_STRIKE) == 10.0D,
                "the chest strike keeps its authored 10 damage");
        require(BossContactStrikePolicy.damage(BossSpell.GROUND_SLAM) == 12.0D,
                "the ground slam keeps its authored 12 damage");
        require(BossContactStrikePolicy.damage(BossSpell.VOID_BLAST) == 0.0D,
                "only the three contact abilities carry contact damage");
        require(BossContactStrikePolicy.knockback(BossSpell.GROUND_SLAM)
                        > BossContactStrikePolicy.knockback(BossSpell.MELEE_SWIPE),
                "the slam pushes harder than the swipe");
        require(BossContactStrikePolicy.lift(BossSpell.GROUND_SLAM)
                        > BossContactStrikePolicy.lift(BossSpell.CHEST_STRIKE),
                "the slam lifts the target higher than the directed strike");

        require(BossContactStrikePolicy.chestStrikeTicks() == 10,
                "the 12 block strike needs ten 1.2 block steps");
        require(BossContactStrikePolicy.chestProbeDistance(0) == 0.0D,
                "the strike starts at the chest");
        require(BossContactStrikePolicy.chestProbeDistance(-3) == 0.0D,
                "a negative tick cannot rewind the strike");
        double previous = -1.0D;
        for (int tick = 0; tick <= 20; tick++) {
            double probe = BossContactStrikePolicy.chestProbeDistance(tick);
            require(probe >= previous, "the probe must advance monotonically");
            require(probe <= BossContactStrikePolicy.CHEST_STRIKE_RANGE,
                    "the probe never leaves the authored range");
            previous = probe;
        }
        require(BossContactStrikePolicy.chestProbeDistance(10)
                        == BossContactStrikePolicy.CHEST_STRIKE_RANGE,
                "the probe reaches the full range on the last step");
        require(BossContactStrikePolicy.chestProbeDistance(50)
                        == BossContactStrikePolicy.CHEST_STRIKE_RANGE,
                "the probe stays bounded after the last step");

        require(BossContactStrikePolicy.chestStrikeHits(3.6D, 3.6D, 0.0D, 0.0D),
                "a target level with the probe is hit");
        require(BossContactStrikePolicy.chestStrikeHits(12.0D, 12.0D, 1.0D, -2.0D),
                "a target at exactly the range is still hit");
        require(!BossContactStrikePolicy.chestStrikeHits(6.0D, 0.5D, 0.0D, 0.0D),
                "a target behind the travelled probe is not hit");
        require(!BossContactStrikePolicy.chestStrikeHits(3.6D, 12.5D, 0.0D, 0.0D),
                "the strike cannot reach past its range");
        require(!BossContactStrikePolicy.chestStrikeHits(3.6D, 0.0D, 0.0D, 0.0D),
                "a target inside the boss body is not a chest strike hit");
        require(!BossContactStrikePolicy.chestStrikeHits(3.6D, -2.0D, 0.0D, 0.0D),
                "a target behind the boss is not hit");
        require(!BossContactStrikePolicy.chestStrikeHits(6.0D, 6.0D, 1.4D, 0.0D),
                "a target outside the strike radius is not hit");
        require(!BossContactStrikePolicy.chestStrikeHits(6.0D, 6.0D, 0.0D, 3.1D),
                "a target outside the vertical band is not hit");
        require(!BossContactStrikePolicy.chestStrikeHits(6.0D, 6.0D, 0.0D, -3.1D),
                "the vertical band is symmetric");

        require(BossContactStrikePolicy.CHEST_STRIKE_LAUNCH_MARKER_TICKS >= 30
                        && BossContactStrikePolicy.CHEST_STRIKE_LAUNCH_MARKER_TICKS <= 50,
                "the chest release pose must sit near the authored 2 s mark, not at the clip end");
        require(BossContactStrikePolicy.CHEST_STRIKE_LAUNCH_MARKER_TICKS
                        + BossContactStrikePolicy.CHEST_STRIKE_LAUNCH_TICKS
                        + BossContactStrikePolicy.chestStrikeTicks() <= 90,
                "the whole chest strike must still fit inside the 4.04 s clip");

        require(BossContactStrikePolicy.MELEE_SWIPE_CONTACT_MARKER_TICKS >= 20
                        && BossContactStrikePolicy.MELEE_SWIPE_CONTACT_MARKER_TICKS <= 30,
                "the swipe must land on the authored ~1.3 s cross-body keyframe");
        require(BossContactStrikePolicy.GROUND_SLAM_CONTACT_MARKER_TICKS >= 100
                        && BossContactStrikePolicy.GROUND_SLAM_CONTACT_MARKER_TICKS <= 120,
                "the slam must land on the authored 5.0-5.5 s contact keyframe");
        for (BossSpell spell : new BossSpell[]{
                BossSpell.MELEE_SWIPE,
                BossSpell.CHEST_STRIKE,
                BossSpell.GROUND_SLAM}) {
            int marker = BossContactStrikePolicy.contactMarkerTicks(spell);
            int clip = BossContactStrikePolicy.clipTicks(spell);
            require(marker > 0 && clip > marker,
                    spell + " must land before the clip ends, not on its last frame");
            require(BossContactStrikePolicy.followThroughTicks(spell) >= 20,
                    spell + " must leave a visible follow-through after the impact");
            require(BossContactStrikePolicy.followThroughTicks(spell) == clip - marker,
                    spell + " follow-through is exactly the rest of the clip");
        }
        require(BossContactStrikePolicy.contactMarkerTicks(
                        BossSpell.MELEE_SWIPE) == 26
                        && BossContactStrikePolicy.contactMarkerTicks(
                        BossSpell.GROUND_SLAM) == 110,
                "the authored contact keyframes are 26 and 110 ticks");
        require(!BossContactStrikePolicy.landed(20.0D, 20.0D, false),
                "a cancelled or immune hit must not produce a hit reaction");
        require(BossContactStrikePolicy.landed(20.0D, 10.0D, false),
                "real damage produces the hit reaction");
        require(BossContactStrikePolicy.landed(20.0D, 20.0D, true),
                "a lethal hit still produces the hit reaction");

        System.out.println("BossContactStrikePolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}