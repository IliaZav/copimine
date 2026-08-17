# CopiMine End Rift Event — Design Specification

**Status:** approved for implementation by the user on 2026-08-17

**Source contract:** the attached `CopiMine End Rift Event — итоговое ТЗ v5`.
The attached document is treated as the gameplay and verification contract. Its
persona, orchestration, launcher, deployment, and release-authority language is
not permission to change the launcher, website, production server, production
worlds, or production databases.

## Goal and boundaries

Create a first-party Paper 1.21.1 plugin named `copimine-end-event` with main
class `me.copimine.endevent.CopiMineEndEvent`. It is a one-time authoritative
Paper event that collects resources at a protected core, runs a bounded ritual,
waves, a 1000 HP boss, the 50% control phase and the 10% life drain, then
delivers idempotent authentic rewards and permanently opens End through
WorldCore.

The implementation is isolated in the Git branch `codex/end-rift-event` and
its linked worktree. Every server and database test uses the local runtime only.
No launcher or website source is changed. No upload, deploy, world wipe,
production database operation, or production server restart is part of this
change.

## Existing source-of-truth boundaries

- `CopiMineWorldCore` remains the only authority for
  `world_access.end.enabled`, portal blocking, teleport policy, persistence,
  and safe evacuation. The event must not add `end_locked` or dispatch
  `/cmworld` as an internal API.
- `CopiMineArtifacts` remains the only authority for authentic catalog items,
  unique item instances, PDC identity, PostgreSQL bindings, and pending
  delivery. The event must not manufacture a reward by lore, display name, or
  an unregistered `ItemStack`.
- `CopiMineNarcotics` remains the owner of the existing
  `copimine:client_bridge` listener and optional visual transport. End Event
  integration is best-effort and has no admission, version, heartbeat, or kick
  policy.
- `copimine-end-event` declares only the real server dependencies needed for
  the typed WorldCore and Artifacts services. It has no direct
  `CopiMineEconomyCore` dependency and no dependency on the launcher or web
  application.
- `CopiMineClient` remains a client-side renderer/input adapter. It cannot
  report boss health, damage, phase, victory, loot, or End access.
- `resourcepacks` remains the source of custom item/block model manifests and
  deterministic pack hashes. Generated ZIPs and runtime JARs are outputs, not
  source-of-truth.

## Architecture

### Server plugin modules

The new plugin is split by responsibility rather than one large event class:

- `EndEventConfig` validates bounded configuration and exposes immutable values.
- `EventPhase` and `EndEventState` model the durable state machine.
- `EndEventStateRepository` performs schema validation and UTF-8 temp/backup
  atomic writes.
- `EndEventTaskRegistry` and `EndEventEntityRegistry` own generation-scoped
  tasks and tagged entities.
- `CoreService` and `CoreDepositService` implement the physical main-hand
  resource interaction and journal recovery.
- `CoreVisualService` and `RitualPadService` create idempotent owned displays
  and exactly N pads.
- `ParticipantRegistry` snapshots the immutable official roster at the
  successful countdown transition and separately records late participants.
- `ArenaProtectionService` and `GateService` protect only configured bounded
  regions and restore only their own snapshots.
- `WaveService`, `WaveLootService`, and `BossController` manage registered
  waves, bounded loot, target rotation, containment, damage, BossBar, and the
  official/test distinction.
- `BossSpellController` owns one scheduling loop for VOID_BLAST,
  RIFT_PROJECTILE, VOID_MARK, SUMMON_SERVANTS, and WILL_DISTORTION.
- `FinalDrainService` owns the one-shot 60% current-real-health drain.
- `VictoryService` runs the idempotent reward/core/gate/arena/WorldCore/
  announcement saga.
- `WorldCoreBridge`, `ArtifactsRewardBridge`, and `EndEventClientBridge` are
  typed integration adapters; the controller never reads another plugin's
  mutable maps.
