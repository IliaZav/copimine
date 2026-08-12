"""Contract and catalog tests for the combat AR artifact slice.

These tests intentionally fail against the pre-feature checkout.  They are
kept independent of Bukkit so catalog and source wiring can be checked in CI
without starting a Paper server.
"""

from __future__ import annotations

import re
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"
BUILD_SH = ROOT / "copimine-artifacts" / "build-plugin.sh"
MATH_SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CombatArtifactMath.java"
MATH_TEST = ROOT / "tests" / "CombatArtifactMathTest.java"
SHOT_POLICY_SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CombatArtifactShotPolicy.java"
SHOT_POLICY_TEST = ROOT / "tests" / "CombatArtifactShotPolicyTest.java"


def item_block(item_id: str) -> str:
    text = ITEMS.read_text(encoding="utf-8")
    match = re.search(
        rf"(?ms)^  - id: {re.escape(item_id)}\s*$.*?(?=^  - id:|^donation-catalog:|\Z)",
        text,
    )
    if not match:
        raise AssertionError(f"missing catalog item {item_id}")
    return match.group(0)


def require_all(text: str, *needles: str) -> None:
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise AssertionError(f"missing markers: {missing}")


class CombatArtifactCatalogContractTest(unittest.TestCase):
    def test_teleport_bow_catalog_is_plain_epic_and_priced(self) -> None:
        block = item_block("combat_crossbow")
        require_all(
            block,
            "material: BOW",
            "source: AR_SHOP",
            'name: "&dЛук телепортации"',
            "rarity: EPIC",
            "price_ar: 100",
            "cooldown_seconds: 200",
            "effect: AR_CROSSBOW_TELEPORT",
            "custom_model_data: 10014",
        )

    def test_trail_bow_has_no_infinity_and_exact_catalog_contract(self) -> None:
        block = item_block("cobblestone_trail_bow")
        require_all(
            block,
            "material: BOW",
            "source: AR_SHOP",
            "name: \"&dЛук блоков\"",
            "rarity: EPIC",
            "price_ar: 64",
            "cooldown_seconds: 15",
            "effect: AR_COBBLESTONE_TRAIL",
            "custom_model_data: 10015",
        )
        self.assertNotIn("enchantment: INFINITY", block)

    def test_explosive_crossbow_has_multishot_and_exact_catalog_contract(self) -> None:
        block = item_block("explosive_crossbow")
        require_all(
            block,
            "material: CROSSBOW",
            "source: AR_SHOP",
            "name: \"&dВзрывной арбалет\"",
            "rarity: EPIC",
            "price_ar: 300",
            "cooldown_seconds: 30",
            "effect: AR_EXPLOSIVE_CROSSBOW",
            "enchantment: MULTISHOT",
            "custom_model_data: 10016",
        )

    def test_streamer_stick_is_hidden_and_not_an_ar_shop_item(self) -> None:
        block = item_block("streamer_stick")
        require_all(
            block,
            "material: STICK",
            "source: ADMIN_ONLY",
            "name: \"&dПалка стримера\"",
            "rarity: EPIC",
            "cooldown_seconds: 15",
            "effect: STREAMER_STICK_ARC",
            "custom_model_data: 0",
        )
        self.assertNotIn("source: AR_SHOP", block)

    def test_source_exposes_all_projectile_and_melee_boundaries(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "ProjectileHitEvent",
            "var1.getBow()",
            "keyProjectileAbility",
            "keyProjectileOwner",
            "AR_CROSSBOW_TELEPORT",
            "AR_COBBLESTONE_TRAIL",
            "AR_EXPLOSIVE_CROSSBOW",
            "STREAMER_STICK_ARC",
            "TNTPrimed",
            "CombatArtifactMath.interpolate",
            "CombatArtifactShotPolicy.decide",
            "BlockPlaceEvent",
            "MULTISHOT",
            "Bukkit.getCurrentTick()",
        )
        self.assertNotIn("EntityLoadCrossbowEvent", source)
        self.assertNotIn("keyExplosiveShotDamage", source)
        self.assertNotIn("player.damage(6.0D)", source)

    def test_teleport_crossbow_uses_the_normal_catalog_material_binding(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(source, "var1.getType() != var8.material()")
        self.assertNotIn("legacyTeleportCrossbow", source)

    def test_new_shot_rules_do_not_intercept_vanilla_items(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "authenticCatalogItem(var1.getBow(), var2, \"projectile_shoot\")",
            "if (weapon != null)",
            "if (decision.startsShot())",
            "var1.setCancelled(true)",
            "ignoreCancelled = false",
            "if (event.isCancelled())",
        )

    def test_projectiles_are_owned_consumed_and_trail_is_protected(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "data.set(this.keyProjectileAbility",
            "data.set(this.keyProjectileOwner",
            "this.combatProjectiles.remove(projectile.getUniqueId())",
            "data.remove(this.keyProjectileAbility)",
            "floor.isReplaceable()",
            "place.isCancelled() || !place.canBuild()",
            "MAX_COBBLESTONE_TRAIL_BLOCKS",
        )

    def test_explosive_crossbow_creates_primed_tnt_only_at_hit(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "case \"AR_EXPLOSIVE_CROSSBOW\" -> this.spawnExplosiveTnt(owner, projectile)",
            "world.spawn(location, TNTPrimed.class)",
            "tnt.setFuseTicks(EXPLOSIVE_CROSSBOW_FUSE_TICKS)",
        )
        self.assertNotIn("world.spawn(location, TNTPrimed.class);\n      tnt.setFuseTicks", source[: source.index("onCombatProjectileHit")])

    def test_projectile_hit_requires_a_server_registered_shot_identity(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "combatProjectileIdentities",
            "CombatProjectileIdentity",
            "this.combatProjectileIdentities.put",
            "CombatProjectileIdentity identity = this.combatProjectileIdentities.remove",
            "if (identity == null)",
            "identity.ability()",
            "identity.ownerUuid()",
        )

    def test_projectile_identity_and_explosive_tracker_are_cleaned_on_quit(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        quit_handler = source[source.index("public void onQuit"):source.index("private void tickPozdnyakovAce")]
        require_all(
            source,
            "explosiveTntOwners",
            "keyExplosiveTnt",
            "onExplosiveArtifactOwnerDamage",
            "this.combatProjectileIdentities.clear()",
        )
        require_all(
            quit_handler,
            "combatProjectileIdentities.entrySet().removeIf",
            "combatProjectiles.entrySet().removeIf",
        )

    def test_cobblestone_trail_is_queued_and_drained_behind_the_projectile(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "pendingCobblestoneTrails",
            "queueCobblestoneTrail",
            "drainCobblestoneTrailQueue",
            "MAX_TRAIL_BLOCKS_PER_TICK",
            "skipTip",
        ):
            self.assertIn(marker, source)

    def test_shot_cannot_consume_cooldown_without_a_projectile(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        shot_handler = source[source.index("public void onCrossbowArtifactShot"):source.index("private void markCombatProjectile")]
        require_all(shot_handler, "if (!(var1.getProjectile() instanceof Projectile))")

    def test_safe_teleport_checks_every_candidate_chunk(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        safe_location = source[source.index("private Location findSafeProjectileLocation"):source.index("private void spawnExplosiveTnt")]
        require_all(safe_location, "world.isChunkLoaded(candidate.getBlockX() >> 4, candidate.getBlockZ() >> 4)")

    def test_explosive_tnt_cannot_damage_its_owner(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "getDamageSource()",
            "getCausingEntity()",
            "keyExplosiveTnt",
            "explosiveTntOwners",
            "tnt.getPersistentDataContainer().set(",
            "owner.getUniqueId().toString()",
            "event.setCancelled(true)",
        )

    def test_streamer_arc_keeps_existing_cooldown_and_self_hit_guard(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "this.actionCooldowns.put(this.actionCooldownKey(var2, var15)",
            "if (var10 != null && var10 != var2)",
            "var10.setFallDistance(0.0F)",
            '"STREAMER_STICK_ARC".equals(var16) && (var10 == null || var10 == var2)',
            "CombatArtifactMath.streamerKnockbackVelocity",
            "Bukkit.getScheduler().runTask(this",
        )

    def test_streamer_arc_is_melee_only_and_ignores_dead_targets(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "isStreamerMeleeHit(var1)",
            "var10.isDead()",
            "private boolean isStreamerMeleeHit",
            "event.getDamager() instanceof Player",
        )

    def test_admin_give_accepts_all_admin_only_catalog_items(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        admin_give = source[source.index("handleAdminGiveCommand"):source.index("persistAdminGrantedInstance")]
        require_all(
            admin_give,
            "if (item == null || !this.isAdminOnlyCatalogItem(itemId))",
            "persistAdminGiftDelivery(ownerUuid, target.getName(), item",
        )
        self.assertNotIn(
            '|| !"POZDNYAKOV_ACE".equalsIgnoreCase(item.effect())',
            admin_give,
            "admin command must not reject new ADMIN_ONLY artifacts by legacy effect name",
        )

    def test_bash_build_compiles_all_java_sources(self) -> None:
        build = BUILD_SH.read_text(encoding="utf-8")
        require_all(
            build,
            'src_root="$plugin_dir/src"',
            'find "$src_root" -type f -name \'*.java\'',
            'javac -encoding UTF-8 -cp "$classpath" -d "$classes" "${javac_sources[@]}"',
        )

    def test_math_unit_test_sources_are_present_and_runnable(self) -> None:
        self.assertTrue(MATH_SOURCE.exists(), "production trajectory seam is missing")
        self.assertTrue(MATH_TEST.exists(), "trajectory unit test is missing")
        with tempfile.TemporaryDirectory() as output:
            result = subprocess.run(
                ["javac", "-encoding", "UTF-8", "-d", output, str(MATH_SOURCE), str(MATH_TEST)],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            run = subprocess.run(
                ["java", "-cp", output, "CombatArtifactMathTest"],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)

    def test_shot_policy_unit_test_sources_are_present_and_runnable(self) -> None:
        self.assertTrue(SHOT_POLICY_SOURCE.exists(), "shot admission policy is missing")
        self.assertTrue(SHOT_POLICY_TEST.exists(), "shot admission policy test is missing")
        with tempfile.TemporaryDirectory() as output:
            result = subprocess.run(
                ["javac", "-encoding", "UTF-8", "-d", output, str(SHOT_POLICY_SOURCE), str(SHOT_POLICY_TEST)],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            run = subprocess.run(
                ["java", "-cp", output, "CombatArtifactShotPolicyTest"],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)


if __name__ == "__main__":
    unittest.main()
