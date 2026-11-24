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
