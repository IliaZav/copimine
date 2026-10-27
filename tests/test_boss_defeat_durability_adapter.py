"""Prove the boss durability contracts at the adapter boundary.

The two probes compile the real adapter methods against small stubs, so the
assertions describe behaviour (ordering, rejection, retry) instead of the
presence of a source string.
"""

from pathlib import Path
import os
import shutil
import subprocess


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


def _tool(name):
    java_home = os.environ.get("JAVA_HOME")
    executable = name + (".exe" if os.name == "nt" else "")
    if java_home:
        candidate = Path(java_home) / "bin" / executable
        if candidate.is_file():
            return str(candidate)
    found = shutil.which(name)
    assert found, f"{name} is required to compile the boss durability probe"
    return found


def _run_probe(tmp_path, name, probe_source, domain_types=()):
    probe = tmp_path / f"{name}.java"
    probe.write_text(probe_source, encoding="utf-8")
    compiled = subprocess.run(
        [_tool("javac"), "-encoding", "UTF-8", "-d", str(tmp_path),
         *(str(DOMAIN / (type_name + ".java")) for type_name in domain_types),
         str(probe)],
        capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        [_tool("java"), "-cp", str(tmp_path), name],
        capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr


def test_half_repel_is_durable_before_its_effects(tmp_path):
    method = _method(SOURCE.read_text(encoding="utf-8"),
                     "private void commitBossHalfRepel(LivingEntity boss)")
    _run_probe(tmp_path, "BossHalfRepelProbe", r'''
import java.util.*;

public class BossHalfRepelProbe {
    static final class LivingEntity {
        UUID getUniqueId() { return UUID.fromString("00000000-0000-0000-0000-0000000000aa"); }
    }
    static final class Log { void warning(String message) { } }

    final Log log = new Log();
    Log getLogger() { return log; }
    long eventId = 1L;
    boolean bossHalfRepelCommitted;
    boolean saveResult = true;
    boolean effects;
    final List<String> order = new ArrayList<>();

    boolean saveStateSync() { order.add("save"); return saveResult; }
    void cancelBossCastForHalfRepel() { order.add("cancel"); }
    void applyBossHalfRepel(LivingEntity boss) { order.add("effects"); effects = true; }

''' + method + r'''

    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        BossHalfRepelProbe durable = new BossHalfRepelProbe();
        durable.commitBossHalfRepel(new LivingEntity());
        check(durable.bossHalfRepelCommitted && durable.effects,
                "a saved repel must stay committed and play its effects");
        check(durable.order.equals(List.of("save", "cancel", "effects")),
                "the cast must be cancelled after the durable save and before the effects: " + durable.order);

        BossHalfRepelProbe failed = new BossHalfRepelProbe();
        failed.saveResult = false;
        failed.commitBossHalfRepel(new LivingEntity());
        check(!failed.bossHalfRepelCommitted,
                "a failed save must release the flag so a later tick retries");
        check(!failed.effects && failed.order.equals(List.of("save")),
                "a failed save must not play the blind/slow/knockback: " + failed.order);

        failed.saveResult = true;
        failed.commitBossHalfRepel(new LivingEntity());
        check(failed.bossHalfRepelCommitted && failed.effects,
                "the retry after a failed save must apply the repel once");
        check(failed.order.equals(List.of("save", "save", "cancel", "effects")),
                "retry must save before the effects exactly once: " + failed.order);
    }
}
''')


def test_only_a_player_owned_lethal_hit_commits_the_durable_defeat(tmp_path):
    method = _method(SOURCE.read_text(encoding="utf-8"),
                     "private void handleOfficialLethalHit(LivingEntity boss, Entity source,")
    _run_probe(tmp_path, "BossLethalDefeatProbe", r'''
import java.util.*;
import me.copimine.endevent.domain.*;

public class BossLethalDefeatProbe {
    static class Entity {
        private final UUID id = UUID.randomUUID();
        UUID getUniqueId() { return id; }
        String getType() { return "ZOMBIE"; }
    }
    static final class Player extends Entity {
        private final UUID id;
        Player(UUID id) { this.id = id; }
        UUID getUniqueId() { return id; }
    }
    static final class LivingEntity {
        double health;
        LivingEntity(double health) { this.health = health; }
        double getHealth() { return health; }
        void setHealth(double value) { health = value; }
        UUID getUniqueId() { return UUID.fromString("00000000-0000-0000-0000-0000000000aa"); }
        boolean isDead() { return health <= 0.0D; }
    }
    static final class Log {
        void info(String message) { }
        void warning(String message) { }
    }

    final Log log = new Log();
    Log getLogger() { return log; }
    long eventId = 42L;
    long generation = 7L;
    BossPhase bossPhase = BossPhase.RAGE;
    UUID bossKillerUuid;
    final Set<UUID> combatHelpers = new HashSet<>();
    Player owner;
    boolean participant = true;
    int synchronizes;
    double synchronizedHealth = -1.0D;
    int commits;
    int feedbacks;
    final List<String> diagnostics = new ArrayList<>();

    Player playerDamageOwner(Entity source) { return owner; }
    boolean isActiveBossParticipant(Player player) { return participant; }
    double bossMaxHealth(LivingEntity boss) { return 20000.0D; }
    void synchronizeBossPhase(LivingEntity boss) {
        synchronizes++;
        synchronizedHealth = boss.health;
        bossPhase = BossPhase.LAST_SEAL;
    }
    void applyBossHitFeedbackAt(LivingEntity boss, Entity source, Object contact,
                                BossHitFeedbackPolicy.Feedback feedback) { feedbacks++; }
    Object bodyImpactContact(LivingEntity boss, Entity source) { return null; }
    void emitBossDamageDiagnostic(String action, String severity, LivingEntity boss,
                                  Entity source, double rawDamage, double finalDamage,
                                  double appliedDamage, double healthBefore,
                                  double healthAfter, String reason) {
        diagnostics.add(action + "/" + reason + "/" + healthBefore);
    }
    void commitOfficialBossDefeat(LivingEntity boss, Entity source) { commits++; }
    // Last Seal entry must create its defenders before any victory is decided.
    int requiredGuardians = 4;
    int ensureCalls;
    int livingGuardians = 4;
    boolean shielded = true;
    boolean testBoss;
    int permanentTentacleTarget(boolean test) { testBoss = test; return requiredGuardians; }
    void ensurePermanentTentacles(LivingEntity boss, int target) { ensureCalls++; }
    int livingTentacleGuardianCount() { return livingGuardians; }
    boolean tentacleGuardianShielded() { return shielded; }
    boolean isTestBoss(LivingEntity boss) { return false; }
    enum EventPhase { BOSS_ACTIVE }
    EventPhase phase = EventPhase.BOSS_ACTIVE;

''' + method + r'''

    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

        BossLethalDefeatProbe accepted = new BossLethalDefeatProbe();
        accepted.owner = new Player(playerId);
        LivingEntity boss = new LivingEntity(9000.0D);
        accepted.handleOfficialLethalHit(boss, accepted.owner, 9000.0D, 12000.0D, 8000.0D, false);
        check(accepted.commits == 0,
                "a lethal hit that only reaches Last Seal must withhold victory");
        check(accepted.ensureCalls == 1 && !accepted.testBoss,
                "Last Seal entry must create its defenders before any victory: "
                        + accepted.ensureCalls);
        check(boss.health == BossPhasePolicy.lastSealThreshold(20000.0D),
                "the lethal hit must stop at the Last Seal threshold, never zero real HP inline");
        check(accepted.synchronizes == 1
                        && accepted.synchronizedHealth == boss.health,
                "crossed phase bands must be entered before the defenders spawn");
        check(accepted.feedbacks == 1,
                "an accepted lethal hit still needs its hit feedback");
        check(accepted.diagnostics.equals(
                        List.of("DAMAGE_ACCEPT/last-seal-entry-shield-pending/9000.0")),
                "Last Seal entry diagnostic missing: " + accepted.diagnostics);

        BossLethalDefeatProbe lastSeal = new BossLethalDefeatProbe();
        lastSeal.bossPhase = BossPhase.LAST_SEAL;
        lastSeal.owner = new Player(playerId);
        lastSeal.shielded = false;
        boss = new LivingEntity(1000.0D);
        lastSeal.handleOfficialLethalHit(boss, lastSeal.owner, 1000.0D, 1000.0D, 1000.0D, true);
        check(lastSeal.commits == 1 && lastSeal.synchronizes == 0 && boss.health == 1000.0D,
                "a Last Seal lethal hit must not replay bands or zero HP inline");
        check(lastSeal.ensureCalls == 0,
                "Last Seal entry defenders must not respawn on the terminal hit");
        check(playerId.equals(lastSeal.bossKillerUuid)
                        && lastSeal.combatHelpers.contains(playerId),
                "the accepted lethal hit must record its killer and participation");

        BossLethalDefeatProbe stranger = new BossLethalDefeatProbe();
        stranger.owner = new Player(playerId);
        stranger.participant = false;
        boss = new LivingEntity(9000.0D);
        stranger.handleOfficialLethalHit(boss, stranger.owner, 9000.0D, 12000.0D, 8000.0D, false);
        check(stranger.commits == 0 && stranger.synchronizes == 0 && boss.health == 9000.0D,
                "a non-participant lethal hit must not defeat the encounter");
        check(stranger.diagnostics.equals(
                        List.of("DAMAGE_REJECT/lethal-hit-not-player-owned/9000.0")),
                "rejected lethal diagnostic missing: " + stranger.diagnostics);

        BossLethalDefeatProbe environment = new BossLethalDefeatProbe();
        boss = new LivingEntity(9000.0D);
        environment.handleOfficialLethalHit(boss, new Entity(), 9000.0D, 12000.0D, 8000.0D, false);
        check(environment.commits == 0 && boss.health == 9000.0D
                        && environment.bossKillerUuid == null,
                "environment or cleanup damage must never win the encounter");
    }
}
''', ("BossPhase", "BossHealthPolicy", "BossPhasePolicy",
      "EndRiftAiPolicy", "BossHitFeedbackPolicy"))