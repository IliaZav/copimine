"""Pure dungeon domain contracts: cuboid geometry, room lifecycle, wand forgery.

The probe compiles and runs the production ``me.copimine.dungeon.domain`` and
``me.copimine.dungeon.editor`` classes. Nothing is mirrored, so a regression in
the shipped geometry or the wand MAC is caught here.
"""
from pathlib import Path

import pytest

from tests.dungeon_probe import compile_core, compile_fixture, run_fixture

ROOT = Path(__file__).resolve().parents[1]

DOMAIN_SOURCE = r'''
import java.util.*;
import me.copimine.dungeon.domain.*;
import me.copimine.dungeon.editor.*;

public class DungeonDomainProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // ---- Cuboid normalization -------------------------------------------
        Cuboid c = new Cuboid(5, 6, 7, 1, 2, 3);
        check(c.minX() == 1 && c.minY() == 2 && c.minZ() == 3, "cuboid min not normalized");
        check(c.maxX() == 5 && c.maxY() == 6 && c.maxZ() == 7, "cuboid max not normalized");
        check(c.sizeX() == 5 && c.sizeY() == 5 && c.sizeZ() == 5, "cuboid sizes wrong");
        check(c.volume() == 125, "cuboid volume wrong");
        check(c.contains(1, 2, 3) && c.contains(5, 6, 7), "inclusive corners must be inside");
        check(!c.contains(0, 2, 3), "a block outside the cuboid must not be inside");
        Cuboid inner = Cuboid.of(2, 3, 4, 4, 5, 6);
        check(c.contains(inner), "containment failed");
        check(inner.intersects(c), "intersection failed");
        check(!inner.intersects(Cuboid.of(100, 100, 100, 101, 101, 101)), "far cuboids must not intersect");

        // ---- exactly twelve edges ------------------------------------------
        check(c.edgeCount() == 12, "a cuboid always has 12 edges");
        check(c.edgeSegments().size() == 12, "edgeSegments must return 12 pairs");
        check(c.corners().size() == 8, "a cuboid always has 8 corners");
        check(c.edgeSamplePoints(2).size() == 12 * 3, "edge sampling count is 12*(steps+1)");
        check(c.verticalCornerPillars(2).size() == 4 * 3, "pillar sampling count is 4*(steps+1)");

        // ---- 1x1x1 exactness -----------------------------------------------
        Cuboid one = Cuboid.of(3, 4, 5, 3, 4, 5);
        check(one.enclosesExactlyOneBlock(), "a single block selection must be 1x1x1");
        check(one.volume() == 1, "1x1x1 volume must be 1");
        for (int[] corner : one.corners()) {
            check(corner[0] == 3 && corner[1] == 4 && corner[2] == 5,
                    "all eight corners of a 1x1x1 selection collapse to the same block");
        }
        check(!c.enclosesExactlyOneBlock(), "a large cuboid is not a single block");

        // ---- RoomState legality --------------------------------------------
        check(RoomStateTransitionValidator.isLegal(RoomState.DRAFT, RoomState.PUBLISHED_IDLE),
                "DRAFT -> PUBLISHED_IDLE must be legal");
        check(!RoomStateTransitionValidator.isLegal(RoomState.FIGHTING, RoomState.DRAFT),
                "FIGHTING -> DRAFT must be illegal");
        check(!RoomStateTransitionValidator.isLegal(RoomState.PUBLISHED_IDLE, RoomState.FIGHTING),
                "sealing must not be skipped");
        check(RoomStateTransitionValidator.evaluate(RoomState.FIGHTING, RoomState.FIGHTING, 7L, 7L)
                        == RoomStateTransitionValidator.Decision.SELF_TRANSITION_IDEMPOTENT,
                "same-state callbacks must be idempotent");
        check(RoomStateTransitionValidator.evaluate(RoomState.ARMING, RoomState.SEALING, 6L, 7L)
                        == RoomStateTransitionValidator.Decision.STALE_GENERATION,
                "an older generation must be rejected as stale");
        check(RoomStateTransitionValidator.describe(RoomState.FIGHTING, RoomState.DRAFT,
                        RoomStateTransitionValidator.Decision.ILLEGAL_TRANSITION)
                        .contains("Недопустимый переход"),
                "rejection reason must be Russian");
        check(RoomState.RECOVERY_REQUIRED.isRecoverable(), "RECOVERY_REQUIRED is recoverable");
        check(RoomState.FIGHTING.isActive(), "FIGHTING is an active state");
        check(RoomState.fromId("draft") == RoomState.DRAFT, "state ids are stable lowercase");

        // ---- editor overlay sampler ----------------------------------------
        Cuboid big = Cuboid.of(0, 0, 0, 200, 200, 200);
        List<OverlaySample> samples = EditorOverlay.sample(OverlayType.ROOM, big, 64);
        check(!samples.isEmpty(), "overlay must produce samples");
        check(samples.size() <= 64, "overlay must honour the point budget");
        for (OverlaySample sample : samples) {
            check(sample.color() == OverlayType.ROOM.color(), "room samples carry the room colour");
            check(sample.style() == OverlayType.ROOM.lineStyle(), "room samples carry the room line style");
        }
        check(OverlayType.TRIGGER.lineStyle() == LineStyle.DASHED, "trigger overlay is dashed");
        check(OverlayType.GATE.lineStyle() == LineStyle.DOUBLE, "gate overlay is double-line");
        check(OverlayType.SECRET.lineStyle() == LineStyle.DOTTED, "secret overlay is dotted");
        check(OverlayType.SECRET.builderOnly() && OverlayType.PROTECTED.builderOnly(),
                "secret and protected overlays are builder-only");

        // ---- enumerated styles ---------------------------------------------
        check(GateStyle.tokens().equals("appearing|grille|lift|slide|collapse|secret"),
                "gate style tokens drifted: " + GateStyle.tokens());
        check(UnlockMode.tokens().equals("on_clear|manual_after_clear|milestone"),
                "unlock tokens drifted: " + UnlockMode.tokens());
        check(ScalingMode.tokens().equals("fixed|per_player|adaptive"), "scaling tokens drifted");
        check(RewardMode.tokens().equals("chest|drops|personal"), "reward tokens drifted");
        check(MobTemplate.dungeonExtensionIds().size() == 3,
                "exactly three dungeon-extension species are allowed");

        // ---- forged wand rejection -----------------------------------------
        String secret = "server-only-secret";
        String owner = "11111111-2222-3333-4444-555555555555";
        String token = WandToken.issue(secret, owner, WandToken.TOKEN_VERSION);
        check(WandToken.verify(secret, owner, WandToken.TOKEN_VERSION, token),
                "a freshly issued token must verify");
        check(!WandToken.verify(secret, "99999999-0000-0000-0000-000000000000",
                        WandToken.TOKEN_VERSION, token),
                "a copied token must not authorize another owner");
        String attacker = WandToken.issue("attacker-secret", owner, WandToken.TOKEN_VERSION);
        check(!WandToken.verify(secret, owner, WandToken.TOKEN_VERSION, attacker),
                "a token minted with a different secret must be rejected");
        check(!WandToken.verify(secret, owner, WandToken.TOKEN_VERSION, token + "00"),
                "a tampered token must be rejected");
        check(!WandToken.verify(secret, owner, WandToken.TOKEN_VERSION + 1, token),
                "a version bump must invalidate the token");
        check(!WandToken.verify("", owner, WandToken.TOKEN_VERSION, token),
                "an empty server secret can never verify");
        check(WandToken.matchesVersion(token, WandToken.TOKEN_VERSION), "version prefix must match");

        // ---- op alone is never enough --------------------------------------
        check(!EditorPermissions.allows(Set.of(), true, EditorPermissions.Capability.EDIT_DRAFT),
                "op without a permission must not edit");
        check(!EditorPermissions.allows(Set.of(), true, EditorPermissions.Capability.ADMIN_PUBLISH),
                "op without a permission must not publish");
        check(EditorPermissions.allows(Set.of(EditorPermissions.BUILDER), false,
                        EditorPermissions.Capability.EDIT_DRAFT),
                "builder permission must edit");
        check(!EditorPermissions.allows(Set.of(EditorPermissions.BUILDER), false,
                        EditorPermissions.Capability.ADMIN_PUBLISH),
                "builder permission must not publish");
        check(EditorPermissions.allows(Set.of(EditorPermissions.ADMIN), false,
                        EditorPermissions.Capability.ADMIN_PUBLISH),
                "admin permission must publish");
        check(EditorPermissions.describeDenial(EditorPermissions.Capability.ADMIN_PUBLISH)
                        .contains(EditorPermissions.ADMIN),
                "denial must name the required permission node");

        System.out.println("PASS dungeon domain");
    }
}
'''


@pytest.fixture(scope="module")
def core(tmp_path_factory):
    return compile_core(tmp_path_factory.mktemp("dungeon-core"))


def test_domain_geometry_lifecycle_overlay_and_wand(core, tmp_path):
    classes = compile_fixture(tmp_path, core, DOMAIN_SOURCE, "DungeonDomainProbe")
    output = run_fixture(classes, "DungeonDomainProbe")
    assert "PASS dungeon domain" in output


def test_pure_core_has_no_bukkit_imports():
    """The probe only works because the core stays Bukkit-free; enforce it."""
    from tests.dungeon_probe import pure_sources

    offenders = []
    for path in pure_sources():
        text = path.read_text(encoding="utf-8")
        if "org.bukkit" in text or "io.papermc" in text:
            offenders.append(path.name)
    assert offenders == [], f"pure core leaked server imports: {offenders}"
