# Last Seal Kagune Tentacles — Design

Date: 2026-09-25

## Intent

Replace the old production tentacle visual/runtime assumptions with the new user-supplied `kagune` model and authored animation set while preserving server-authoritative gameplay, generation safety, boss/tentacle ownership boundaries, and deterministic cleanup.

This design supersedes the segmented `root/segment_1/.../tip` visual contract in the earlier combined End Rift architecture spec. The broader Waves and Boss architecture remains unchanged.

## Source asset contract

The new art package contains one complete animated model:

- `kagune.bbmodel`
- `skins/kagune_texture.png` — 64x64 RGBA PNG
- bones/groups: `1layer`, `1layer2`, `2layer`, `2layer2`, `3layer`, `3layer2`
- animations:
  - `idle` 4.0 s
  - `emerge` 1.0 s
  - `telegraph_grab` 1.25 s
  - `grab_success` 0.8333 s
  - `hold` 1.2083 s
  - `trow` 0.875 s
  - `grab_miss` 0.8333 s
  - `hurt` 0.25 s
  - `death` 1.25 s
  - `retract` 0.875 s
  - `spawn_under_player` 0.4167 s
  - `shield_channel` 0.4167 s

The asset ID `trow` is preserved as supplied. Server semantics may call the gameplay state `THROW`, but the client mapping targets the asset clip `trow`.

The successful authored chain is continuous:

```text
telegraph_grab -> grab_success -> hold -> trow
```

The final pose of each prior clip matches the initial pose of the next for the shared authored channels. This continuity is part of the contract.

`grab_miss` does not begin from the telegraph end pose, so the client uses a short bounded 4-6 tick blend instead of a one-frame snap.

## Architecture boundary

```text
BossLastSealController
        |
        v
TentacleGateway
        |
        v
EndRiftTentacleController
        |
        +-- GuardianTentacleRuntime
        +-- AmbushTentacleRuntime
        +-- TentacleClipContract
        +-- TentacleGameplayGeometry
        +-- TentacleGrabController
        +-- TentacleDamagePolicy
        +-- TentacleDefeatLedger
        +-- AmbushScheduler
        +-- TentaclePresentationBridge
```

Client:

```text
TentaclePresentationBridge
        |
        v
EndRiftTentacleClientState
        |
        +-- KaguneModel
        +-- KaguneClipLibrary
        +-- KaguneAnimationPlayer
        +-- EndRiftTentacleRenderer
```

The boss owns shield, regen and boss damage admission. Tentacles emit semantic Guardian defeat events only.

The client owns presentation only. It never decides contact, hold, damage, throw, vulnerability or objective completion.

## One logical tentacle = one complete Kagune model

A Guardian or Ambush tentacle is one logical runtime instance and one complete rendered `kagune` model.

The previous visible five-segment carrier/rig model is retired from production. A single hidden carrier/anchor entity may remain per logical tentacle if useful for world identity and bridge integration.

The new model's six bones are visual animation bones, not server gameplay segments.

## Gameplay geometry

Because the new art has no gameplay socket bone, contact/hold geometry is server-owned and independent of decorative bones.

One central geometry profile owns:

- render scale;
- interaction radius/height;
- attack reach;
- grab radius;
- vertical tolerance;
- authoritative hold anchor offsets;
- damage proxy dimensions;
- spawn depth;
- arena-safe throw clamp.

The hold anchor is calibrated live so the player visually sits inside the authored hold pose.

## Animation timing

The shared semantic-to-asset mapping is:

```text
READY          -> idle
EMERGING       -> emerge
SHIELD_CHANNEL -> shield_channel
TELEGRAPH_GRAB -> telegraph_grab
GRAB_SUCCESS   -> grab_success
HOLD           -> hold
THROW          -> trow
MISS_RECOVERY  -> grab_miss
HIT_REACTION   -> hurt
DYING          -> death
RETRACT        -> retract
SPAWN_UNDER_PLAYER -> spawn_under_player
```

Approximate 20 TPS timings:

- idle 80 ticks
- emerge 20
- telegraph_grab 25
- grab_success 17
- hold 24
- trow 18
- grab_miss 17
- hurt 5
- death 25
- retract 18
- spawn_under_player 8
- shield_channel 8

Gameplay timing is derived from this shared clip contract rather than the old procedural durations.

Contact occurs when `telegraph_grab` completes. If the target is no longer in the valid grab volume, the tentacle misses. If still valid, it enters `grab_success`.

