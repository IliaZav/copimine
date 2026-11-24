"""Pure V4 dungeon controller cores: gate block masks and bounded wave planning.

The probe compiles and runs the shipped ``me.copimine.dungeon.domain`` classes,
so a regression in the gate mask bound, the cross-gate overlap test, the secret
classification or the bounded wave scheduler is caught here instead of at
runtime. Every assertion exercises the real production code, nothing mirrored.
"""
from pathlib import Path

import pytest

from tests.dungeon_probe import compile_core, compile_fixture, run_fixture

ROOT = Path(__file__).resolve().parents[1]

CONTROLLER_SOURCE = r'''
import java.util.*;
import me.copimine.dungeon.domain.*;

public class DungeonControllerProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static GateDefinition gate(String id, GateStyle style, Cuboid bounds) {
        return GateDefinition.of(id, "room-a", style, UnlockMode.ON_ROOM_CLEAR,
                "world", bounds, "north", false);
    }

    public static void main(String[] args) {
        // ---- exact, bounded door mask --------------------------------------
        GateDefinition door = gate("g-main", GateStyle.RISING_PORTCULLIS,
                Cuboid.of(10, 64, 10, 10, 66, 11));
        List<int[]> mask = PhysicalGatePolicy.doorMask(door);
        check(mask.size() == 2 * 3, "gate mask must cover the exact door occupancy");
        check(mask.get(0)[0] == 10 && mask.get(0)[1] == 64 && mask.get(0)[2] == 10,
                "mask scan order is stable");
        check(mask.size() == PhysicalGatePolicy.sourceSnapshot(door).size(),
                "the source snapshot restores exactly the door mask");
        check(PhysicalGatePolicy.doorMask(Cuboid.of(3, 4, 5, 3, 4, 5)).size() == 1,
                "a one-block door masks exactly one block");
        boolean rejected = false;
        try {
            PhysicalGatePolicy.doorMask(new Cuboid(0, 0, 0, 100, 100, 100));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "an oversized gate mask must fail closed");

        // ---- overlap checks -------------------------------------------------
        GateDefinition twin = gate("g-twin", GateStyle.SPLIT_SLIDING_WALL,
                Cuboid.of(10, 64, 11, 10, 66, 12));
        GateDefinition far = gate("g-far", GateStyle.LIFTING_STONE_WALL,
                Cuboid.of(40, 64, 40, 40, 66, 41));
        check(PhysicalGatePolicy.overlaps(door, twin), "adjacent gates sharing a block must overlap");
        check(!PhysicalGatePolicy.overlaps(door, far), "separated gates must not overlap");
        check(!PhysicalGatePolicy.overlaps(door, door), "a gate never overlaps itself");
        GateDefinition otherWorld = GateDefinition.of("g-w2", "room-a", GateStyle.LIFTING_STONE_WALL,
                UnlockMode.ON_ROOM_CLEAR, "another-world", Cuboid.of(10, 64, 10, 10, 66, 11), "auto", false);
        check(!PhysicalGatePolicy.overlaps(door, otherWorld),
                "identical geometry in another world never conflicts");
        List<String[]> conflicts = PhysicalGatePolicy.overlapConflicts(List.of(door, twin, far));
        check(conflicts.size() == 1, "exactly one conflicting pair");
        check(conflicts.get(0)[0].equals("g-main") && conflicts.get(0)[1].equals("g-twin"),
                "conflict pair names both gates: " + Arrays.toString(conflicts.get(0)));

        // ---- six templates, six animation ids, one secret -------------------
        Set<String> ids = new LinkedHashSet<>();
        int secrets = 0;
        for (GateStyle style : GateStyle.values()) {
            ids.add(PhysicalGatePolicy.animationId(style));
            if (style.isSecretFamily()) secrets++;
        }
        check(GateStyle.values().length == 6, "there are exactly six gate templates");
        check(ids.size() == 6, "each template has its own animation id");
        check(secrets == 1, "exactly one template is the secret family");
        check(PhysicalGatePolicy.animationId(GateStyle.ASSEMBLING_SEAL).equals("gate.appearing"),
                "animation id follows the style token");
        check(PhysicalGatePolicy.classification(door).equals("public"), "an ordinary gate is public");
        GateDefinition secret = gate("g-secret", GateStyle.SECRET_DISGUISED, Cuboid.of(0, 0, 0, 0, 0, 0));
        check(PhysicalGatePolicy.isSecret(secret), "SECRET_DISGUISED is a secret");
        check(PhysicalGatePolicy.classification(secret).equals("secret"), "a secret is classified secret");

        // ---- an occupied doorway defers closure -----------------------------
        check(PhysicalGatePolicy.occupied(mask, List.of(new int[]{10, 65, 10})),
                "a player in the doorway must defer closure");
        check(!PhysicalGatePolicy.occupied(mask, List.of(new int[]{0, 0, 0})),
                "an empty doorway is safe to close");

        // ---- bounded, deterministic wave planning ---------------------------
        DungeonRoomDefinition twoWave = DungeonRoomDefinition.builder("room-a")
                .scalingMode(ScalingMode.PER_PLAYER)
                .maxConcurrentEnemies(6)
                .addWave(WaveSpec.of(20, EnemyGroup.of("rift_silverfish", 2)))
                .addWave(WaveSpec.of(40, EnemyGroup.of("rift_breeze", 2)))
                .build();
        RoomWaveSchedulePolicy.Plan two = RoomWaveSchedulePolicy.plan(twoWave, 3);
        check(two.waveCount() == 2, "two waves planned");
        check(two.waves().get(0).startTick() == 0, "the first wave starts immediately");
        check(two.waves().get(1).startTick() == 20, "wave start ticks accumulate prior delays");
        check(two.waves().get(1).delayTicks() == 40, "each wave keeps its own configured delay");
        check(two.waves().get(0).enemyCount() == 6, "per-player scaling multiplies by the roster");
        check(!two.truncated(), "a plan exactly at the cap is not truncated");
        check(two.totalEnemies() == 12, "the total counts every scheduled enemy");

        RoomWaveSchedulePolicy.Plan big = RoomWaveSchedulePolicy.plan(twoWave, 50);
        check(big.waves().get(0).enemyCount() == 6, "a huge roster is clamped to the live cap");
        check(big.truncated(), "a clamped plan is flagged truncated");

        DungeonRoomDefinition fixed = DungeonRoomDefinition.builder("room-a")
                .scalingMode(ScalingMode.FIXED).maxConcurrentEnemies(0)
                .addWave(WaveSpec.of(0, EnemyGroup.of("rift_silverfish", 5))).build();
        check(RoomWaveSchedulePolicy.plan(fixed, 20).waves().get(0).enemyCount() == 5,
                "fixed scaling ignores the roster");

        check(RoomWaveSchedulePolicy.plan(twoWave, 3).equals(RoomWaveSchedulePolicy.plan(twoWave, 3)),
                "a replayed/simultaneous activation computes exactly the same plan");

        check(RoomWaveSchedulePolicy.liveCap(0) == RoomWaveSchedulePolicy.MAX_LIVE_CAP,
                "an unset cap uses the ceiling");
        check(RoomWaveSchedulePolicy.liveCap(30) == 30, "a small cap is honoured");
        check(RoomWaveSchedulePolicy.liveCap(5000) == RoomWaveSchedulePolicy.MAX_LIVE_CAP,
                "an oversized cap is clamped to the ceiling");

        System.out.println("PASS dungeon controllers");
    }
}
'''