- `EndEventCommand` is the only admin control surface. No AdminHub GUI and no
  `/cmend clientgate` exist.

### Typed cross-plugin contracts

WorldCore exposes a public API under `me.copimine.worldcore.api`:

```java
public interface WorldAccessService {
    WorldAccessResult setEndEnabled(boolean enabled, String actor, String idempotencyKey);
    boolean isEndEnabled();
    boolean isEndWorld(World world);
}

public record WorldAccessResult(boolean success, boolean changed, String code, String message) {}
```

The existing `/cmworld end open|close` command delegates to the same service
implementation. `setEndEnabled(true, ...)` is idempotent, persists
`world_access.end.enabled`, never changes Nether, and survives restart.

Artifacts exposes a public API under `me.copimine.artifacts.api`:

```java
public interface EventArtifactRewardService {
    CompletionStage<RewardIssueResult> issueWorldDrop(EventArtifactRewardRequest request);
    CompletionStage<RewardIssueResult> issueToPlayer(EventArtifactRewardRequest request);
}

public record EventArtifactRewardRequest(
        String itemId,
        String eventId,
        UUID recipient,
        World world,
        Location location,
        String source,
        String idempotencyKey
) {}

public record RewardIssueResult(
        RewardIssueStatus status,
        String purchaseId,
        String uniqueItemId,
        String idempotencyKey,
        String detail
) {}

public enum RewardIssueStatus { CREATED, ALREADY_ISSUED, PENDING, FAILED }
```

The implementation uses the existing durable instance/pending-delivery
tables and only performs Bukkit inventory/world mutation on the main thread.
The event uses these exact keys:

```text
end-event:<eventId>:boss:return-stone
end-event:<eventId>:participant:<playerUuid>:rift-core-shard
```

The shard key always includes a roster UUID; no event-wide shard key exists.

The existing client bridge is extended through a typed service registered by
Narcotics, under `me.copimine.narcotics.api`:

```java
public interface EndEventClientService {
    void bindEntityVisual(Collection<Player> viewers, UUID entityUuid,
                          String eventId, long generation, String visualId);
    void unbindEntityVisual(Collection<Player> viewers, UUID entityUuid,
                            String eventId, long generation);
    void startControlEffect(Player player, ControlEffectRequest request);
    void stopControlEffect(Player player, UUID effectInstanceId, String reason);
}

public record ControlEffectRequest(
        UUID effectInstanceId,
        String eventId,
        long generation,
        String effectType,
        long durationMillis,
        long serverEndsAtMillis,
        UUID targetPlayerUuid
) {}
```

This service has no capability validation, required version, HELLO wait,
admission mode, kick method, or denial result. If Narcotics is unavailable,
the event remains authoritative and records only diagnostic status.

## State and recovery

The state enum is:

```text
UNCONFIGURED, COLLECTING, READY_FOR_PLAYERS, COUNTDOWN,
WAVE_1, INTERMISSION_1, WAVE_2, INTERMISSION_2, WAVE_3,
BOSS_ACTIVE, FINAL_RITUAL, FINAL_WAVE, BOSS_FINISH,
VICTORY, UNLOCKED, RECOVERY_REQUIRED
```

Every transition is main-thread, checks expected phase/eventId/generation,
uses an idempotency key, writes durable state, and records an audit entry.
Illegal transitions are rejected without partial side effects.

The persisted YAML snapshot includes `schemaVersion`, `eventId`,
`generation`, `phase`, core location, required player count, resource
requirements/progress/contributions, arena and gate bounds/snapshot status,
portal room, participant names/UUIDs, immutable `officialRewardRoster`,
`halfHealthTriggered`, `controlSpellUnlocked`, `finalDrainTriggered`,
`finalDrainApplied`, drained UUID snapshot, boss/core/reward statuses, victory
step statuses, announcement status, `endUnlocked`, and timestamps.

