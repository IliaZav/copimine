import me.copimine.endevent.domain.CocoonBeamPolicy;

public final class CocoonBeamPolicyTest {
    public static void main(String[] args) {
        check(CocoonBeamPolicy.FIRE_TICKS == 400, "active beam must last exactly 20 seconds at 20 TPS");
        check(CocoonBeamPolicy.COOLDOWN_TICKS == 2400, "cooldown must be 120 seconds after termination");
        check(CocoonBeamPolicy.CHARGE_TICKS > 0 && CocoonBeamPolicy.RECOVERY_TICKS > 0,
                "charge and recovery must stay outside the active window");
        check(CocoonBeamPolicy.HIT_INTERVAL_TICKS == 20, "initial balance: one accepted hit per second");
        check(CocoonBeamPolicy.DAMAGE == 4.0D, "initial balance: 4 HP per accepted contact");
        check(CocoonBeamPolicy.maxTurn(1, false) < CocoonBeamPolicy.maxTurn(1, true),
                "release must track more slowly than warning");
        check(Math.toDegrees(CocoonBeamPolicy.maxTurn(1, false)) < 1D,
                "live beam must not snap onto a sidestepping target");
        check(CocoonBeamPolicy.maxTurn(200, false) == CocoonBeamPolicy.maxTurn(5, false),
                "an interrupted tick must not allow an instant turn");
        double escaping = -.21D;
        check(escaping + CocoonBeamPolicy.pullIncrement(escaping) < 0D,
                "inward force must not cancel outward movement in a tick");
        check(.17D + CocoonBeamPolicy.pullIncrement(.17D) <= .18000001D,
                "inward force must taper at its cap");
        check(CocoonBeamPolicy.pullIncrement(.30D) == 0D, "existing inward speed must not be amplified");
        check(CocoonBeamPolicy.pullIncrement(Double.NaN) == 0D, "non-finite motion must not be amplified");
        System.out.println("CocoonBeamPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