@pytest.fixture(scope="module")
def core(tmp_path_factory):
    return compile_core(tmp_path_factory.mktemp("dungeon-controllers-core"))


def test_gate_masks_overlaps_and_bounded_wave_scheduling(core, tmp_path):
    classes = compile_fixture(tmp_path, core, CONTROLLER_SOURCE, "DungeonControllerProbe")
    output = run_fixture(classes, "DungeonControllerProbe")
    assert "PASS dungeon controllers" in output


REWARD_SOURCE = r'''
import java.util.*;
import me.copimine.dungeon.domain.*;

public class DungeonRewardProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        DungeonRewardLedger ledger = new DungeonRewardLedger();
        String a = "11111111-1111-1111-1111-111111111111";
        String b = "22222222-2222-2222-2222-222222222222";

        // ---- first grant, then never twice ----------------------------------
        check(ledger.claim("room-a", 1, a, RewardMode.PERSONAL)
                == DungeonRewardLedger.Decision.GRANTED, "the first claim must grant");
        check(ledger.claim("room-a", 1, a, RewardMode.PERSONAL)
                == DungeonRewardLedger.Decision.ALREADY_CLAIMED, "a repeated claim must not pay twice");
        check(ledger.grantedCount() == 1, "only one live grant");
        check(ledger.isGranted("room-a", 1, a), "the grant is recorded");

        // ---- per-player independence ----------------------------------------
        check(ledger.claim("room-a", 1, b, RewardMode.PERSONAL)
                == DungeonRewardLedger.Decision.GRANTED, "another player is paid independently");
        check(ledger.grantedCount() == 2, "two live grants");

        // ---- generation fencing ---------------------------------------------
        check(ledger.latestGeneration("room-a") == 1L, "generation recorded");
        check(ledger.claim("room-a", 0, a, RewardMode.PERSONAL)
                == DungeonRewardLedger.Decision.STALE_GENERATION, "an older generation must be rejected");
        check(ledger.claim("room-a", 2, a, RewardMode.PERSONAL)
                == DungeonRewardLedger.Decision.GRANTED, "a newer generation pays again");
        check(ledger.latestGeneration("room-a") == 2L, "latest generation advances");

        // ---- rollback is exact and once-only --------------------------------
        check(ledger.rollback("room-a", 2, a), "a live grant rolls back");
        check(!ledger.rollback("room-a", 2, a), "a grant rolls back only once");
        check(!ledger.isGranted("room-a", 2, a), "a rolled-back grant is not live");
        check(ledger.claim("room-a", 2, a, RewardMode.PERSONAL)
                == DungeonRewardLedger.Decision.GRANTED, "after recovery the player is paid again");
        check(ledger.grantedCount() == 3, "three live grants after recovery");

        // ---- invalid input never records a receipt --------------------------
        check(ledger.claim("", 3, a, RewardMode.PERSONAL)
                == DungeonRewardLedger.Decision.INVALID, "a blank room is invalid");
        check(ledger.claim("room-a", 3, "  ", RewardMode.PERSONAL)
                == DungeonRewardLedger.Decision.INVALID, "a blank player is invalid");
        check(ledger.claim("room-a", 3, a, null)
                == DungeonRewardLedger.Decision.INVALID, "a null mode is invalid");
        check(ledger.receiptCount() == 3, "invalid attempts add no receipt: " + ledger.receiptCount());

        System.out.println("PASS dungeon rewards");
    }
}
'''