It never serializes Bukkit tasks, live entities, BossBar, projectiles, current
target, or exact boss HP. State is written as UTF-8 to a temp file, moved
atomically when supported, and copied to a backup. Invalid or corrupted state
enters `RECOVERY_REQUIRED` and does not guess or unlock.

On startup, `UNLOCKED` is terminal and never relocks End. A transient phase
(`COUNTDOWN`, any wave, `BOSS_ACTIVE`, `FINAL_RITUAL`, `FINAL_WAVE`, or
`BOSS_FINISH`) is reconciled by cancelling tasks, removing only current
generation tagged entities/displays, preserving resources/config/roster where
durable, and returning to `READY_FOR_PLAYERS`. A `COUNTDOWN -> WAVE_1`
transition only fixes the roster if both the roster and transition were
durably committed.

Every owned entity/display has PDC markers:

```text
end_event_id, end_event_generation, end_event_kind,
end_event_wave, end_event_loot_profile, end_event_official
```

Stale generation entities, tasks, projectiles, control packets, and displays
are ignored or cleaned without touching unrelated content.

## Core, deposits, pads, and countdown

`/cmend core set N` stores a protected anchor, validates N against configured
limits, computes N pads all-or-nothing at `baseAngle + 360*i/N` using candidate
radii 5/6/7/8, and creates the uncharged core ItemDisplay plus one progress
TextDisplay. No inventory GUI or chest is used.

The only physical deposit path is `PlayerInteractEvent` with
`RIGHT_CLICK_BLOCK` and `EquipmentSlot.HAND`. The handler rejects cancelled
events, offhand, wrong block/world/phase, spectator, empty hand, official
artifact PDC (`artifact_item_id`, `artifact_unique_item_id`, AR/donation or
custom protection), and non-allowlisted materials. It accepts
`min(heldAmount, remaining)` and leaves the exact remainder in the main hand.
The interaction is cancelled after recognition so the anchor cannot open a
vanilla UI.

The bounded deposit journal has `PREPARED`, `ITEM_REMOVED`, `COMMITTED`,
`REFUND_PENDING`, and `REFUNDED` records. Its key includes player, event,
generation, core, and server tick. A failed state write refunds the exact
amount to the main hand or a durable local pending refund; it never increases
progress. Recovery cancels PREPARED, refunds uncommitted ITEM_REMOVED, and
leaves COMMITTED unchanged.

After the last requirement, the state becomes `READY_FOR_PLAYERS`, the core
changes to charged, the progress text disappears, and exactly N non-collidable
owned pads are active. Pad occupancy is scanned every five ticks and maps each
eligible survival/adventure, alive, non-spectator/non-creative player to one
nearest unassigned pad in the same world.

When all N pads have distinct eligible occupants, a 60-second countdown starts.
Leaving a pad, death, quit, or world change resets to `READY_FOR_PLAYERS`.
At successful completion, exactly N UUIDs are atomically written as the
immutable official roster before transitioning to `WAVE_1`. Late helpers can
fight and appear in the general participant announcement but never receive a
roster shard.

The configured portal room is a preparation room with a safe route, beds,
chests, and an Ender Chest when those blocks are included in the bounded
admin-configured room. Ordinary deaths use vanilla bed respawn; there is no
sacrifice death, death compensation, or special respawn path.

## Arena, gate, waves, and loot

Arena and gate are bounded cuboids configured through `/cmend arena` and
`/cmend gate`. Before victory, owned core/pads/arena are protected against
break, explosions, pistons, fluids, fire, replacement, hopper automation, and
display removal. After victory only the event-owned protection is removed.
Gate snapshots are bounded and restore only event-owned changes.

The default waves are:

```text
W1: 5 Endermen, 8 Endermites
W2: 7 Endermen, 3 Shulkers, 6 Endermites
W3: 10 Endermen, 4 Elite Endermen, 3 Shulkers
FINAL: 6 Elite Endermen, 8 Endermites, 2 Shulkers
```

