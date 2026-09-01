import me.copimine.endevent.domain.RiftObeliskDamagePolicy;
import me.copimine.endevent.domain.RiftFireballPolicy;

public final class RiftObeliskDamagePolicyTest {
    public static void main(String[] args) {
        testOnlyCurrentReflectedPlayerProjectileDamagesObelisk();
        testThreeHitsDestroyAndFourthIsNoop();
        testPulseEffectsAreExact();
        testFireballEffectsAreExact();
        testBossIsImmuneOnlyToRiftFireballSource();
        testVanillaPlayerDamageIsOwnedByTheImpactTransaction();
        System.out.println("RiftObeliskDamagePolicyTest OK");
    }

    private static void testOnlyCurrentReflectedPlayerProjectileDamagesObelisk() {
        check(RiftObeliskDamagePolicy.applyReflectedHit(3, true, true, true, true).remainingHealth() == 2,
                "current reflected player fireball must remove one HP");
        check(RiftObeliskDamagePolicy.applyReflectedHit(3, false, true, true, true).remainingHealth() == 3,
                "ordinary fireball must not damage an obelisk");
        check(RiftObeliskDamagePolicy.applyReflectedHit(3, true, false, true, true).remainingHealth() == 3,
                "unreflected fireball must not damage an obelisk");
        check(RiftObeliskDamagePolicy.applyReflectedHit(3, true, true, false, true).remainingHealth() == 3,
                "old-generation fireball must not damage an obelisk");
        check(RiftObeliskDamagePolicy.applyReflectedHit(3, true, true, true, false).remainingHealth() == 3,
                "non-player reflection must not damage an obelisk");
    }

    private static void testThreeHitsDestroyAndFourthIsNoop() {
        int hp = 3;
        hp = RiftObeliskDamagePolicy.applyReflectedHit(hp, true, true, true, true).remainingHealth();
        hp = RiftObeliskDamagePolicy.applyReflectedHit(hp, true, true, true, true).remainingHealth();
        RiftObeliskDamagePolicy.HitResult destroyed =
                RiftObeliskDamagePolicy.applyReflectedHit(hp, true, true, true, true);
        check(destroyed.destroyed() && destroyed.remainingHealth() == 0, "third reflected hit must destroy it");
        check(RiftObeliskDamagePolicy.applyReflectedHit(0, true, true, true, true).ignored(),
                "hit after destruction must be a no-op");
    }

    private static void testPulseEffectsAreExact() {
        RiftFireballPolicy.EffectProfile pulse = RiftFireballPolicy.pulseEffects();
        check(pulse.damage() == 0.0D && pulse.blindnessTicks() == 0,
                "pulse must not damage or blind");
        check(pulse.weaknessAmplifier() == 0 && pulse.nauseaAmplifier() == 1
                        && pulse.slownessAmplifier() == 0,
                "pulse amplifiers must be Weakness I, Nausea II, Slowness I");
        check(pulse.weaknessTicks() == 60 && pulse.nauseaTicks() == 60 && pulse.slownessTicks() == 60,
                "pulse effects must last exactly 60 ticks");
        check(RiftFireballPolicy.PULSE_RADIUS == 5.0D, "pulse radius must be five blocks");
    }

    private static void testFireballEffectsAreExact() {
        RiftFireballPolicy.EffectProfile hit = RiftFireballPolicy.fireballEffects(6.0D, 40, 60);
        check(hit.damage() == 6.0D && hit.blindnessTicks() == 40,
                "fireball hit must use configured damage and 40-tick blindness");
        check(hit.weaknessTicks() == 60 && hit.nauseaTicks() == 60 && hit.slownessTicks() == 60,
                "fireball debuffs must last exactly 60 ticks");
        check(hit.weaknessAmplifier() == 0 && hit.nauseaAmplifier() == 1 && hit.slownessAmplifier() == 0,
                "fireball amplifiers must be I, II, I");
    }

    private static void testBossIsImmuneOnlyToRiftFireballSource() {
        check(RiftFireballPolicy.blocksBossDamage(true, true),
                "direct Rift Fireball damage to boss must be blocked");
        check(RiftFireballPolicy.blocksBossDamage(true, false),
                "reflected Rift Fireball damage to boss must be blocked");
        check(!RiftFireballPolicy.blocksBossDamage(false, false),
                "ordinary player attack must remain damageable");
    }

    private static void testVanillaPlayerDamageIsOwnedByTheImpactTransaction() {
        check(RiftFireballPolicy.blocksVanillaPlayerDamage(true),
                "event Rift Fireball vanilla player damage must be cancelled");
        check(!RiftFireballPolicy.blocksVanillaPlayerDamage(false),
                "ordinary fireball player damage must remain untouched");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
