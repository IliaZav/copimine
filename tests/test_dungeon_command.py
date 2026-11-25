"""Full ``/cmdungeon`` contract: dispatch tree, usage, completion, permissions.

The probe drives the real ``DungeonCommandDispatcher`` / ``CommandRegistry`` (the
same objects the Bukkit executor delegates to), so every documented sub-command,
the unknown-subcommand usage hint, tab completion over real registered ids and
the "op alone is not enough" rule are proven against shipped code.
"""
from pathlib import Path

import pytest

from tests.dungeon_probe import compile_core, compile_fixture, run_fixture

ROOT = Path(__file__).resolve().parents[1]

COMMAND_SOURCE = r'''
import java.util.*;
import me.copimine.dungeon.domain.*;
import me.copimine.dungeon.store.*;
import me.copimine.dungeon.editor.*;
import me.copimine.dungeon.command.*;

public class DungeonCommandProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static final UUID BUILDER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID OP_ONLY = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    static DungeonRoomDefinition room(String id, Cuboid bounds) {
        return DungeonRoomDefinition.builder(id)
                .displayName(id)
                .world("world")
                .bounds(bounds)
                .trigger(bounds)
                .scalingMode(ScalingMode.FIXED)
                .maxConcurrentEnemies(8)
                .addWave(new WaveSpec(List.of(new EnemyGroup(MobTemplate.RIFT_BREEZE, 2)), 20))
                .reward(RewardSpec.personal("dungeon_common"))
                .build();
    }

    static DungeonDefinitionStore seeded() {
        DungeonDefinitionStore store = DungeonDefinitionStore.empty();
        store.applyEdit(catalog -> {
            catalog.putRoom(room("rose", Cuboid.of(0, 0, 0, 20, 10, 20)));
            catalog.putRoom(room("tulip", Cuboid.of(30, 0, 30, 40, 10, 40)));
            catalog.putGate(new GateDefinition("rose_gate", "rose", GateStyle.ASSEMBLING_SEAL,
                    UnlockMode.ON_ROOM_CLEAR, "world", Cuboid.of(0, 1, 0, 0, 3, 2), "auto", false));
            catalog.putSecret(new SecretDefinition("rose_secret", "rose", GateStyle.SECRET_DISGUISED,
                    UnlockMode.MANUAL_AFTER_ROOM_CLEAR, "world", Cuboid.of(1, 1, 1, 1, 3, 3), "rune"));
            catalog.putSpawn(new SpawnAnchor("rose_spawn", "rose", "world", 5, 1, 5, 1.0D));
            catalog.putRoom(catalog.room("rose").toBuilder()
                    .addGateId("rose_gate")
                    .addSecretId("rose_secret")
                    .addSpawnAnchorId("rose_spawn")
                    .build());
        });
        return store;
    }

    static final class Rig {
        final DungeonDefinitionStore store = seeded();
        final EditorSessionManager sessions = new EditorSessionManager();
        final AuditLog audit = new AuditLog();
        final DungeonCommandDispatcher dispatcher =
                new DungeonCommandDispatcher(store, sessions, audit);
        final CommandActor builder = CommandActor.player(BUILDER, "Builder", "world", 10, 65, 10,
                Set.of(EditorPermissions.ADMIN), false);

        Rig() {
            dispatcher.setPublicationGateway((id, enable) -> check(id.equals("rose"), "wrong publication owner"));
            EditorSession session = sessions.open(BUILDER, "Builder");
            session.setWorld("world");
            session.setCorner1(1, 2, 3);
            session.setCorner2(4, 5, 6);
            session.selectRoom("rose");
        }

        CommandResponse run(String line) {
            return dispatcher.execute(builder, List.of(line.split(" ")));
        }
    }

    public static void main(String[] args) {
        String scenario = args.length == 0 ? "tree" : args[0];
        switch (scenario) {
            case "tree" -> tree();
            case "unknown" -> unknown();
            case "complete" -> complete();
            case "permissions" -> permissions();
            case "wand" -> wand();
            default -> throw new AssertionError("unknown scenario " + scenario);
        }
    }

    static void tree() {
        Rig rig = new Rig();
        String[][] commands = {
                {"help", "help"},
                {"status", "status"},
                {"wand give builder_one", "wand give"},
                {"editor grant builder_one scope", "editor grant"},
                {"editor revoke builder_one", "editor revoke"},
                {"editor list", "editor list"},
                {"editor mode gate", "editor mode"},
                {"editor wand builder_one", "editor wand"},
                {"pos1", "pos1"},
                {"pos2", "pos2"},
                {"select rose", "select"},
                {"create daisy", "create"},
                {"inspect rose", "inspect"},
                {"room list nearby", "room list nearby"},
                {"room list all", "room list all"},
                {"room info rose", "room info"},
                {"room edit rose", "room edit"},
                {"room validate rose", "room validate"},
                {"room preview rose", "room preview"},
                {"room publish rose", "room publish"},
                {"room disable rose", "room disable"},
                {"room reset rose confirm", "room reset confirm"},
                {"room test rose", "room test"},
                {"room test rose solo", "room test solo"},
                {"room test rose party", "room test party"},
                {"room export rose", "room export"},
                {"room import rooms.tsv --dry-run", "room import --dry-run"},
                {"room import rooms.tsv confirm", "room import confirm"},
                {"room undo rose", "room undo"},
                {"room redo rose", "room redo"},
                {"room show rose 30", "room show"},
                {"overlay on", "overlay on"},
                {"overlay off", "overlay off"},
                {"overlay filter rooms", "overlay filter rooms"},
                {"overlay filter gates", "overlay filter gates"},
                {"overlay filter secrets", "overlay filter secrets"},
                {"overlay filter all", "overlay filter all"},
                {"overlay range 48", "overlay range"},
                {"gate add rose_gate2", "gate add"},
                {"gate style rose_gate appearing", "gate style"},
                {"gate unlock rose_gate manual_after_clear", "gate unlock"},
                {"gate bind rose_gate rose", "gate bind"},
                {"gate direction rose_gate auto", "gate direction"},
                {"gate preview rose_gate open", "gate preview open"},
                {"gate preview rose_gate close", "gate preview close"},
                {"gate validate rose_gate", "gate validate"},
                {"gate status rose_gate", "gate status"},
                {"secret add rose_secret2", "secret add"},
                {"secret style rose_secret secret", "secret style"},
                {"secret unlock rose_secret milestone", "secret unlock"},
                {"spawn add rose_spawn2 rift_breeze", "spawn add"},
                {"spawn list rose", "spawn list"},
                {"spawn remove rose_spawn", "spawn remove"},
                {"mobs add rose rift_breeze 5", "mobs add"},
                {"mobs scaling rose adaptive", "mobs scaling"},
                {"mobs concurrent-cap rose 10", "mobs concurrent-cap"},
                {"wave add rose", "wave add"},
                {"wave delay rose 0 40", "wave delay"},
                {"reward mode rose drops", "reward mode"},
                {"reward anchor rose", "reward anchor"},
                {"reward loot rose rose_loot", "reward loot"},
                {"audit rose", "audit"},
                {"audit rose recent", "audit recent"},
        };
        CommandRegistry registry = rig.dispatcher.registry();
        for (String[] entry : commands) {
            CommandRegistry.Route route = registry.resolve(List.of(entry[0].split(" ")));
            check(route.matched(), "did not resolve: /cmdungeon " + entry[0]
                    + " -> " + route.problem());
            check(route.canonicalPath().equals(entry[1]),
                    "/cmdungeon " + entry[0] + " resolved to '" + route.canonicalPath()
                            + "', expected '" + entry[1] + "'");
            CommandResponse response = rig.run(entry[0]);
            check(response.success(), "/cmdungeon " + entry[0] + " failed: " + response.lines());
        }
        check(registry.usageLines().size() >= 60,
                "documented tree is incomplete: " + registry.usageLines().size() + " usage lines");
        check(registry.usageLines().contains("/cmdungeon room reset <roomId> confirm"),
                "usage rendering lost the reset confirm form");
        check(registry.usageLines().contains("/cmdungeon gate preview <gateId> open"),
                "usage rendering lost the gate preview form");

        CommandResponse publish = rig.run("room publish rose");
        check(!publish.success(), "publication must reject an incomplete second wave");
        check(rig.store.catalog().room("rose") != null, "a refused publication preserves the draft");

        CommandResponse reset = rig.run("room reset rose confirm");
        check(reset.success(), "reset dry-run failed: " + reset.lines());
        check(String.join("\n", reset.lines()).contains("DRY-RUN"),
                "reset must emit a dry-run receipt");

        System.out.println("PASS dungeon command tree");
    }

    static void unknown() {
        Rig rig = new Rig();
        CommandResponse root = rig.run("creat");
        check(!root.success(), "a root typo must fail");
        String rootText = String.join("\n", root.lines());
        check(rootText.contains("Использование: /cmdungeon"),
                "unknown subcommand must print usage: " + rootText);
        check(rootText.contains("create"), "unknown subcommand must suggest the nearest command: " + rootText);

        CommandResponse deep = rig.run("room inf rose");
        check(!deep.success(), "a deep typo must fail");
        String deepText = String.join("\n", deep.lines());
        check(deepText.contains("info"), "deep typo must suggest info: " + deepText);
        check(deepText.contains("/cmdungeon room"), "deep usage must show the room prefix: " + deepText);

        System.out.println("PASS dungeon unknown usage and suggestion");
    }

    static void complete() {
        Rig rig = new Rig();
        CommandRegistry registry = rig.dispatcher.registry();
        List<String> root = registry.complete(List.of());
        check(root.contains("create") && root.contains("gate") && root.contains("room"),
                "root completion must offer the documented subcommands: " + root);
        check(registry.complete(List.of("room", "validate", "ro")).equals(List.of("rose")),
                "room id completion must offer real ids and filter the prefix: "
                        + registry.complete(List.of("room", "validate", "ro")));
        check(registry.complete(List.of("gate", "style", "rose_gate", "app")).contains("appearing"),
                "gate style completion must offer the enumerated tokens");
        check(registry.complete(List.of("overlay", "filter", "")).containsAll(
                        List.of("rooms", "gates", "secrets", "all")),
                "overlay filter completion must offer every filter");
        check(registry.complete(List.of("editor", "mode", "")).containsAll(
                        List.of("room", "gate", "secret", "spawn", "reward", "rest", "portal")),
                "editor mode completion must offer the enumerated modes");
        check(registry.complete(List.of("mobs", "add", "rose", "rif")).contains("rift_breeze"),
                "mob template completion must offer real templates");
        check(registry.complete(List.of("spawn", "remove", "")).equals(List.of("rose_spawn")),
                "spawn completion must offer only registered spawn ids");
        check(registry.complete(List.of("gate", "add", "")).isEmpty(),
                "a brand-new gate id must not be completed from existing ids");

        System.out.println("PASS dungeon tab completion");
    }

    static void permissions() {
        Rig rig = new Rig();
        CommandActor opOnly = CommandActor.player(OP_ONLY, "OpOnly", "world", 0, 64, 0,
                Set.of(), true);
        CommandResponse deniedCreate = rig.dispatcher.execute(opOnly, List.of("create", "daisy"));
        check(!deniedCreate.success(), "op alone must not create a room");
        check(String.join("\n", deniedCreate.lines()).contains("Недостаточно прав"),
                "denial must be Russian: " + deniedCreate.lines());

        CommandActor builderOnly = CommandActor.player(OP_ONLY, "BuilderOnly", "world", 0, 64, 0,
                Set.of(EditorPermissions.BUILDER), false);
        CommandResponse deniedPublish = rig.dispatcher.execute(builderOnly,
                List.of("room", "publish", "rose"));
        check(!deniedPublish.success(), "builder must not publish");
        CommandResponse allowedStatus = rig.dispatcher.execute(builderOnly, List.of("status"));
        check(allowedStatus.success(), "builder must be allowed to read status: " + allowedStatus.lines());

        CommandActor console = new CommandActor(null, "CONSOLE", true, null, 0, 0, 0, false,
                Set.of(EditorPermissions.ADMIN, EditorPermissions.BUILDER), true);
        CommandResponse consolePos = rig.dispatcher.execute(console, List.of("pos1"));
        check(!consolePos.success(), "pos1 must be player-only");
        check(String.join("\n", consolePos.lines()).contains("только игроку"),
                "console rejection must be Russian: " + consolePos.lines());

        CommandRegistry registry = rig.dispatcher.registry();
        CommandRegistry.Route reset = registry.resolve(
                List.of("room", "reset", "rose", "confirm"));
        check(reset.matched() && reset.requiresConfirm(),
                "reset must require an explicit confirm token");
        check(!registry.resolve(List.of("room", "publish", "rose")).worldMutating(),
                "publication stages a real definition without touching world blocks");

        System.out.println("PASS dungeon permissions and safety flags");
    }

    static void wand() {
        Rig rig = new Rig();
        List<String> minted = new ArrayList<>();
        rig.dispatcher.setWandGateway(name -> {
            if ("ghost".equals(name)) {
                return WandGateway.Result.fail("Игрок '" + name + "' не в сети.");
            }
            minted.add(name);
            return WandGateway.Result.ok("Жезл выдан '" + name + "'.");
        });

        CommandResponse given = rig.run("wand give builder_one");
        check(given.success(), "wand give must succeed through the gateway: " + given.lines());
        check(minted.contains("builder_one"), "the gateway must receive the typed target: " + minted);
        check(String.join("\n", given.lines()).contains("builder_one"),
                "the receipt must name the target: " + given.lines());

        CommandResponse alias = rig.run("editor wand builder_two");
        check(alias.success() && minted.contains("builder_two"),
                "editor wand alias must mint through the same gateway: " + alias.lines());

        CommandResponse offline = rig.run("wand give ghost");
        check(!offline.success(), "an offline target must be a refusal, not a receipt");
        check(String.join("\n", offline.lines()).contains("не в сети"),
                "the offline refusal must say why: " + offline.lines());

        // Without a live gateway the pure core must resolve the request but never
        // claim that an item was created.
        DungeonCommandDispatcher bare = new DungeonCommandDispatcher(
                rig.store, rig.sessions, rig.audit);
        CommandResponse noGateway = bare.execute(rig.builder, List.of("wand", "give", "someone"));
        check(noGateway.success(), "the no-gateway receipt must still resolve");
        check(String.join("\n", noGateway.lines()).contains("не создаётся"),
                "the no-gateway receipt must not claim the item exists: " + noGateway.lines());

        System.out.println("PASS dungeon wand issuance");
    }
}
'''


@pytest.fixture(scope="module")
def core(tmp_path_factory):
    return compile_core(tmp_path_factory.mktemp("dungeon-core-command"))


@pytest.fixture(scope="module")
def compiled(core, tmp_path_factory):
    return compile_fixture(tmp_path_factory.mktemp("dungeon-command-fixture"),
                           core, COMMAND_SOURCE, "DungeonCommandProbe")


@pytest.mark.parametrize("scenario,marker", [
    ("tree", "PASS dungeon command tree"),
    ("unknown", "PASS dungeon unknown usage and suggestion"),
    ("complete", "PASS dungeon tab completion"),
    ("permissions", "PASS dungeon permissions and safety flags"),
    ("wand", "PASS dungeon wand issuance"),
])
def test_cmdungeon_contract(compiled, scenario, marker):
    output = run_fixture(compiled, "DungeonCommandProbe", scenario, timeout=60)
    assert marker in output, output