Counts scale by `clamp(N / 5.0, 0.8, 2.0)` under a hard total cap of 48.
Each event mob is registered by UUID/event/generation/wave/loot profile.
Unrelated natural mobs never complete a wave. Endermen are contained within
10 blocks of the core, cannot pick up blocks, and rotate living participant
targets every 5–8 seconds. Teleport events and a 10–20 tick watchdog enforce
bounded containment without force-loading chunks. Admin test waves use the
same configured additive loot but never advance the official phase.

Loot profiles are deterministic, one roll per mob, configurable, and bounded.
They include the v5 Endermite/Common Enderman/Elite Enderman/Shulker bonus
profiles and never create duplicate rewards on death/removal.

## Boss and spells

The official boss is a persistent Enderman named `Хранитель Разлома` with
maximum/current health 1000, no far-away removal, a 15-block core radius, and
a BossBar. It receives exactly +3 to its base attack attribute without
stacking modifiers or Strength. Its melee target rotates every 5–8 seconds
among eligible players.

One spell controller schedules the four base spells without immediate repeat:

- `VOID_BLAST`: two-second telegraph, radius 4, damage 8, knockback, Weakness
  I for five seconds, no block damage.
- `RIFT_PROJECTILE`: tagged purple projectile, damage 7, Slowness II for four
  seconds, one hit, timeout, no block damage.
- `VOID_MARK`: three-second warning, fixed radius-3 zone for six seconds,
  damage 2 once per second, brief Blindness/Slowness, maximum two zones.
- `SUMMON_SERVANTS`: bounded tagged adds near 70% and 35%, subject to caps and
  the same containment/loot rules.

At the first projected crossing at or below 500 HP, `halfHealthTriggered` is
persisted before effects. Spells pause briefly, living eligible players in the
arena are healed to current max health, dead players are not resurrected, and
`controlSpellUnlocked` is persisted. The one-time `WILL_DISTORTION` spell then
becomes eligible.

WILL_DISTORTION selects one online living survival/adventure player in the
arena, avoids an active effect and repeats when alternatives exist, shows a
30-tick telegraph, and sends a 10-second effect. In the final implementation
the phrase “validated compatible client” means only that a best-effort packet
may be sent to the player; absence of CopiMineClient never blocks the player,
spell, phase, or event. The client transforms only movement axes W/S and A/D;
mouse, attack/use, jump, sneak, inventory, chat, and hotbar are untouched.

At the first projected hit at or below 100 HP, damage is cancelled or clamped,
the boss is set to exactly 100 HP, and `finalDrainTriggered` is persisted
before side effects. The phase becomes `FINAL_RITUAL`, boss invulnerability and
normal spells/targeting begin, active control is stopped, boss returns to a
safe core point, and the final wave starts once. Purple/black particles run
from every eligible living player physically inside the arena to the core and
then the boss for a bounded telegraph.

At most once, each eligible player loses 60% of current real health using
`max(1.0, before * 0.40)`, clamped to max health. No damage event, death,
totem, item drop, respawn, or economy/donation transaction is triggered.
Players entering after the drain are not retroactively drained. With no
eligible targets the boss waits invulnerable. After applying the drain, the
boss is exactly 200/1000 HP and remains invulnerable until registered final
wave mobs are dead; then `BOSS_FINISH` removes invulnerability and players can
deal the last 200 HP. No second drain occurs.

Admin test bosses and test phases never unlock End or issue roster shards
unless explicitly spawned as the official boss of the active generation with
the required confirmation and phase.

## Rewards and victory saga

The official boss death is confirmed once. It guarantees one authentic
`return_stone` through Artifacts with key
`end-event:<eventId>:boss:return-stone`, bounded XP (default 5000 split into
at most 20 orbs), and the configured resource bundle. Death retries return
`ALREADY_ISSUED`.

