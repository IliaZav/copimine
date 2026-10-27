"""Execute the production tentacle hit adapters against a real lifecycle controller."""

from pathlib import Path
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
SOURCE_ROOT = ROOT / "copimine-end-event/src"


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for cursor in range(opening, len(source)):
        if source[cursor] == "{":
            depth += 1
        elif source[cursor] == "}":
            depth -= 1
            if depth == 0:
                return source[start : cursor + 1]
    raise AssertionError(f"unterminated method: {signature}")


@pytest.fixture(scope="module")
def tentacle_hit_probe(tmp_path_factory):
    source = SOURCE.read_text(encoding="utf-8")
    methods = "\n".join(
        _method(source, signature)
        for signature in (
            "public void onTentacleGuardianDamage(EntityDamageByEntityEvent event)",
            "public void onTentacleGuardianProjectile(ProjectileHitEvent event)",
            "private void applyTentacleGuardianDamage(ItemDisplay display, double damage,",
            "private double tentacleMaxHealth(TentacleAnimationPolicy.Kind kind)",
        )
    )
    directory = tmp_path_factory.mktemp("tentacle-hit-adapter")
    probe = directory / "TentacleHitAdapterProbe.java"
    probe.write_text(
        r'''
import java.util.*;
import java.util.logging.Logger;
import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.TentacleAnimationPolicy;
import me.copimine.endevent.domain.TentacleGuardianPolicy;
import me.copimine.endevent.domain.SmallKagunePolicy;
import me.copimine.endevent.runtime.TentacleController;

public class TentacleHitAdapterProbe {
    enum EventPhase { BOSS_ACTIVE }
    enum Sound { BLOCK_RESPAWN_ANCHOR_DEPLETE, BLOCK_AMETHYST_BLOCK_RESONATE }
    enum SoundCategory { HOSTILE }
    static final class World {
        int sounds;
        void playSound(Object loc, Sound sound, SoundCategory category, float volume, float pitch) {
            sounds++;
        }
    }
    static final class ItemDisplay {
        final UUID id = new UUID(0, 1);
        final World world = new World();
        double health = 60;
        UUID getUniqueId() { return id; }
        World getWorld() { return world; }
        Object getLocation() { return this; }
    }
    static final class Player {
        final UUID id = new UUID(0, 2);
        UUID getUniqueId() { return id; }
    }
    static final class EntityDamageByEntityEvent {
        final ItemDisplay target;
        final Object attacker;
        double finalDamage = 10;
        boolean cancelled;
        EntityDamageByEntityEvent(ItemDisplay target, Object attacker, boolean cancelled) {
            this.target = target; this.attacker = attacker; this.cancelled = cancelled;
        }
        ItemDisplay getEntity() { return target; }
        Object getDamager() { return attacker; }
        boolean isCancelled() { return cancelled; }
        void setCancelled(boolean value) { cancelled = value; }
        double getDamage() { return 10; }
        double getFinalDamage() { return finalDamage; }
    }
    static class Projectile {
        final Player shooter;
        boolean removed;
        Projectile(Player shooter) { this.shooter = shooter; }
        Player getShooter() { return shooter; }
        void remove() { removed = true; }
    }
    static final class AbstractArrow extends Projectile {
        AbstractArrow(Player shooter) { super(shooter); }
        double getDamage() { return 10; }
    }
    static final class ProjectileHitEvent {
        final ItemDisplay target;
        final Projectile projectile;
        boolean cancelled;
        ProjectileHitEvent(ItemDisplay target, Projectile projectile, boolean cancelled) {
            this.target = target; this.projectile = projectile; this.cancelled = cancelled;
        }
        ItemDisplay getHitEntity() { return target; }
        Projectile getEntity() { return projectile; }
        boolean isCancelled() { return cancelled; }
        void setCancelled(boolean value) { cancelled = value; }
    }
    static final class Config { String environment() { return "local"; } }

    final Config config = new Config();
    final ItemDisplay display = new ItemDisplay();
    final Player player = new Player();
    final TentacleController tentacleController = new TentacleController();
    final Map<Integer, Double> tentacleGuardianHealth = new HashMap<>();
    final Map<Integer, Long> tentacleGuardianRespawnDeadlineMillis = new HashMap<>();
    final Set<Integer> tentacleGuardianWarnedSlots = new HashSet<>();
    final Map<UUID, Long> tentacleNextAttackTick = new HashMap<>();
    final Object keyTentacleHealth = new Object(), keyTentacleHealthState = new Object();
    final double TENTACLE_TEMPORARY_MAX_HEALTH = 20;
    final String eventId = "hit-window-probe";
    final long generation = 17, tentacleActiveMillis = 0;
    final EventPhase phase = EventPhase.BOSS_ACTIVE;
    BossPhase bossPhase = BossPhase.LAST_SEAL;
    int visualUpdates, stateChanges;

    TentacleHitAdapterProbe() {
        tentacleController.begin(generation);
        require(tentacleController.register(generation, display.id,
                TentacleAnimationPolicy.Kind.PERMANENT, 0, 0,
                TentacleAnimationPolicy.State.SHIELD_CHANNEL), "registration failed");
        tentacleGuardianHealth.put(0, 60.0);
    }
    ItemDisplay tentacleDisplayForHitbox(ItemDisplay hitbox) { return hitbox; }
    boolean isActiveBossParticipant(Player attacker) { return attacker == player; }
    Player playerDamageOwner(Object source) {
        return source instanceof Player p ? p : source instanceof Projectile p ? p.shooter : null;
    }
    boolean tentacleDamageWindowOpen() { return true; }
    int eventScalePlayers() { return 2; }
    record SmallTuning(double health) { }
    SmallTuning smallKaguneTuning() { return new SmallTuning(12); }
    int tentacleRespawnDelayTicks() { return 1800; }
    double readDouble(ItemDisplay item, Object key, double fallback) { return item.health; }
    String readString(ItemDisplay item, Object key) { return "updated"; }
    Logger getLogger() { return Logger.getAnonymousLogger(); }
    void updateTentacleGuardianHealthVisual(ItemDisplay item, double hp, double maximum) {
        visualUpdates++; item.health = hp;
    }
    void setTentacleState(ItemDisplay item, TentacleAnimationPolicy.State state, UUID target) {
        stateChanges++;
        require(tentacleController.transition(generation, item.id, state, 1, target),
                "state transition failed");
    }
''' + methods + r'''
    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    void acceptedFirstHit() {
        onTentacleGuardianDamage(new EntityDamageByEntityEvent(display, player, false));
        require(tentacleGuardianHealth.get(0) == 50.0, "first hit must deduct 10 HP");
        require(tentacleController.state(display.id).state()
                == TentacleAnimationPolicy.State.HIT_RECOVERY, "first hit enters recovery");
    }
    void assertUnchanged(String reason) {
        require(tentacleGuardianHealth.get(0) == 50.0,
                reason + " must not drain HP; actual=" + tentacleGuardianHealth.get(0));
        require(tentacleController.state(display.id).state()
                == TentacleAnimationPolicy.State.HIT_RECOVERY, reason + " must not restart animation");
        require(visualUpdates == 1 && stateChanges == 1 && display.world.sounds == 1,
                reason + " must not emit a second hit reaction");
    }
    public static void main(String[] args) {
        var probe = new TentacleHitAdapterProbe();
        switch (args[0]) {
            case "reduced-final-damage" -> {
                var hit = new EntityDamageByEntityEvent(probe.display, probe.player, false);
                hit.finalDamage = 2;
                probe.onTentacleGuardianDamage(hit);
                require(probe.tentacleGuardianHealth.get(0) == 58.0,
                        "reduced final damage must not be replaced by raw damage");
            }
            case "zero-final-damage" -> {
                var hit = new EntityDamageByEntityEvent(probe.display, probe.player, false);
                hit.finalDamage = 0;
                probe.onTentacleGuardianDamage(hit);
                require(probe.tentacleGuardianHealth.get(0) == 60.0 && probe.visualUpdates == 0,
                        "fully blocked damage must have no custom health or hurt effect");
            }
            case "projectile-transaction" -> {
                var arrow = new AbstractArrow(probe.player);
                probe.onTentacleGuardianProjectile(new ProjectileHitEvent(probe.display, arrow, false));
                require(probe.tentacleGuardianHealth.get(0) == 60.0 && !arrow.removed,
                        "collision must wait for the native damage/protection transaction");
                var hit = new EntityDamageByEntityEvent(probe.display, arrow, false);
                hit.finalDamage = 3;
                probe.onTentacleGuardianDamage(hit);
                require(probe.tentacleGuardianHealth.get(0) == 57.0,
                        "player-owned projectile final damage must be accepted exactly once");
            }
            case "melee-recovery" -> {
                probe.acceptedFirstHit();
                probe.onTentacleGuardianDamage(new EntityDamageByEntityEvent(
                        probe.display, probe.player, false));
                probe.assertUnchanged("melee during HIT_RECOVERY");
            }
            case "projectile-recovery" -> {
                probe.acceptedFirstHit();
                probe.onTentacleGuardianProjectile(new ProjectileHitEvent(probe.display,
                        new AbstractArrow(probe.player), false));
                probe.assertUnchanged("projectile during HIT_RECOVERY");
            }
            case "cancelled-melee" -> {
                var hit = new EntityDamageByEntityEvent(probe.display, probe.player, true);
                probe.onTentacleGuardianDamage(hit);
                require(probe.tentacleGuardianHealth.get(0) == 60.0 && probe.visualUpdates == 0
                        && probe.stateChanges == 0 && probe.display.world.sounds == 0,
                        "previously cancelled melee changed guardian state");
            }
            case "cancelled-projectile" -> {
                var projectile = new AbstractArrow(probe.player);
                probe.onTentacleGuardianProjectile(new ProjectileHitEvent(probe.display,
                        projectile, true));
                require(probe.tentacleGuardianHealth.get(0) == 60.0 && probe.visualUpdates == 0
                        && probe.stateChanges == 0 && probe.display.world.sounds == 0
                        && !projectile.removed, "previously cancelled projectile changed state");
            }
            default -> throw new AssertionError("unknown scenario: " + args[0]);
        }
    }
}
''',
        encoding="utf-8",
    )
    compiled = subprocess.run(
        ["javac", "-proc:none", "-encoding", "UTF-8", "-sourcepath", str(SOURCE_ROOT),
         "-d", str(directory), str(probe)],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return directory


@pytest.mark.parametrize(
    "scenario",
    ["melee-recovery", "projectile-recovery", "cancelled-melee", "cancelled-projectile",
     "reduced-final-damage", "zero-final-damage", "projectile-transaction"],
)
def test_authoritative_hit_window_and_prior_cancellation(tentacle_hit_probe, scenario):
    executed = subprocess.run(
        ["java", "-cp", str(tentacle_hit_probe), "TentacleHitAdapterProbe", scenario],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    assert executed.returncode == 0, executed.stdout + executed.stderr