def test_reward_ledger_is_once_only_fenced_and_rollback_safe(core, tmp_path):
    classes = compile_fixture(tmp_path, core, REWARD_SOURCE, "DungeonRewardProbe")
    output = run_fixture(classes, "DungeonRewardProbe")
    assert "PASS dungeon rewards" in output


MOB_SOURCE = r'''
import java.util.*;
import me.copimine.dungeon.domain.*;

public class DungeonMobProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(DungeonMobRegistry.newSpeciesCount() == 3, "exactly three new species");

        Set<DungeonMobRegistry.Ability> silverfish =
                DungeonMobRegistry.profile(MobTemplate.RIFT_SILVERFISH).abilities();
        Set<DungeonMobRegistry.Ability> breeze =
                DungeonMobRegistry.profile(MobTemplate.RIFT_BREEZE).abilities();
        Set<DungeonMobRegistry.Ability> bogged =
                DungeonMobRegistry.profile(MobTemplate.RIFT_BOGGED).abilities();
        check(silverfish.contains(DungeonMobRegistry.Ability.BURROW)
                && silverfish.contains(DungeonMobRegistry.Ability.LEAP),
                "silverfish burrows and leaps");
        check(!silverfish.contains(DungeonMobRegistry.Ability.POISON_BOLT),
                "silverfish must not borrow another species signature");
        check(breeze.contains(DungeonMobRegistry.Ability.WIND_PROJECTILE), "breeze casts wind");
        check(bogged.contains(DungeonMobRegistry.Ability.POISON_BOLT), "bogged casts poison");
        check(!silverfish.equals(breeze) && !breeze.equals(bogged) && !bogged.equals(silverfish),
                "the three species have distinct capability sets");

        for (MobTemplate template : MobTemplate.values()) {
            check(DungeonMobRegistry.isDerivedFromVanilla(template),
                    "template must derive from a vanilla base type: " + template.id());
            DungeonMobRegistry.Profile profile = DungeonMobRegistry.profile(template);
            check(profile.template() == template, "profile round-trips the template");
            check(profile.aggroRange() >= 1
                            && profile.aggroRange() <= DungeonMobRegistry.MAX_AGGRO_RANGE,
                    "aggro range bounded for " + template.id());
        }

        boolean rejected = false;
        try {
            new DungeonMobRegistry.Profile(MobTemplate.RIFT_GIANT,
                    Set.of(DungeonMobRegistry.Ability.MELEE), DungeonMobRegistry.MAX_AGGRO_RANGE + 1);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "an over-limit aggro range must fail closed");

        System.out.println("PASS dungeon mobs");
    }
}
'''