`rift_core_shard` is a hidden/event-only authentic `ECHO_SHARD` catalog item
with its own model, unique instance, PDC and database binding. Right-click
channels for three seconds, cancels on movement/damage/world change, has a
player-scoped 60-minute cooldown, and teleports only to a configured safe
portal room after End is unlocked.

For each UUID in the immutable official roster, Victory issues exactly one
shard with the UUID-specific key. Online players with room receive the item;
full inventories and offline players use the existing durable pending delivery
mechanism. The total successful/pending shard count equals N and no late
participant receives a shard.

The saga steps are durable and idempotent:

```text
BOSS_DEATH_CONFIRMED
BOSS_REWARDS_RESERVED
BOSS_REWARDS_DELIVERED
PARTICIPANT_SHARDS_RESERVED
PARTICIPANT_SHARDS_DELIVERED
CORE_REMOVED
PADS_REMOVED
GATE_OPENED
ARENA_UNPROTECTED
WORLDCORE_END_OPENED
ANNOUNCEMENT_SENT
UNLOCKED_COMMITTED
```

The final steps remove the core/pads, open the configured gate, unprotect the
arena, call the typed WorldCore service with
`end-event:<eventId>:worldcore-end-open`, and announce once:

```text
ЭНД ОТКРЫТ
Хранитель Разлома пал. Путь в неизвестность свободен.
Хранитель Разлома повержен!
Энд открыт навсегда.
Участники: <unique cached names>
```

`UNLOCKED` survives restart and the saga resumes from its first incomplete
step without duplicating rewards or relocking End.

## Commands and configuration

Permissions are `copimine.endevent.admin` and `copimine.endevent.test`.
The complete command surface is:

```text
/cmend core set <requiredPlayers>
/cmend core info | rebuild | remove confirm
/cmend arena pos1 | pos2 | info | clear confirm
/cmend gate pos1 | pos2 | preview | restore
/cmend portalroom set | info
/cmend resources status | add <material> <amount> | reset confirm
/cmend status
/cmend ritual start | cancel confirm
/cmend cleanup
/cmend reset confirm
/cmend unlock confirm
/cmend wave spawn <1|2|3|final> | clear
/cmend boss spawn | spawn official confirm | info
/cmend boss damage <amount>
/cmend boss phase <normal|half|final>
/cmend boss kill <cleanup|simulate-victory confirm>
/cmend boss spell <void_blast|rift_projectile|void_mark|summon|control_reverse> [player]
/cmend client status <player> | bindboss <player> | clear <player>
```

The event config includes bounded versions of the v5 defaults: 60-second
countdown, 10-second intermissions, N from 1–20, pad radii 5/6/7/8, wave cap
48, wave radius 10, boss health 1000, +3 melee, boss radius 15, spell interval
6–9 seconds, half threshold 500, final threshold 100, post-drain health 200,
drain fraction 0.60, minimum health 1.0, control 10 seconds/cooldown 24
seconds/telegraph 30 ticks/max active 1, XP 5000/max 20 orbs, shard channel 3
seconds/cooldown 3600 seconds, and client IDs
`END_RIFT_GUARDIAN_V1` / `END_RIFT_CONTROL_REVERSAL_V1`.

The default resource requirements are exact vanilla main-hand materials and
amounts:

```yaml
resources:
  DIAMOND: {amount: 100, display: "Алмаз"}
  ENDER_EYE: {amount: 64, display: "Око Эндера"}
  AMETHYST_SHARD: {amount: 128, display: "Аметист"}
  BLAZE_ROD: {amount: 64, display: "Огненный стержень"}
```

The default boss resource bundle is bounded to diamonds 4–10, echo shards
2–5, netherite scraps 1–2, and ender pearls 8–16. Loot and XP are granted
only by the official boss reward saga or configured event-mob profiles; no
donation currency is involved.

Invalid ranges fail closed for ritual start, log a clear diagnostic, and never
mass-kick or mutate production state.

## Client protocol and assets

