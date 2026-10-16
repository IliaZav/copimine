import java.util.UUID;
import me.copimine.endevent.runtime.ritual.PrisonerAbilityController;
import me.copimine.endevent.runtime.ritual.PrisonerAbilityController.Ability;
import me.copimine.endevent.runtime.ritual.PrisonerAbilityController.HudState;
import me.copimine.endevent.runtime.ritual.PrisonerAbilityController.Rejection;

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
                "release hides cooldown state from the inactive session");
        reconnectRetainsCurrentIdentityCooldowns(prisoner, ally);
        newIdentityStartsFreshCooldowns(prisoner, ally);
        System.out.println("PrisonerAbilityControllerTest OK");
    }

    private static void reconnectRetainsCurrentIdentityCooldowns(UUID prisoner, UUID ally) {
        PrisonerAbilityController controller = new PrisonerAbilityController();
        controller.start(8L, prisoner);
        Ability[] abilities = {Ability.HEAL, Ability.BATTLE_SURGE, Ability.GUARDIAN_LINK, Ability.TURNCOAT};
        long[] deadlines = {20_100L, 30_100L, 30_100L, 55_100L};
        for (Ability ability : abilities) {
            check(controller.request(8L, prisoner, ability, ally, 4, 100L, true).accepted(),
                    "fixture casts every unlocked ability before disconnect");
        }
        controller.end(8L);
        check(controller.snapshot(4).generation() == 0L && controller.snapshot(4).prisoner() == null,
                "disconnect exposes an inactive identity so the caller sends a fresh resume on reconnect");
        check(controller.request(8L, prisoner, Ability.HEAL, ally, 4, 200L, true).rejection()
                        == Rejection.INACTIVE_SESSION,
                "a queued request stays rejected while the prisoner is disconnected");
        controller.start(8L, prisoner);
        for (int index = 0; index < abilities.length; index++) {
            Ability ability = abilities[index];
            check(controller.request(8L, prisoner, ability, ally, 4, deadlines[index] - 1L, true).rejection()
                            == Rejection.COOLDOWN,
                    "reconnecting the same prisoner in the same generation preserves " + ability + " cooldown");
            check(controller.cooldownUntil(ability) == deadlines[index],
                    "reconnect keeps the original absolute " + ability + " deadline");
        }
        controller.start(8L, prisoner);
        check(controller.request(8L, prisoner, Ability.HEAL, ally, 4, 20_099L, true).rejection()
                        == Rejection.COOLDOWN,
                "a duplicate start cannot reset an active cooldown");
        controller.end(7L);
        check(controller.request(7L, prisoner, Ability.HEAL, ally, 4, 20_100L, true).rejection()
                        == Rejection.STALE_GENERATION,
                "a stale end cannot deactivate the current session and stale requests still fail");
        controller.end(8L);
        check(controller.state(Ability.HEAL, 4, 55_101L, true) == HudState.LOCKED,
                "inactive HUD state never advertises an input-ready ability");
        check(controller.request(8L, prisoner, Ability.HEAL, ally, 4, 55_101L, true).rejection()
                        == Rejection.INACTIVE_SESSION,
                "expiry alone cannot reactivate an ended session");
        controller.start(8L, prisoner);
        for (int index = 0; index < abilities.length; index++) {
            check(controller.request(8L, prisoner, abilities[index], ally, 4, deadlines[index], true).accepted(),
                    "disconnect time counts toward the original " + abilities[index] + " cooldown deadline");
        }
    }

    private static void newIdentityStartsFreshCooldowns(UUID prisoner, UUID ally) {
        PrisonerAbilityController controller = new PrisonerAbilityController();
        controller.start(8L, prisoner);
        check(controller.request(8L, prisoner, Ability.HEAL, ally, 1, 100L, true).accepted(),
                "fixture consumes the original identity cooldown");
        controller.end(8L);
        controller.start(9L, prisoner);
        check(controller.request(9L, prisoner, Ability.HEAL, ally, 1, 200L, true).accepted(),
                "a new generation starts an independent cooldown session for the same player");
        controller.end(9L);
        controller.start(9L, ally);
        check(controller.request(9L, ally, Ability.HEAL, prisoner, 1, 300L, true).accepted(),
                "a replacement prisoner starts an independent cooldown session in the same generation");
        check(controller.request(9L, prisoner, Ability.HEAL, ally, 1, 300L, true).rejection()
                        == Rejection.NOT_PRISONER,
                "the replaced prisoner cannot use the new session");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