def test_mob_registry_keeps_three_species_with_bounded_abilities(core, tmp_path):
    classes = compile_fixture(tmp_path, core, MOB_SOURCE, "DungeonMobProbe")
    output = run_fixture(classes, "DungeonMobProbe")
    assert "PASS dungeon mobs" in output


GATE_TRANSACTION_SOURCE = r'''
import java.util.*;
import me.copimine.dungeon.domain.*;

public class DungeonGateTransactionProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static final class FakeWorld implements PhysicalGateTransaction.GateWorld {
        final Map<String, String> blocks = new HashMap<>();
        boolean loaded = true;
        public boolean chunkLoaded(int x, int y, int z) { return loaded; }
        public String blockKey(int x, int y, int z) {
            return blocks.getOrDefault(x + "," + y + "," + z, "minecraft:stone");
        }
        public void setBlock(int x, int y, int z, String key) {
            blocks.put(x + "," + y + "," + z, key);
        }
        boolean allAir(Cuboid bounds) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++)
                for (int y = bounds.minY(); y <= bounds.maxY(); y++)
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                        if (!PhysicalGateTransaction.AIR.equals(blocks.get(x + "," + y + "," + z)))
                            return false;
            return true;
        }
        boolean allStone(Cuboid bounds) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++)
                for (int y = bounds.minY(); y <= bounds.maxY(); y++)
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                        if (!"minecraft:stone".equals(blocks.get(x + "," + y + "," + z)))
                            return false;
            return true;
        }
    }

    public static void main(String[] args) {
        Cuboid bounds = Cuboid.of(10, 64, 10, 10, 66, 11);
        GateDefinition door = GateDefinition.of("g-main", "room-a", GateStyle.RISING_PORTCULLIS,
                UnlockMode.ON_ROOM_CLEAR, "world", bounds, "north", false);
        FakeWorld world = new FakeWorld();
        PhysicalGateTransaction tx = new PhysicalGateTransaction(door, world);
        check(tx.state() == GateState.CLOSED, "a gate starts with its declared state");
        check(tx.blockCount() == 6, "the transaction owns the exact door mask");
        check(tx.animationId().equals("gate.grille"), "the gate keeps its style animation id");

        // ---- open snapshots every original block, then replaces with air ----
        check(tx.open() == PhysicalGateTransaction.Result.APPLIED, "open must apply");
        check(tx.journaled(), "opening keeps a durable journal");
        check(tx.journalKeys().size() == 6, "journal covers every doorway cell");
        check(world.allAir(bounds), "the whole doorway becomes air when open");
        check(tx.state() == GateState.OPEN, "state is OPEN");
        check(tx.open() == PhysicalGateTransaction.Result.ALREADY_OPEN, "double open is idempotent");

        // ---- a blocked doorway defers closure, never entombs ----
        List<int[]> occupied = List.of(new int[]{10, 65, 10});
        check(tx.close(occupied) == PhysicalGateTransaction.Result.BLOCKED_BY_ENTITY,
                "an occupied doorway defers closure");
        check(tx.state() == GateState.BLOCKED_BY_ENTITY, "state records the deferral");
        check(world.allAir(bounds), "a deferred close leaves the doorway open");

        // ---- a clear doorway restores the original builder blocks ----
        check(tx.close(List.of()) == PhysicalGateTransaction.Result.APPLIED, "an empty doorway closes");
        check(world.allStone(bounds), "close restores the exact snapshot");
        check(!tx.journaled(), "a clean close consumes the journal");
        check(tx.state() == GateState.CLOSED, "state is CLOSED");
        check(tx.close(List.of()) == PhysicalGateTransaction.Result.ALREADY_CLOSED,
                "double close is idempotent");

        // ---- restart recovery is journal driven ----
        check(tx.open() == PhysicalGateTransaction.Result.APPLIED, "reopen applies");
        check(tx.recover() == PhysicalGateTransaction.Result.APPLIED, "recover restores the journal");
        check(world.allStone(bounds), "recover restores every original block");
        check(tx.recover() == PhysicalGateTransaction.Result.ALREADY_CLOSED,
                "recover without a journal on a closed gate is safe");

        // ---- an unloaded chunk is refused, not written blind ----
        FakeWorld unloaded = new FakeWorld();
        unloaded.loaded = false;
        PhysicalGateTransaction blocked = new PhysicalGateTransaction(door, unloaded);
        check(blocked.open() == PhysicalGateTransaction.Result.CHUNK_UNLOADED,
                "an unloaded chunk must not be mutated");
        check(blocked.state() == GateState.RECOVERY_REQUIRED, "the refusal is explicit");

        // ---- an initially-open gate captured a snapshot at construction ----
        GateDefinition openGate = GateDefinition.of("g-open", "room-a",
                GateStyle.ASSEMBLING_SEAL, UnlockMode.ON_ROOM_CLEAR, "world", bounds, "auto", true);
        FakeWorld openWorld = new FakeWorld();
        PhysicalGateTransaction alwaysOpen = new PhysicalGateTransaction(openGate, openWorld);
        check(alwaysOpen.journaled(), "an initially-open gate snapshots immediately");
        check(alwaysOpen.close(List.of()) == PhysicalGateTransaction.Result.APPLIED,
                "an initially-open gate can be closed");
        check(openWorld.allStone(bounds), "closing it restores the captured blocks");

        System.out.println("PASS dungeon gate transaction");
    }
}
'''


