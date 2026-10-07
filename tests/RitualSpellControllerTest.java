import java.util.UUID;
import me.copimine.endevent.runtime.ritual.RitualSpellController;
import me.copimine.endevent.runtime.ritual.RitualSpellController.Spell;
import me.copimine.endevent.runtime.ritual.RitualSpellController.Stage;

public final class RitualSpellControllerTest {
    public static void main(String[] args) {
        RitualSpellController controller = new RitualSpellController(11L);
        UUID caster = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherCaster = UUID.fromString("22222222-2222-2222-2222-222222222222");

        check(!controller.start(10L, Spell.RIFT_BARRAGE, caster,
                        true, true, true, true, 100L).accepted(),
                "a stale generation cannot start a major spell");
        check(!controller.start(11L, Spell.RIFT_BARRAGE, caster,
                        false, true, true, true, 100L).accepted(),
                "a spell cannot start outside active Wave 6");
        check(!controller.start(11L, Spell.RIFT_BARRAGE, caster,
                        true, false, true, true, 100L).accepted(),
                "a dead caster cannot start a major spell");
        check(!controller.start(11L, Spell.RIFT_BARRAGE, caster,
                        true, true, false, true, 100L).accepted(),
                "a disabled spell cannot start");
        check(!controller.start(11L, Spell.RIFT_BARRAGE, caster,
                        true, true, true, false, 100L).accepted(),
                "a spell without a valid target cannot start");
        check(controller.snapshot().stage() == Stage.IDLE,
                "rejected starts must leave the shared scheduler idle");

        check(controller.start(11L, Spell.RIFT_BARRAGE, caster,
                        true, true, true, true, 100L).accepted(),
                "the first valid spell should begin its telegraph");
        check(controller.snapshot().stage() == Stage.TELEGRAPH,
                "a spell begins in telegraph");
        check(!controller.start(11L, Spell.GRAVITY_WELL, otherCaster,
                        true, true, true, true, 101L).accepted(),
                "a second major spell cannot overlap the first telegraph");
        check(controller.tick(11L, 119L).stage() == Stage.TELEGRAPH,
                "a spell cannot execute before its telegraph ends");
        check(controller.tick(11L, 120L).stage() == Stage.EXECUTE,
                "the spell becomes executable only after its telegraph");
        check(controller.effectStarted(11L, 120L).stage() == Stage.ACTIVE,
                "the single shared scheduler owns the active effect");
        check(!controller.start(11L, Spell.GRAVITY_WELL, otherCaster,
                        true, true, true, true, 121L).accepted(),
                "no second major spell starts during an active effect");
        check(controller.tick(10L, 125L).stage() == Stage.ACTIVE,
                "a stale generation cannot mutate an active spell");
        check(controller.complete(11L, 130L).stage() == Stage.RECOVERY,
                "every completed spell enters the shared recovery");
        check(!controller.start(11L, Spell.GRAVITY_WELL, otherCaster,
                        true, true, true, true, 149L).accepted(),
                "no major spell starts during global recovery");
        check(controller.tick(11L, 150L).stage() == Stage.IDLE,
                "the scheduler returns to idle after global recovery");
        check(controller.start(11L, Spell.GRAVITY_WELL, otherCaster,
                        true, true, true, true, 150L).accepted(),
                "a new spell may start after the recovery expires");

        controller.clear(11L);
        controller.clear(11L);
        check(controller.snapshot().stage() == Stage.IDLE,
                "clear is idempotent");
        check(controller.snapshot().spell() == null,
                "clear removes the old spell identity");

        check(controller.start(11L, Spell.GRAVITY_WELL, caster,
                        true, true, true, true, 200L, 24L).accepted(),
                "the Gravity Well can use its 1.2 second warning window");
        check(controller.tick(11L, 223L).stage() == Stage.TELEGRAPH,
                "Gravity Well remains telegraphed until its warning completes");
        check(controller.tick(11L, 224L).stage() == Stage.EXECUTE,
                "Gravity Well executes after 24 event ticks");
        System.out.println("RitualSpellControllerTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
