# End Rift Event — completion and fidelity addendum

**Status:** implementation authorized by the continuing user goal on 2026-08-24.

**Base specification:** `docs/superpowers/specs/2026-08-17-end-rift-event-design.md` and the attached v5 prompt.

## Purpose

Close the remaining gap between the current End Rift implementation and the later user decisions. This addendum does not replace the state machine, reward, WorldCore, Artifacts, launcher, website, or production-safety boundaries of the base specification.

## Binding decisions from the later user requests

- Wave spiders replace Endermites. No Endermite is spawned by any configured or test wave.
- The official boss has 1200 maximum health, 600 half-health threshold, 120 final threshold, and 200 post-drain health.
- Event entity visuals are opt-in and UUID-scoped. Ordinary vanilla Endermen, spiders, shulkers, and other mobs keep their vanilla appearance.
- The event visual set includes distinct original textures for common Endermen, elite Endermen, spiders, shulkers, and the Rift Guardian. The client renderer binds only entities explicitly announced by the server for the current event generation.
- The Core overlay is anchored to the exact protected block. Runes are floor overlays at the exact top face of their pad blocks, occupy the full top surface, and switch to the occupied model while a valid participant stands on them.
- Official pad rules continue to exclude Creative and Spectator. A local admin test runner may drive all phases while the operator is in Creative, without registering the operator as an official participant or changing production gameplay rules.
- Every spell that is described as flying must emit a visible, bounded particle flight from caster to the fixed target before applying its effect. No spell may damage blocks.

## Gate / portal passage contract

The command surface becomes:

```text
/cmend gate pos1
/cmend gate pos2
/cmend gate info
/cmend gate preview
/cmend gate open [ticks-per-layer]
/cmend gate restore confirm
```

`pos1` and `pos2` capture block coordinates in one world. The volume is inclusive and bounded to the existing safe maximum. `info` reports both points, volume, current status, snapshot count, and opening progress.

`open` first durably snapshots the exact original `BlockData` for every block in the cuboid, then removes the blocks one horizontal Y-layer at a time from greatest Y to least Y. Within a layer, processing order is deterministic. Each layer produces a short purple/black particle burst and a bounded sound; the operation never scans outside the configured cuboid and never modifies blocks not present in the durable snapshot. A save failure stops before further mutation.

The statuses are `UNSET`, `PREVIEW`, `OPENING`, `OPENED`, `RESTORED`, and `RESTORED_ON_BOOT`. A restart during `OPENING` restores the complete snapshot and marks the gate `RESTORED_ON_BOOT`; it never resumes from an ambiguous partial mutation. `restore confirm` restores only the saved gate blocks and never scans the world. Victory invokes the same idempotent opening operation and records the victory step only after all layers are removed.

## Full local validation contract

The isolated Creative test scenario must produce direct evidence for:

1. Core placement and exact overlay anchoring.
2. Resource deposit, Russian material-colored progress text, charged core, and rune rebuild.
3. Occupied/unoccupied rune model changes at the exact pad surface.
4. Arena boundary particles for the configured 20-block horizontal and 3-block vertical bounds.
5. Countdown and all wave compositions, including spiders and custom entity visual binds.
6. Mini-boss one-spell behavior, boss flight particles, BossBar, target containment, and 1200 HP thresholds.
7. Half-health heal/control phase, final 60% current-health drain without death, final wave, and finish.
8. Core destruction cleanup of all current and stale event entities/projectiles/displays.
9. Gradual two-point gate opening, top-down ordering, restart rollback during opening, and victory idempotency.
10. Resource-pack rendering for Core, charged Core, idle/occupied rune, Rift Shard, each wave mob, and the boss in a real client or the strongest available local client harness. If a real GUI client is unavailable, the report must mark GUI observations separately from protocol/resource assertions.

## Safety and release boundary

All tests use the linked worktree and disposable local Paper/PostgreSQL runtime only. Launcher, website, production server, production worlds, production databases, and deployment scripts remain untouched. The branch is pushed to `origin/codex/end-rift-event` only after fresh source, build, asset, and local-runtime evidence is captured.
