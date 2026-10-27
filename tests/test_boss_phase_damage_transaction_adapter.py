"""Exercise the boss health commit across real phase policy boundaries."""

from pathlib import Path
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


def _method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for cursor in range(opening, len(source)):
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        if depth == 0:
            return source[start : cursor + 1]
    raise AssertionError(f"unterminated method: {signature}")


def test_all_damage_routes_share_the_phase_aware_commit():
    source = SOURCE.read_text(encoding="utf-8")
    for signature in (
        "private void handleCurrentBossDamage(EntityDamageEvent event, LivingEntity boss, Entity source)",
        "private void handleCurrentBossProjectileDamage(ProjectileHitEvent event,",
        "private boolean applyCurrentBossProjectileDamage(LivingEntity boss, Projectile projectile)",
    ):
        method = _method(source, signature)
        assert method.index("synchronizeBossPhase(boss)") < method.index("bossDamageImmunityReason()")
        assert "commitCurrentBossHealth(boss, result)" in method
        assert "boss.setHealth(result.remainingHealth())" not in method


def test_phase_aware_health_commit_and_guardian_shield(tmp_path):
    source = SOURCE.read_text(encoding="utf-8")
    method = _method(source, "private BossHealthCommit commitCurrentBossHealth(")
    probe = tmp_path / "BossHealthCommitProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.*;

public class BossHealthCommitProbe {
    static final class LivingEntity {
        double health;
        LivingEntity(double value) { health = value; }
        double getHealth() { return health; }
        void setHealth(double value) { health = value; }
    }
    record BossHealthCommit(BossRealHealthDamagePolicy.Result result, double shieldBlockedDamage) { }
    BossPhase bossPhase = BossPhase.AWAKENING;
    boolean guardians = true;
    final List<BossPhase> entered = new ArrayList<>();
    double bossMaxHealth(LivingEntity ignored) { return 5000; }
    boolean tentacleGuardianShielded() { return guardians && bossPhase == BossPhase.LAST_SEAL; }
    void synchronizeBossPhase(LivingEntity boss) {
        var transition = BossPhasePolicy.transition(bossPhase, boss.getHealth(), bossMaxHealth(boss));
        entered.addAll(transition.entered());
        bossPhase = transition.current();
    }
''' + method + r'''
    BossHealthCommit hit(LivingEntity boss, double damage) {
        synchronizeBossPhase(boss);
        var reason = tentacleGuardianShielded()
                ? BossPhasePolicy.DamageImmunityReason.LAST_SEAL_GUARDIANS
                : BossPhasePolicy.DamageImmunityReason.NONE;
        var result = BossRealHealthDamagePolicy.apply(boss.getHealth(), damage,
                BossDamagePolicy.evaluate(bossPhase, reason), bossMaxHealth(boss));
        return result.applied() ? commitCurrentBossHealth(boss, result) : new BossHealthCommit(result, 0);
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var ordinary = new BossHealthCommitProbe();
        var boss = new LivingEntity(4500);
        var result = ordinary.hit(boss, 2000);
        check(boss.health == 2500 && result.result().appliedDamage() == 2000,
                "ordinary crossed bands lost accepted damage");
        check(ordinary.entered.equals(List.of(BossPhase.HUNT, BossPhase.RIFT)),
                "ordinary crossed bands were not processed in order");

        var sameTick = new BossHealthCommitProbe();
        boss = new LivingEntity(4500);
        sameTick.hit(boss, 500);
        sameTick.hit(boss, 1500);
        check(boss.health == 2500 && sameTick.entered.equals(List.of(BossPhase.HUNT, BossPhase.RIFT)),
                "same-tick hits or exact thresholds replayed or lost damage");
        boss.health = 4500;
        sameTick.synchronizeBossPhase(boss);
        check(sameTick.entered.size() == 2, "healing replayed crossed phases");

        var shield = new BossHealthCommitProbe();
        boss = new LivingEntity(5000);
        result = shield.hit(boss, 9999);
        check(boss.health == 1000 && result.result().appliedDamage() == 4000
                && result.shieldBlockedDamage() == 1000 && !result.result().lethal(),
                "entry shield failed to block lethal overflow");
        check(shield.entered.equals(List.of(BossPhase.HUNT, BossPhase.RIFT, BossPhase.OVERLOAD,
                BossPhase.RAGE, BossPhase.LAST_SEAL)), "lethal hit skipped phase entries");
        result = shield.hit(boss, 30);
        check(boss.health == 1000 && !result.result().applied(),
                "a subsequent hit bypassed pending guardians");
        shield.guardians = false;
        result = shield.hit(boss, 1000);
        check(boss.health == 0 && result.result().lethal() && shield.entered.size() == 5,
                "open Last Seal did not allow the lethal hit");

        var disabled = new BossHealthCommitProbe();
        disabled.guardians = false;
        boss = new LivingEntity(5000);
        result = disabled.hit(boss, 9999);
        check(boss.health == 0 && result.result().lethal() && result.shieldBlockedDamage() == 0
                && disabled.bossPhase == BossPhase.LAST_SEAL,
                "unshielded lethal hit skipped phase entry or invented a shield");
    }
}
''', encoding="utf-8")
    files = [DOMAIN / (name + ".java") for name in (
        "BossPhase", "BossPhasePolicy", "BossHealthPolicy", "BossRealHealthDamagePolicy",
        "BossDamagePolicy", "EndRiftAiPolicy",
    )]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
                             *map(str, files), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "BossHealthCommitProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