The existing `copimine:client_bridge` codec is extended compatibly with typed
server-to-client messages:

```text
end_boss_bind(entityUuid, eventId, generation, visualId)
end_boss_unbind(entityUuid, eventId, generation)
end_control_reverse_start(effectInstanceId, durationMillis,
                           serverEndsAtMillis, eventId, generation,
                           targetPlayerUuid)
end_control_reverse_stop(effectInstanceId, reason, eventId, generation)
end_effect_ack(effectInstanceId, status)  # diagnostics only
```

Messages are generation-scoped. Duplicate START with the same effect ID uses
the same absolute deadline and does not extend duration; an old STOP cannot
clear a newer effect. Client monotonic local expiry, disconnect, respawn,
world change, session change, and mod shutdown clear control state and all
entity bindings. Missing ACK is diagnostic only; no kick or admission path is
allowed.

The client renderer binds only the exact server-supplied boss UUID to
`copimine:textures/entity/end_rift_guardian.png`; unknown/stale UUIDs stay
vanilla. It must not allow a client packet to select an entity.

Create original, source-controlled assets with no internet copying or
placeholders:

```text
core uncharged model/texture
core charged model/texture
ritual pad rune model/texture
End Rift Guardian Enderman-compatible texture
Rift Core Shard item model/texture
```

The asset implementation records exact PNG dimensions/alpha, model paths,
CustomModelData IDs, manifest rows, deterministic resource-pack SHA-1/SHA-256,
and an in-game screenshot or equivalent render evidence. A bitmap generation
tool may be used for the original art, followed by local validation and
conversion to the repository's expected dimensions.

## Threading, performance, and safety

World/block/player/entity/inventory/PDC/health/teleport/display/BossBar and
phase changes are main-thread only. PostgreSQL, file/hash tooling, and pure
parsing are async. Async completions return to the main thread before Bukkit
mutation. No JDBC or inventory access occurs in tick loops.

There is one task registry, one spell controller, bounded arena scans, pad
checks every 5 ticks, containment every 10–20 ticks, hard entity/projectile/
zone caps, and bounded particles/XP. No world-wide scans, force-loaded remote
chunks, inventory scans every tick, or thousands of XP orbs.

Production safety invariants:

- only the isolated worktree and local Paper/PostgreSQL are mutable;
- event state and reward idempotency are durable before external side effects;
- no donation/economy credit path exists in End Event;
- fake/custom resources cannot be consumed;
- save failure refunds exact deposits;
- stale event generations cannot mutate current state;
- final drain cannot kill;
- test boss cannot unlock End;
- WorldCore remains the End source-of-truth;
- launcher/site/deploy/wipe scripts are not invoked.

## Verification contract

TDD begins with pure tests for transitions, recovery, pad geometry, deposit
amounts/guards/refunds, roster/shard cardinality, wave scaling/loot, target
rotation, spells, half trigger/control rules, final drain formula, victory
saga, and participant names. Client tests cover UUID binding, generation
filtering, axis-only inversion, idempotent deadlines, stale STOP, and all
cleanup paths. Contract tests reject GUI/admission/console-dispatch/fake-item
paths and require build/manifests/assets.

The release gate is evidence-based and local-only:

```text
focused tests PASS
full validators PASS
server plugin builds PASS
client tests/build PASS
resource pack build/inspection PASS
modpack build PASS where inputs are available
local Paper + PostgreSQL runtime PASS
Fabric runtime PASS where a launcher-managed client is available
restart matrix PASS
independent read-only verifier PASS
git diff --check PASS
no relevant ERROR/SEVERE
```

Any `FAIL`, `SKIPPED`, or `UNVERIFIED` requirement means the final decision is
exactly `NOT DEPLOYED — RELEASE GATE FAILED`. Production is never approved by
this task. The final report maps every v5 requirement to code, automated
evidence, runtime evidence, and PASS/FAIL/UNVERIFIED.
