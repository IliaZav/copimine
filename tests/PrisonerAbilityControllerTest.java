import java.util.UUID;
import me.copimine.endevent.runtime.ritual.PrisonerAbilityController;
import me.copimine.endevent.runtime.ritual.PrisonerAbilityController.Ability;
import me.copimine.endevent.runtime.ritual.PrisonerAbilityController.HudState;

public final class PrisonerAbilityControllerTest {
    public static void main(String[] args) {
        UUID prisoner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID ally = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PrisonerAbilityController controller = new PrisonerAbilityController();

        check(!controller.request(8L, prisoner, Ability.HEAL, ally, 1, 0L, true).accepted(),
                "requests outside an active prisoner session are rejected");
        controller.start(8L, prisoner);
        check(controller.state(Ability.HEAL, 1, 0L, false) == HudState.NO_TARGET,
                "an unlocked ability without a valid target reports NO_TARGET");
        check(!controller.request(7L, prisoner, Ability.HEAL, ally, 0, 0L, true).accepted(),
                "stale encounter generations are rejected");
        check(!controller.request(8L, ally, Ability.HEAL, prisoner, 1, 0L, true).accepted(),
                "only the current prisoner can cast");
        check(!controller.request(8L, prisoner, Ability.HEAL, ally, 0, 0L, true).accepted(),
                "an ability remains locked until its Caster death");
        check(!controller.request(8L, prisoner, Ability.HEAL, ally, 1, 0L, false).accepted(),
                "an invalid target cannot consume a cooldown");
        check(controller.request(8L, prisoner, Ability.HEAL, ally, 1, 100L, true).accepted(),
                "the unlocked ability accepts an eligible target");
        check(controller.cooldownUntil(Ability.HEAL) == 20_100L,
                "successful casts use the fixed server cooldown");
        check(controller.state(Ability.HEAL, 1, 101L, true) == HudState.COOLDOWN,
                "the HUD reports server cooldown state");
        check(!controller.request(8L, prisoner, Ability.HEAL, ally, 1, 20_099L, true).accepted(),
                "a cooldown cannot be bypassed");
        check(controller.request(8L, prisoner, Ability.HEAL, ally, 1, 20_100L, true).accepted(),
                "the ability becomes ready at its authoritative deadline");
        check(controller.state(Ability.BATTLE_SURGE, 1, 20_101L, true) == HudState.LOCKED,
                "later abilities stay locked");
        controller.end(8L);
        check(!controller.request(8L, prisoner, Ability.HEAL, ally, 1, 50_000L, true).accepted(),
                "release clears the active session immediately");
        check(controller.cooldownUntil(Ability.HEAL) == 0L,
                "release clears per-session cooldown state");
        System.out.println("PrisonerAbilityControllerTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
