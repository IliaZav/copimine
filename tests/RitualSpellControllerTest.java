import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import me.copimine.endevent.domain.FallingAnvilVolleyPolicy.LandingPoint;
import me.copimine.endevent.domain.GravityAnchorPolicy.AnchorPoint;
import me.copimine.endevent.domain.GravityAnchorPolicy.Phase;
import me.copimine.endevent.runtime.ritual.RitualSpellController;
import me.copimine.endevent.runtime.ritual.RitualSpellController.Spell;
import me.copimine.endevent.runtime.ritual.RitualSpellController.Stage;
import me.copimine.endevent.runtime.ritual.RitualSpellController.Wave6Spell;

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
                        true, true, true, true, 379L).accepted(),
                "no major spell starts before fourteen seconds have elapsed since the previous start");
        check(controller.tick(11L, 379L).stage() == Stage.RECOVERY,
                "spell recovery remains active until the start-to-start cadence expires");
        check(controller.tick(11L, 380L).stage() == Stage.IDLE,
                "the scheduler returns to idle at fourteen seconds after the prior spell start");
        check(controller.start(11L, Spell.GRAVITY_WELL, otherCaster,
                        true, true, true, true, 380L).accepted(),
                "a new spell may start when the fourteen-second cadence expires");

        controller.clear(11L);
        controller.clear(11L);
        check(controller.snapshot().stage() == Stage.IDLE,
                "clear is idempotent");
        check(controller.snapshot().spell() == null,
                "clear removes the old spell identity");

        check(controller.start(11L, Spell.GRAVITY_WELL, caster,
                        true, true, true, true, 400L, 24L).accepted(),
                "the Gravity Well can use its 1.2 second warning window");
        check(controller.tick(11L, 423L).stage() == Stage.TELEGRAPH,
                "Gravity Well remains telegraphed until its warning completes");
        check(controller.tick(11L, 424L).stage() == Stage.EXECUTE,
                "Gravity Well executes after 24 event ticks");

        wave6AnchorAndAnvilControllerContract();
        System.out.println("RitualSpellControllerTest OK");
    }

    private static void wave6AnchorAndAnvilControllerContract() {
        long generation = 72L;
        UUID caster = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID target = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID crystal = UUID.fromString("66666666-6666-6666-6666-666666666666");
        RitualSpellController controller = new RitualSpellController(generation);

        var anchor = controller.startGravityAnchor(generation, caster, target,
                new AnchorPoint(10.5D, 72.5D, -3.5D), 2,
                true, true, true, true, 100L);
        check(anchor.accepted(), "Gravity Anchor starts for one eligible fixed target");
        check(anchor.snapshot().wave6Spell() == Wave6Spell.GRAVITY_ANCHOR
                        && anchor.snapshot().stage() == Stage.TELEGRAPH
                        && anchor.snapshot().deadlineTick() == 140L,
                "Gravity Anchor uses its two-second (40 tick) warning, not generic 20 ticks");
        check(anchor.snapshot().gravityAnchorState().phase() == Phase.TELEGRAPH
                        && anchor.snapshot().gravityAnchorState().targetPlayer().equals(target)
                        && anchor.snapshot().combinedRiftBarrageProjectiles() == 2,
                "optional barrage projectiles remain part of one captured Anchor cast");
        var crystalSpawnedDuringWarning = controller.gravityAnchorCrystalSpawned(
                generation, crystal, 100L);
        check(crystalSpawnedDuringWarning.stage() == Stage.TELEGRAPH
                        && crystalSpawnedDuringWarning.gravityAnchorState().phase() == Phase.ANCHOR
                        && crystalSpawnedDuringWarning.gravityAnchorState().crystalEntity().equals(crystal),
                "the visible End Crystal is bound while its 40-tick warning is running");
        check(controller.tick(generation, 120L).stage() == Stage.TELEGRAPH,
                "Anchor cannot execute after the generic 20-tick duration");
        check(!controller.startFallingAnvils(generation, UUID.randomUUID(), 11,
                        1, grid(0.0D), true, true, true, true, 120L).accepted(),
                "anvil spell cannot overlap an active combined Gravity Anchor cast");
        check(controller.tick(generation, 140L).stage() == Stage.EXECUTE,
                "Anchor leaves its visible crystal warning only after 40 ticks");
        var pullStarted = controller.advanceGravityAnchorPhase(generation, Phase.PULL, 140L);
        check(pullStarted.stage() == Stage.ACTIVE
                        && pullStarted.gravityAnchorState().phase() == Phase.PULL,
                "pull begins only after the crystal warning completes");
        check(controller.advanceGravityAnchorPhase(generation, Phase.EXPLOSION, 142L)
                        .gravityAnchorState().phase() == Phase.EXPLOSION,
                "pull transitions to the terrain-safe explosion adapter hook");
        check(controller.advanceGravityAnchorPhase(generation, Phase.LANDING, 143L)
                        .gravityAnchorState().phase() == Phase.LANDING,
                "explosion transitions to bounded fall and landing resolution");
        var anchorComplete = controller.advanceGravityAnchorPhase(generation, Phase.COMPLETE, 144L);
        check(anchorComplete.stage() == Stage.RECOVERY
                        && anchorComplete.gravityAnchorState().phase() == Phase.COMPLETE
                        && anchorComplete.caster().equals(caster),
                "Anchor owns one caster and remains locked until landing cleanup completes");
        check(controller.tick(generation, 379L).stage() == Stage.RECOVERY,
                "the completed Anchor cannot shorten the start-to-start cadence");
        check(controller.tick(generation, 380L).stage() == Stage.IDLE,
                "the Anchor scheduler recovers at fourteen seconds after its start");

        List<LandingPoint> firstGrid = grid(0.0D);
        var anvils = controller.startFallingAnvils(generation, caster, 11,
                8, firstGrid, true, true, true, true, 400L);
        check(anvils.accepted() && anvils.snapshot().wave6Spell() == Wave6Spell.FALLING_ANVILS,
                "eleven participants start Falling Anvils as one major cast");
        check(anvils.snapshot().fallingAnvilState().totalVolleys() == 2
                        && anvils.snapshot().fallingAnvilState().activeVisuals() == 25
                        && anvils.snapshot().deadlineTick() == 460L,
                "first 5x5 volley locks 25 visuals behind a 60-tick warning");
        check(anvils.snapshot().fallingAnvilState().landingPositions().equals(firstGrid),
                "warning snapshots immutable first-volley landing positions");
        check(!controller.startGravityAnchor(generation, UUID.randomUUID(), target,
                        new AnchorPoint(5.5D, 73.5D, 5.5D), 0,
                        true, true, true, true, 400L).accepted(),
                "Gravity Anchor cannot overlap either half of anvil cast");
        check(controller.tick(generation, 459L).fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.WARNING,
                "the first anvil group remains warned before tick 60");
        var firstImpact = controller.tick(generation, 460L);
        check(firstImpact.stage() == Stage.EXECUTE
                        && firstImpact.fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.IMPACT,
                "the first group drops at the end of its warning");
        controller.effectStarted(generation, 460L);
        var intermission = controller.fallingAnvilImpactResolved(generation, 460L);
        check(intermission.fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.INTERMISSION
                        && intermission.fallingAnvilState().deadlineTick() == 480L,
                "the next warning is gated by one second after confirmed impact");
        check(controller.tick(generation, 479L).fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.INTERMISSION,
                "volley two cannot start before the post-impact delay");

        List<LandingPoint> secondGrid = grid(20.0D);
        var secondWarning = controller.beginNextFallingAnvilVolley(
                generation, 7, secondGrid, 480L);
        check(secondWarning.fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.WARNING
                        && secondWarning.fallingAnvilState().volleyIndex() == 2
                        && secondWarning.fallingAnvilState().deadlineTick() == 540L,
                "a newly selected second grid begins a full 60-tick warning at tick 80");
        check(secondWarning.fallingAnvilState().landingPositions().equals(secondGrid),
                "volley two captures its new locked cluster rather than following volley one");
        var secondImpact = controller.tick(generation, 540L);
        check(secondImpact.fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.IMPACT,
                "volley two drops after its own three-second warning");
        controller.effectStarted(generation, 540L);
        var anvilComplete = controller.fallingAnvilImpactResolved(generation, 540L);
        check(anvilComplete.stage() == Stage.RECOVERY
                        && anvilComplete.fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.COMPLETE,
                "the second impact completes the single major spell and starts recovery");

        RitualSpellController smallRoster = new RitualSpellController(73L);
        var threePlayer = smallRoster.startFallingAnvils(73L, caster, 3,
                2, List.of(new LandingPoint(1.5D, 64.0D, 1.5D),
                        new LandingPoint(2.5D, 64.0D, 1.5D)),
                true, true, true, true, 0L);
        check(threePlayer.accepted()
                        && threePlayer.snapshot().fallingAnvilState().activeVisuals() == 2,
                "2–3 roster creates one locked anvil per free participant");

        RitualSpellController mediumRoster = new RitualSpellController(74L);
        var tenPlayer = mediumRoster.startFallingAnvils(74L, caster, 10,
                3, groupedGridNines(0.0D, 3), true, true, true, true, 0L);
        check(tenPlayer.accepted()
                        && tenPlayer.snapshot().fallingAnvilState().totalVolleys() == 1
                        && tenPlayer.snapshot().fallingAnvilState().activeVisuals() == 27,
                "4–10 roster creates one 3×3 group per free participant without the high-roster 25 cap");
    }

    private static List<LandingPoint> grid(double origin) {
        List<LandingPoint> points = new ArrayList<>(25);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                points.add(new LandingPoint(origin + x, 70.0D, origin + z));
            }
        }
        return List.copyOf(points);
    }

    private static List<LandingPoint> gridNine(double origin) {
        List<LandingPoint> points = new ArrayList<>(9);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                points.add(new LandingPoint(origin + x, 70.0D, origin + z));
            }
        }
        return List.copyOf(points);
    }

    private static List<LandingPoint> groupedGridNines(double origin, int groups) {
        List<LandingPoint> points = new ArrayList<>(groups * 9);
        for (int group = 0; group < groups; group++) {
            points.addAll(gridNine(origin + group * 4.0D));
        }
        return List.copyOf(points);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
