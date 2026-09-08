import java.util.List;
import java.util.UUID;
import me.copimine.endevent.runtime.WaveSixChamberController;

public final class WaveSixChamberControllerTest {
    public static void main(String[] args) {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var controller = new WaveSixChamberController();
        controller.begin(7L, List.of(a, b));
        check(controller.owns(7L), "controller owns its generation");
        check(!controller.allowsInteraction(7L, a, b), "closed passage isolates chambers");
        int aChamber = controller.assignment().chamberByPlayer().get(a);
        check(controller.assignEntity(7L, a, aChamber), "mob assignment is accepted");
        check(controller.chamberOfEntity(7L, a) == aChamber, "mob room is stable");
        check(!controller.allowsMobTarget(7L, a, b), "mob cannot target a player in another room");
        check(controller.allowsMobTarget(7L, a, a), "mob can target its room player");
        controller.openPassage(7L);
        check(controller.allowsInteraction(7L, a, b), "open passage permits interaction");
        check(controller.allowsMobTarget(7L, a, b), "open passage permits cross-room target");
        controller.clear();
        check(!controller.owns(7L), "clear removes stale generation");
        check(controller.chamberOfEntity(7L, a) == -1, "clear removes mob room mapping");
        System.out.println("WaveSixChamberControllerTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
