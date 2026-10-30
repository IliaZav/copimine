"""Execute the wave-owned elite matrix and retain objective role assignments."""
from pathlib import Path
import subprocess
import re
import pytest

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def ai_probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("wave-ai-roles")
    probe = directory / "WaveAiRolesProbe.java"
    probe.write_text('''
import java.util.*;
import me.copimine.endevent.domain.*;
public class WaveAiRolesProbe {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  public static void main(String[] args){
    switch(args[0]){
      case "no-intro-elites" -> {
        for(var role:EliteAbilityPolicy.Role.values()) {
          check(EliteAbilityPolicy.profileForWave(1,role).isEmpty(),"Wave 1 has no elite ability");
        }
        check(EliteAbilityPolicy.profileForWave(2,EliteAbilityPolicy.Role.SKELETON)
          .equals(List.of(EliteAbilityPolicy.Ability.TRIPLE_ARROWS, EliteAbilityPolicy.Ability.ARROW_RAIN)),
          "Wave 2 elite skeleton retains triple arrows and gains arrow rain");
        check(EliteAbilityPolicy.profileForWave(2,EliteAbilityPolicy.Role.ENDERMAN).isEmpty()
          &&EliteAbilityPolicy.profileForWave(2,EliteAbilityPolicy.Role.SPIDER).isEmpty(),
          "Wave 2 has no Enderman or spider elite cast pool");
        check(EliteAbilityPolicy.activeCastForWave(2,EliteAbilityPolicy.Role.SKELETON)
          ==EliteAbilityPolicy.Ability.ARROW_RAIN,
          "Wave 2 elite skeleton actively casts arrow rain");
      }
      case "wave-owned-abilities" -> {
        check(EliteAbilityPolicy.activeCastForWave(3,EliteAbilityPolicy.Role.SKELETON)
          ==EliteAbilityPolicy.Ability.ARROW_RAIN,"Wave 3 skeleton cast");
        check(EliteAbilityPolicy.activeCastForWave(3,EliteAbilityPolicy.Role.ENDERMAN)
          ==EliteAbilityPolicy.Ability.RIFT_BOLT,"Wave 3 Enderman cast");
        check(EliteAbilityPolicy.activeCastForWave(5,EliteAbilityPolicy.Role.ENDERMAN)
          ==EliteAbilityPolicy.Ability.DRAGON_BREATH,"Wave 5 Enderman cast");
        check(EliteAbilityPolicy.activeCastForWave(6,EliteAbilityPolicy.Role.ENDERMAN)
          ==EliteAbilityPolicy.Ability.VOID_BURST,"Wave 6 Enderman cast");
        check(EliteAbilityPolicy.activeCastForWave(6,EliteAbilityPolicy.Role.SKELETON)
          ==EliteAbilityPolicy.Ability.ARROW_RAIN,"Wave 6 skeleton cast");
        check(EliteAbilityPolicy.spiderConversions(3)==1
          &&EliteAbilityPolicy.spiderConversions(5)==2
          &&EliteAbilityPolicy.spiderConversions(6)==1,"spider support counts");
      }
      case "wave-seven-empty" -> {
        for(var role:EliteAbilityPolicy.Role.values()) {
          check(EliteAbilityPolicy.profileForWave(7,role).isEmpty(),"Wave 7 generic loadout");
          check(EliteAbilityPolicy.activeCastForWave(7,role)==null,"Wave 7 generic cast");
        }
      }
      case "objective-elites" -> {
        check(CombatTacticsPolicy.tacticFor(EndRiftObjective.Objective.RIFT_GATES,"ELITE",0)
          ==CombatTacticsPolicy.MobTactic.GATE_DEFENDER,"elite must defend the active portal");
        check(CombatTacticsPolicy.tacticFor(EndRiftObjective.Objective.OBELISK_ASSAULT,"ELITE",0)
          ==CombatTacticsPolicy.MobTactic.OBELISK_GUARD,"elite must retain its obelisk role");
      }
      default -> throw new AssertionError("unknown case");
    }
  }
}
''', encoding="utf-8")
    domain = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
    files = [domain / name for name in ["BossPhase.java", "CombatTacticsPolicy.java",
             "EliteAbilityPolicy.java", "EndRiftAiPolicy.java", "EndRiftObjective.java"]]
    result = subprocess.run(["javac", "-d", str(directory), *map(str, files), str(probe)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("scenario", ["no-intro-elites", "wave-owned-abilities",
                                      "wave-seven-empty", "objective-elites"])
def test_wave_specific_elite_loadouts(ai_probe, scenario):
    result = subprocess.run(["java", "-cp", str(ai_probe), "WaveAiRolesProbe", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_dragon_breath_uses_player_filtered_ghast_projectile():
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8"
    )
    launch = _java_method_body(source, "private boolean launchPhysicalEliteAbility(")
    assert "case DRAGON_BREATH -> caster.launchProjectile(SmallFireball.class, velocity);" in launch
    assert "launchProjectile(DragonFireball.class" not in launch
    cloud = _java_method_body(source, "private boolean spawnEliteDragonBreathCloud(")
    tick = _java_method_body(source, "private void tickEliteDragonBreathCloud(")
    assert "cloud.setBasePotionType(PotionType.WATER)" in cloud
    assert "cloud.addCustomEffect" not in cloud
    assert "for (Player player : Bukkit.getOnlinePlayers())" in tick
    assert "DragonBreathCloudPolicy.shouldSchedulePlayerHit" in tick
    assert "isWaveTargetAllowed(shooter, player)" in tick


def _java_method_body(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"unterminated Java method: {signature}")


def test_cancelled_elite_projectile_collision_cannot_apply_player_effects():
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8"
    )
    hit = _java_method_body(source, "public void onEliteProjectileHit(")
    outcome = _java_method_body(source, "public void onEliteProjectileHitOutcome(")

    assert "event.isCancelled()" in hit
    assert "runTaskLater(this, () -> resolveEliteProjectileImpact(" in hit
    assert "applyEliteSpiderWeb(" not in hit
    assert "spawnEliteDragonBreathCloud(" not in hit
    assert "event.isCancelled()" in outcome
    assert "eliteProjectileDamageBlocked.add(projectileId)" in outcome
    assert "eliteProjectileDamageAllowed.remove(projectileId)" in outcome


def test_rejected_elite_projectile_hit_cleanup_is_delegated_and_clears_markers():
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8"
    )
    hit = _java_method_body(source, "public void onEliteProjectileHit(")
    rejected_hit = hit[
        hit.index("if (event.isCancelled() || eliteProjectileDamageBlocked.contains(projectileId)"):
        hit.index("UUID shooterId = shooter.getUniqueId();")
    ]
    cleanup = _java_method_body(source, "private void tickEliteAbilityState(")
    clear_runtime = _java_method_body(source, "private void clearEliteProjectileRuntime(")

    assert "activeEliteProjectileExpiresAtTick.remove(projectileId)" not in rejected_hit
    assert "clearEliteProjectileRuntime(entityId, \"elite-ability-entity-expired-or-unloaded\"," in cleanup
    assert "eliteProjectileImpactScheduled.contains(entityId)" in cleanup
    preserve_gate = "if (preserveScheduledImpact && eliteProjectileImpactScheduled.contains(projectileId)) return;"
    assert preserve_gate in clear_runtime
    assert clear_runtime.index(preserve_gate) < clear_runtime.index(
        "eliteProjectileDamageAllowed.remove(projectileId)"
    )
    assert "eliteProjectileDamageBlocked.remove(projectileId)" in clear_runtime


def test_tagged_elite_projectile_with_dead_owner_is_cancelled_before_native_damage():
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8"
    )
    damage = _java_method_body(source, "public void onEliteProjectileDamage(")
    tagged = _java_method_body(source, "private boolean isTaggedEliteAbilityProjectile(")

    assert "isTaggedEliteAbilityProjectile(projectile)" in damage
    assert "!isEliteAbilityProjectile(projectile)" in damage
    assert "event.setCancelled(true)" in damage
    assert "eliteProjectileDamageBlocked.add(projectileId)" in damage
    assert "PersistentDataContainer" not in tagged
    assert "isLiveOwnedEntity(" not in tagged


