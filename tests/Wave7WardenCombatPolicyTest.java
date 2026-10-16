import me.copimine.endevent.domain.Wave7WardenCombatPolicy;

public final class Wave7WardenCombatPolicyTest {
    private Wave7WardenCombatPolicyTest() {
    }

    public static void main(String[] args) {
        check(Wave7WardenCombatPolicy.maxHealth() == 200.0D,
                "the Warden health profile matches the approved approximate 200 HP");
        check(Wave7WardenCombatPolicy.currentHealthAfterMaxHealthChange(150.0D, 150.0D)
                        == 200.0D,
                "a previously full old-profile Warden is promoted to full new-profile health");
        check(Wave7WardenCombatPolicy.currentHealthAfterMaxHealthChange(63.0D, 150.0D)
                        == 63.0D
                        && Wave7WardenCombatPolicy.currentHealthAfterMaxHealthChange(63.0D, 200.0D)
                        == 63.0D,
                "partial Warden health remains unchanged across recovery and restart");
        check(Wave7WardenCombatPolicy.currentHealthAfterMaxHealthChange(230.0D, 300.0D)
                        == 200.0D,
                "recovered health is clamped to the configured maximum");

        Wave7WardenCombatPolicy.AttackProfile narrow =
                Wave7WardenCombatPolicy.profileForPattern(0);
        Wave7WardenCombatPolicy.AttackProfile wide =
                Wave7WardenCombatPolicy.profileForPattern(1);
        Wave7WardenCombatPolicy.AttackProfile lunge =
                Wave7WardenCombatPolicy.profileForPattern(2);
        check(narrow.warning() != wide.warning()
                        && narrow.warning() != lunge.warning()
                        && wide.warning() != lunge.warning(),
                "the three existing Warden attacks have distinguishable warning identities");
        check(Wave7WardenCombatPolicy.patternForOrdinal(3) == 0
                        && Wave7WardenCombatPolicy.patternForOrdinal(4) == 1
                        && Wave7WardenCombatPolicy.patternForOrdinal(5) == 2,
                "Warden attacks keep their existing bounded three-pattern cycle");
        check(narrow.warningLength() == 7.0D && narrow.warningWidth() == 1.35D
                        && narrow.damageRange() == 7.0D && narrow.damage() == 5.5D,
                "narrow strike range, width, and damage remain unchanged");
        check(wide.warningLength() == 5.0D && wide.warningWidth() == 2.7D
                        && wide.damageRange() == 5.0D && wide.damage() == 4.0D,
                "wide strike range, width, and damage remain unchanged");
        check(lunge.lunge() && lunge.lungeTicks() == 9
                        && lunge.lungeSpeed() == 0.82D
                        && lunge.lungeDamageWidth() == 1.3D
                        && lunge.lungeDamageRange() == 4.0D
                        && lunge.lungeDamage() == 5.0D,
                "lunge duration, speed, width, range, and damage remain unchanged");
        System.out.println("Wave7WardenCombatPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