The THROW release marker is synchronized near the strongest authored release motion at approximately 0.7917 seconds into `trow`, around tick 16.

## Guardian lifecycle

```text
EMERGING
 -> CHANNELING
 -> TARGETING
 -> TELEGRAPH_GRAB
    -> success: GRAB_SUCCESS -> HOLD -> THROW
    -> miss: MISS_RECOVERY
 -> VULNERABLE
 -> RECOVERY
 -> CHANNELING
```

Death:

```text
VULNERABLE -> DYING -> DEAD
```

Guardians are protected outside the explicit vulnerability window. The window is about 1.5-2.0 seconds.

A successful grab creates authoritative HeldPlayerState. The player is smoothly pulled to the hold anchor, briefly held, then released during `trow`.

Throw uses independently tuned horizontal XZ and vertical Y components. Initial tuning target:

- horizontal 1.10-1.25
- vertical 0.65-0.80

After release, the player gets 2-3 seconds of anti-regrab protection.

## Guardian count and durability

Primary design:

- 1-2 players: 2 Guardians
- 3-4 players: 3 Guardians
- 5-6 players: 4 Guardians

For larger QA rosters, keep the Guardian count capped at 4 unless an already-established broader event contract requires otherwise. Prefer bounded HP/cooldown scaling over visual spam.

Normal 5-6 player target: roughly 2-4 good vulnerability windows to kill one Guardian.

## Permanent defeat

The old Guardian respawn design is removed.

A generation-scoped logical defeat ledger owns Guardian slots. On lethal damage:

1. mark slot DEFEATED immediately;
2. release any held player;
3. play `death`;
4. remove visual/runtime instance;
5. never recreate that slot in the current generation.

The old `DEAD_RESPAWN`, 40-second Guardian respawn and temporary all-dead damage-window cycle are not part of the new design.

Each Guardian defeat emits `GUARDIAN_DEFEATED(slot)`. Final defeat emits `ALL_GUARDIANS_DEFEATED` exactly once. Boss Last Seal logic permanently breaks shield and disables regen.

## Ambush lifecycle

Ambush uses the same model but is a separate temporary hazard:

```text
WARNING
 -> SPAWN_UNDER_PLAYER
 -> CONTACT_CHECK
    -> hit: GRAB_SUCCESS -> HOLD -> THROW
    -> miss: GRAB_MISS
 -> RETRACT
 -> DONE
```

Ambush has no objective HP.

Concurrency:

- 1-4 players: max 1 active Ambush
- 5-6 players: max 2 active Ambushes on different targets when possible

One central scheduler owns Ambush spawning. Post-throw-protected or already-held players are invalid targets.

## Client integration

The new renderer displays the actual supplied `kagune` geometry and 64x64 texture. It does not also render the old generated segmented/claw body.

Do not turn this stage into a general GeckoLib/Blockbench importer project. Reuse an existing safe loader if available; otherwise build the smallest fixed-format adapter required for this known six-bone model and its supplied animation JSON.

The client receives semantic state + start time + target context and evaluates the clip locally. It does not receive per-bone transforms every tick.

Finite clips may be repeated at the gameplay-state level for READY, SHIELD_CHANNEL and HOLD as needed; source files do not need to be rewritten to declare loops.

## Cleanup and failure handling

Every callback is generation-fenced.

On reset/abort/boss defeat/generation replacement:

1. stop Ambush scheduler;
2. release held players;
3. cancel contact/throw callbacks;
4. remove runtime instances/carriers/proxies;
5. clear client bindings;
6. clear post-throw protections and target ownership.

Cleanup is idempotent.

Disconnect or death while held immediately removes ownership and prevents later stale throws.

## Verification

Required automated coverage:

- asset contract: exact six bones, 64x64 texture, 12 clips, finite data;
- successful animation continuity;
- miss blend path;
- new clip durations and semantic mapping;
- permanent defeat/no-respawn;
- vulnerability admission;
- HeldPlayerState lifecycle;
- throw vector policy;
- Ambush success/miss/concurrency;
- generation fencing;
- client render safety.

Required live verification:

- ~4.5-5 block Guardian scale;
- emerge;
- shield channel;
- readable 1.25 s telegraph;
- successful grab continuity;
- hold alignment;
- dodgeable miss;
- throw release synchronized to animation;
- readable vulnerability;
- death and no respawn after old delay;
- permanent boss shield break after final Guardian;
- Ambush path;
- reset while holding/throwing/dying/Ambush active.

## Deferred work

General boss/model UV importer redesign remains explicitly outside this task.