def test_gate_transaction_snapshots_defers_and_restores(core, tmp_path):
    classes = compile_fixture(tmp_path, core, GATE_TRANSACTION_SOURCE, "DungeonGateTransactionProbe")
    output = run_fixture(classes, "DungeonGateTransactionProbe")
    assert "PASS dungeon gate transaction" in output


ENCOUNTER_PLAN_SOURCE = r'''
import java.util.*;
import me.copimine.dungeon.domain.*;

public class DungeonEncounterPlanProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static SpawnAnchor anchor(String id, String room, int x, int y, int z) {
        return SpawnAnchor.of(id, room, "world", x, y, z, 1.0D);
    }

    public static void main(String[] args) {
        DungeonRoomDefinition room = DungeonRoomDefinition.builder("room-a")
                .world("world")
                .scalingMode(ScalingMode.PER_PLAYER)
                .maxConcurrentEnemies(6)
                .addWave(WaveSpec.of(0,
                        EnemyGroup.of("rift_silverfish", 2),
                        EnemyGroup.of("rift_breeze", 1)))
                .addWave(WaveSpec.of(20, EnemyGroup.of("rift_bogged", 2)))
                .build();
        List<SpawnAnchor> anchors = List.of(
                anchor("s-b", "room-a", 3, 64, 3),
                anchor("s-a", "room-a", 1, 64, 1),
                anchor("s-foreign", "room-b", 9, 64, 9));

        DungeonEncounterPlanPolicy.Plan plan =
                DungeonEncounterPlanPolicy.plan(room, anchors, 3);
        check(plan.viable(), "a fully configured room is viable: " + plan.reason());
        check(plan.waveCount() == 2, "both waves are planned");
        check(plan.liveCap() == 6, "the live cap is honoured");
        check(plan.eligibleParticipants() == 3, "the roster is recorded");
        check(plan.truncated(), "a capped wave reports truncation");
        check(plan.totalAssignments() == 12,
                "assignments match the bounded schedule: " + plan.totalAssignments());

        // The clamped wave keeps the configured 2:1 proportion (4 + 2), and the
        // foreign-room anchor is never used.
        List<DungeonEncounterPlanPolicy.Assignment> first = plan.wave(0);
        check(first.size() == 6, "wave 0 holds six bounded enemies");
        int silver = 0;
        int breeze = 0;
        for (DungeonEncounterPlanPolicy.Assignment a : first) {
            if (a.template() == MobTemplate.RIFT_SILVERFISH) silver++;
            if (a.template() == MobTemplate.RIFT_BREEZE) breeze++;
            check(!a.anchorId().equals("s-foreign"), "a foreign-room anchor is ignored");
        }
        check(silver == 4 && breeze == 2, "the clamped wave preserves 2:1 proportion");
        check(first.get(0).ordinalInWave() == 0, "ordinals start at zero");
        check(first.get(0).anchorId().equals("s-a"), "anchors are used in stable id order");

        // Determinism: same inputs, same plan.
        check(plan.equals(DungeonEncounterPlanPolicy.plan(room, anchors, 3)),
                "a replayed activation computes exactly the same plan");

        // Fail closed without anchors or waves.
        DungeonEncounterPlanPolicy.Plan noAnchors =
                DungeonEncounterPlanPolicy.plan(room, List.of(), 3);
        check(!noAnchors.viable(), "a room without anchors is not viable");
        check(noAnchors.totalAssignments() == 0, "an unviable plan assigns nothing");

        DungeonRoomDefinition empty = DungeonRoomDefinition.builder("room-empty")
                .world("world").build();
        check(!DungeonEncounterPlanPolicy.plan(empty, anchors, 3).viable(),
                "a room without waves is not viable");

        // FIXED scaling ignores the roster.
        DungeonRoomDefinition fixed = DungeonRoomDefinition.builder("room-a")
                .world("world").scalingMode(ScalingMode.FIXED)
                .addWave(WaveSpec.of(0, EnemyGroup.of("rift_silverfish", 2)))
                .build();
        DungeonEncounterPlanPolicy.Plan fixedPlan =
                DungeonEncounterPlanPolicy.plan(fixed, anchors, 20);
        check(fixedPlan.totalAssignments() == 2, "fixed scaling ignores the roster");

        System.out.println("PASS dungeon encounter plan");
    }
}
'''


