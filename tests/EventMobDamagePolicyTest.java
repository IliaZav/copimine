import me.copimine.endevent.domain.EventMobDamagePolicy;

public final class EventMobDamagePolicyTest {
    public static void main(String[] args) {
        check(EventMobDamagePolicy.adjust(10.0D, 1.0D, false, false, false).damage() == 6.0D,
                "ordinary event damage is reduced by four");
        check(EventMobDamagePolicy.adjust(3.0D, 1.0D, false, false, false).damage() == 1.0D,
                "ordinary event damage is clamped to its minimum");
        check(EventMobDamagePolicy.adjust(10.0D, 1.0D, true, false, false).damage() == 10.0D,
                "boss damage is not reduced by the mob policy");
        check(EventMobDamagePolicy.adjust(10.0D, 1.0D, false, true, false).damage() == 10.0D,
                "scripted HP/control damage is not reduced");
        EventMobDamagePolicy.Decision already = EventMobDamagePolicy.adjust(6.0D, 1.0D, false, false, true);
        check(already.damage() == 6.0D && !already.adjusted(),
                "a damage value already adjusted upstream must not be reduced twice");
        check(EventMobDamagePolicy.adjust(Double.NaN, 1.0D, false, false, false).damage() == 1.0D,
                "non-finite damage fails closed at the minimum");
        System.out.println("EventMobDamagePolicyTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
