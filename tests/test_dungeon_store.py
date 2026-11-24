"""Durable dungeon definition store: atomic save, versioned schema, dry-run import.

Runs the real ``DungeonDefinitionStore`` against a real temporary directory, so
the atomic write, the schema header, the Russian validation text, the
export/import ``--dry-run`` contract and draft undo/redo are all exercised as
shipped.
"""
from pathlib import Path

import pytest

from tests.dungeon_probe import compile_core, compile_fixture, run_fixture

ROOT = Path(__file__).resolve().parents[1]

STORE_SOURCE = r'''
import java.nio.file.*;
import java.util.*;
import me.copimine.dungeon.domain.*;
import me.copimine.dungeon.store.*;

public class DungeonStoreProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static DungeonRoomDefinition room(String id, String world, Cuboid bounds) {
        return DungeonRoomDefinition.builder(id)
                .displayName(id)
                .world(world)
                .bounds(bounds)
                .scalingMode(ScalingMode.FIXED)
                .maxConcurrentEnemies(8)
                .addWave(new WaveSpec(List.of(new EnemyGroup(MobTemplate.RIFT_BREEZE, 3)), 20))
                .reward(RewardSpec.personal("dungeon_common"))
                .build();
    }

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args[0]);

        // ---- atomic save + round trip --------------------------------------
        DungeonDefinitionStore store = DungeonDefinitionStore.empty();
        store.applyEdit(catalog -> {
            catalog.putRoom(room("rose", "world", Cuboid.of(0, 0, 0, 10, 5, 10)));
            catalog.putGate(new GateDefinition("rose_gate", "rose", GateStyle.ASSEMBLING_SEAL,
                    UnlockMode.ON_ROOM_CLEAR, "world", Cuboid.of(0, 1, 0, 0, 3, 2), "auto", false));
            catalog.putSecret(new SecretDefinition("rose_secret", "rose", GateStyle.SECRET_DISGUISED,
                    UnlockMode.MANUAL_AFTER_ROOM_CLEAR, "world", Cuboid.of(1, 1, 1, 1, 3, 3), "rune"));
            catalog.putSpawn(new SpawnAnchor("rose_spawn", "rose", "world", 5, 1, 5, 1.0D));
        });

        Path file = dir.resolve("dungeons.tsv");
        store.save(file);
        check(Files.isRegularFile(file), "save must write the definitions file");
        check(!Files.exists(dir.resolve("dungeons.tsv.tmp")),
                "the atomic temp file must be moved away, not left behind");
        String text = Files.readString(file);
        check(text.startsWith("CMDUNGEON\t1\n"), "the schema-version header is mandatory");

        DungeonDefinitionStore reloaded = DungeonDefinitionStore.load(file);
        check(reloaded.loadProblems().isEmpty(), "clean file must reload without problems: "
                + reloaded.loadProblems());
        check(reloaded.catalog().room("rose") != null, "room did not survive the round trip");
        check(reloaded.catalog().gate("rose_gate") != null, "gate did not survive the round trip");
        check(reloaded.catalog().secret("rose_secret") != null, "secret did not survive the round trip");
        check(reloaded.catalog().spawn("rose_spawn") != null, "spawn did not survive the round trip");
        check(reloaded.serialize().equals(text), "serialization must be deterministic");

        // ---- Russian validation text ---------------------------------------
        DungeonDefinitionStore broken = DungeonDefinitionStore.empty();
        broken.applyEdit(catalog -> catalog.putRoom(DungeonRoomDefinition.builder("broken")
                .world("world")
                .bounds(Cuboid.of(0, 0, 0, 4, 4, 4))
                .build()));
        List<String> problems = broken.validate(null);
        check(!problems.isEmpty(), "a room without waves must report problems");
        check(problems.stream().anyMatch(p -> p.contains("Комната 'broken'")),
                "problems must name the room: " + problems);
        check(problems.stream().anyMatch(p -> p.contains("не задан")),
                "validation text must be Russian: " + problems);

        // ---- export / import --dry-run never mutates ------------------------
        Path export = dir.resolve("export.tsv");
        store.export(export);
        String before = store.serialize();
        ImportResult dry = store.importFile(export, true);
        check(dry.isClean(), "a valid export must dry-run clean: " + dry.problems());
        check(!dry.applied(), "a dry-run must never apply");
        check(dry.importedDefinitions() > 0, "a dry-run still reports how many definitions it saw");
        check(store.serialize().equals(before), "a dry-run must never mutate the live catalogue");

        // ---- a confirmed import replaces the catalogue ----------------------
        DungeonDefinitionStore target = DungeonDefinitionStore.empty();
        ImportResult applied = target.importFile(export, false);
        check(applied.applied() && applied.isClean(), "a clean confirmed import applies: "
                + applied.problems());
        check(target.catalog().room("rose") != null, "imported room is missing");

        // ---- rejected imports keep the Russian error ------------------------
        ImportResult badHeader = target.importText("NOT_A_HEADER\t1\n", false);
        check(!badHeader.applied() && badHeader.hasProblems(), "a bad header must be rejected");
        check(badHeader.problems().get(0).contains("Заголовок"),
                "header rejection must be Russian: " + badHeader.problems());

        ImportResult newer = target.importText("CMDUNGEON\t99\n", false);
        check(!newer.applied() && newer.hasProblems(), "a newer schema must fail closed");
        check(newer.problems().get(0).contains("новее"),
                "newer-schema rejection must be Russian: " + newer.problems());

        // ---- draft undo / redo ---------------------------------------------
        DungeonDefinitionStore history = DungeonDefinitionStore.empty();
        check(!history.canUndo(), "a fresh store has nothing to undo");
        history.applyEdit(catalog -> catalog.putRoom(room("alpha", "world", Cuboid.of(0, 0, 0, 2, 2, 2))));
        check(history.catalog().room("alpha") != null, "the edit must apply");
        check(history.canUndo(), "the edit must be undoable");
        check(history.undo(), "undo must report success");
        check(history.catalog().room("alpha") == null, "undo must remove the edit");
        check(history.canRedo(), "undo must enable redo");
        check(history.redo(), "redo must report success");
        check(history.catalog().room("alpha") != null, "redo must restore the edit");

        System.out.println("PASS dungeon store");
    }
}
'''


@pytest.fixture(scope="module")
def core(tmp_path_factory):
    return compile_core(tmp_path_factory.mktemp("dungeon-core-store"))


def test_store_atomic_schema_validation_and_dry_run(core, tmp_path):
    classes = compile_fixture(tmp_path, core, STORE_SOURCE, "DungeonStoreProbe")
    output = run_fixture(classes, "DungeonStoreProbe", str(tmp_path))
    assert "PASS dungeon store" in output