def test_encounter_plan_is_deterministic_bounded_and_fails_closed(core, tmp_path):
    classes = compile_fixture(tmp_path, core, ENCOUNTER_PLAN_SOURCE, "DungeonEncounterPlanProbe")
    output = run_fixture(classes, "DungeonEncounterPlanProbe")
    assert "PASS dungeon encounter plan" in output


LIFECYCLE_SOURCE = r'''
import java.util.*;
import me.copimine.dungeon.domain.*;

public class DungeonLifecycleProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static final class FakePorts implements DungeonRoomLifecycle.Ports {
        int live;
        boolean sealOk = true;
        boolean openOk = true;
        boolean rewardOk = true;
        boolean sealed;
        boolean opened;
        int spawnCalls;
        int rewardCalls;
        final List<Integer> spawnedWaves = new ArrayList<>();

        public boolean sealEntrance() { sealed = true; return sealOk; }
        public boolean openExit() { opened = true; return openOk; }
        public int spawnWave(int index, List<DungeonEncounterPlanPolicy.Assignment> assignments) {
            spawnCalls++;
            spawnedWaves.add(index);
            live += assignments.size();
            return assignments.size();
        }
        public int liveEnemies() { return live; }
        public boolean grantReward() { rewardCalls++; return rewardOk; }
        public void report(String message) { }
    }

    static DungeonRoomDefinition room() {
        return DungeonRoomDefinition.builder("room-a").world("world")
                .addWave(WaveSpec.of(20, EnemyGroup.of("rift_silverfish", 1)))
                .addWave(WaveSpec.of(0, EnemyGroup.of("rift_breeze", 1)))
                .build();
    }

    static DungeonRoomLifecycle lifecycle(FakePorts ports) {
        DungeonRoomDefinition room = room();
        List<SpawnAnchor> anchors =
                List.of(SpawnAnchor.of("s-a", "room-a", "world", 1, 64, 1, 1.0D));
        DungeonEncounterPlanPolicy.Plan plan =
                DungeonEncounterPlanPolicy.plan(room, anchors, 1);
        check(plan.viable(), "test room must be viable");
        return new DungeonRoomLifecycle(room, plan, ports);
    }

    public static void main(String[] args) {
        FakePorts ports = new FakePorts();
        DungeonRoomLifecycle life = lifecycle(ports);
        check(life.state() == RoomState.PUBLISHED_IDLE, "a room starts idle");

        // ---- activation seals, bumps generation and spawns the first wave ----
        check(life.tryActivate(1, 0) == DungeonRoomLifecycle.Signal.FIGHTING,
                "a valid activation fights");
        check(life.state() == RoomState.FIGHTING, "state is FIGHTING");
        check(ports.sealed, "activation seals the entrance");
        check(life.generation() == 1, "a fresh generation starts");
        check(ports.spawnCalls == 1, "wave 0 spawns immediately");
        check(ports.live == 1, "one live enemy");

        // ---- a simultaneous second trigger is rejected ----
        check(life.tryActivate(5, 1) == DungeonRoomLifecycle.Signal.REJECTED,
                "a duplicate trigger cannot stand up a second controller");
        check(life.generation() == 1, "the rejected trigger does not bump generation");

        // ---- do not clear while a later wave is still pending ----
        ports.live = 0;
        check(life.advance(5) == DungeonRoomLifecycle.Signal.FIGHTING,
                "no clear while wave 1 is pending");
        check(life.state() == RoomState.FIGHTING, "still fighting");

        // ---- the delayed wave spawns on schedule ----
        check(life.advance(20) == DungeonRoomLifecycle.Signal.FIGHTING, "wave 1 spawns at tick 20");
        check(ports.spawnCalls == 2, "exactly two waves spawned");
        check(ports.spawnedWaves.equals(List.of(0, 1)), "waves spawn in order");
        check(ports.live == 1, "wave 1 is live");

        // ---- a real clear commits, opens the exit and requests reward ----
        ports.live = 0;
        check(life.advance(21) == DungeonRoomLifecycle.Signal.OPENED, "a real clear opens the exit");
        check(life.state() == RoomState.REWARD_READY, "state is REWARD_READY");
        check(ports.opened, "the exit gate really opened");
        check(ports.rewardCalls == 0, "the reward is not requested before REWARD_READY advance");

        // ---- reward once, then reset to idle ----
        check(life.advance(22) == DungeonRoomLifecycle.Signal.REWARDED, "reward then reset");
        check(ports.rewardCalls == 1, "reward requested exactly once");
        check(life.state() == RoomState.PUBLISHED_IDLE, "room is idle again");
        check(life.advance(23) == DungeonRoomLifecycle.Signal.IDLE, "idle advance does nothing");
        check(ports.rewardCalls == 1, "idle advance never re-requests the reward");

        // ---- re-arm with a new generation ----
        check(life.tryActivate(1, 30) == DungeonRoomLifecycle.Signal.FIGHTING, "re-arm works");
        check(life.generation() == 2, "re-arm uses a new generation");

        // ---- a failed seal aborts safely and never fights ----
        FakePorts failed = new FakePorts();
        failed.sealOk = false;
        DungeonRoomLifecycle aborted = lifecycle(failed);
        check(aborted.tryActivate(1, 0) == DungeonRoomLifecycle.Signal.ABORTED,
                "a failed seal aborts");
        check(aborted.state() == RoomState.PUBLISHED_IDLE, "an aborted room returns to idle");

        // ---- aborting an in-flight room opens the exit and cleans up ----
        FakePorts midRun = new FakePorts();
        DungeonRoomLifecycle running = lifecycle(midRun);
        running.tryActivate(1, 0);
        check(running.abort() == DungeonRoomLifecycle.Signal.ABORTED, "abort works");
        check(midRun.opened, "aborting opens the exit so nobody is trapped");
        check(running.state() == RoomState.PUBLISHED_IDLE, "aborted room is idle");
        check(running.abort() == DungeonRoomLifecycle.Signal.REJECTED,
                "aborting an idle room is a safe rejection");

        // ---- an unviable room never starts ----
        FakePorts noSpawn = new FakePorts();
        DungeonRoomDefinition bare = DungeonRoomDefinition.builder("room-a").world("world")
                .addWave(WaveSpec.of(0, EnemyGroup.of("rift_silverfish", 1))).build();
        DungeonEncounterPlanPolicy.Plan unviable =
                DungeonEncounterPlanPolicy.plan(bare, List.of(), 1);
        DungeonRoomLifecycle never = new DungeonRoomLifecycle(bare, unviable, noSpawn);
        check(never.tryActivate(1, 0) == DungeonRoomLifecycle.Signal.REJECTED,
                "a room without anchors must not start");

        System.out.println("PASS dungeon room lifecycle");
    }
}
'''


def test_room_lifecycle_is_generation_fenced_and_reward_once(core, tmp_path):
    classes = compile_fixture(tmp_path, core, LIFECYCLE_SOURCE, "DungeonLifecycleProbe")
    output = run_fixture(classes, "DungeonLifecycleProbe")
    assert "PASS dungeon room lifecycle" in output