def test_elite_skeleton_arrows_recheck_wave_and_session_target_admission():
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8"
    )
    skeleton_damage = _java_method_body(source, "public void onEventSkeletonArrowDamage(")
    custom_arrow_damage = _java_method_body(source, "private void onCustomEventArrowDamage(")

    assert skeleton_damage.count("!isWaveTargetAllowed(skeleton, player)") == 3
    assert "!isWaveTargetAllowed(shooter, player)" in custom_arrow_damage
    assert "!ownedBySession(shooter, eventId, generation)" in custom_arrow_damage


def test_web_snowball_hit_can_apply_without_a_damage_event():
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8"
    )
    hit = _java_method_body(source, "public void onEliteProjectileHit(")
    resolve = _java_method_body(source, "private void resolveEliteProjectileImpact(")

    assert "ability == EliteAbilityPolicy.Ability.WEB_SHOT" in hit
    assert "event.getHitEntity() instanceof Player player" in hit
    assert "eliteProjectileDamageAllowed.add(projectileId)" in hit
    assert "eliteProjectileDamageAllowed.contains(projectileId)" in resolve
    assert "applyEliteSpiderWeb(player, shooter)" in resolve


def test_wave_one_and_wave_two_default_rosters_are_increased_for_small_parties():
    config = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")
    waves = config.split("\nwaves:\n", 1)[1]

    def counts(wave):
        match = re.search(rf"(?m)^  wave-{wave}:\n((?:    .*\n|    .*\Z)*)", waves)
        assert match, f"missing Wave {wave} roster"
        return {key: int(value) for key, value in re.findall(
            r"(?m)^    (endermen|spiders|skeletons): (\d+)\s*$", match.group(1)
        )}

    wave_one = counts(1)
    wave_two = counts(2)
    assert sum(wave_one.values()) == 44
    assert sum(wave_two.values()) == 96
    pressure = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/WavePressurePolicy.java").read_text(
        encoding="utf-8"
    )
    assert "SMALL_PARTY_WAVE_ONE_PRESSURE = 12" in pressure
