# Wave 7 miniboss visuals, spell rework and atlas repair — production release 2026-11-14

Installed source: a7de87e1 (branch `codex/rift-wave7-visuals-20261114`, based on the
production lineage `04d8be93`).
Previous production release: c390f85f (plugin 9c37509d…, client c8d15631…, pack 40f569de…).

The user requested production installation and testing on the live server. Deployment,
artifact identity, service readiness and event-state safety are confirmed.
Miniboss runtime behaviour, boss-bar appearance, pose playback, spell silhouettes and
texture appearance on screen are **NOT VERIFIED** — they need a real client, and this
release deliberately refuses to run Wave 7 without one (see BLOCKED below).

## What this increment contains

- **Wave 7 miniboss boss bars.** Each named trial miniboss owns a dedicated Bukkit
  BossBar with an authored title, driven by `Wave7MinibossBossBarPolicy` (pure policy:
  `ownsBar`, `kindForTrialRole`, `title`, `progress`). No mod is required and no protocol
  change was made. The Warden bar is titled `Хранитель Бездны` because the client mixin
  suppresses titles prefixed `Хранитель Разлома` / `Страж Разлома`.
- **Wave 7 miniboss poses.** `Wave7MinibossPosePolicy` maps the authoritative cast
  receipt and the Warden action field onto poses, so a scripted miniboss (which runs
  with AI off) still animates. Poses are published with a lease
  (`leaseMillis`, `insertRelease`) rather than raw vanilla animation flags.
- **Spell visual rework.** One source of truth for spell appearance:
  `BossSpellVisualCompositionPolicy` owns colour and particle choice,
  `BossSpellGeometryPolicy` owns silhouettes (new shapes `RIFT_GATE`, `SNARE_CHAINS`,
  `ECHO_BLOOM`, `EUPHORIA_HELIX`). Both the telegraph and impact renderers read those
  tables, so a spell cannot drift into two different looks. No resource-pack or CMD
  change was made, deliberately.
- **Wave 7 atlas repair.** The three miniboss atlases had authored sheets that no longer
  matched their rigs; measured by sampling each face's real baked UV rectangle against
  the shipped PNG: Archmage sigil ring 0 %, Archmage rune hand 0 %, Warden shoulder
  shards 0 %, Marksman lower legs 0 %. `EndRiftAtlasUvLayout.fit` hid this by repacking
  low-coverage faces onto unrelated pixels, except `rune`/`sigil` paths, which it leaves
  transparent by design. The generator wrote into `resourcepacks/` (`parents[1]` instead
  of `parents[2]`), so the shipped PNGs were never regenerated. Fixed at the generator,
  repainted from the rigs' actual `texOffs`, and gated by a shared UV footprint
  (`resourcepacks/tools/wave7_uv_layout.py`).

## Installed artifacts

| Artifact | Bytes | SHA-256 | SHA-1 |
|---|---:|---|---|
| CopiMineEndEvent.jar | 2496283 | ef457be6142a2a2dbe4bcae3cb8a34aee7dabf37b0855c71ec1c926271e4e195 | 38ae52f514eae26fa52fce5f8b43c87adeb1dc85 |
| CopiMineClient-0.1.4+26.3.jar | 30091088 | 4f492998252ddb99b22429b9766a2d331994d4af4183f4e0518a25b0bfeaf452 | ab7169f9e4574af1fb8b9f546fec75d7c21aba7b |
| CopiMineResourcePack-26.3.zip (unchanged) | 34241093 | 40f569decb8f8f369ed15255f87b6e9e35f6c5848a09284a732f4ca1e172ee0c | — |

Both exported digests were taken at the source, re-verified on the uploaded staging copy,
and re-verified again on the installed file. The client jar rebuilds byte-identically
from a deleted output, so the pin is stable rather than a snapshot of `libs/`.

Client download: https://copimine.ru/assets/CopiMineClient-0.1.4+26.3-4f492998252ddb99.jar
(fetched over HTTPS: `200`, 30091088 bytes — exact size match).
The pack is byte-identical to the one already published, so it was not re-uploaded.
The site's download link is admin-assigned (`modUrl`), not derived from the filename, so
publishing the asset does not change what players are handed.

## Production evidence

- `systemctl is-active copimine-game.service` → `active`, new run started 18:31:31 +07.
- On disk: `plugins/CopiMineEndEvent.jar` is 2496283 bytes,
  sha256 `ef457be6…`, `copimine-game:copimine 644` — identity and ownership preserved.
- Startup log: `Client bridge signer is ready`, `Done (50.188s)`.
- `[CopiMineEndEvent] Enabling CopiMineEndEvent v0.1.0`, `End Event signed V4 bridge
  registered`, `END_EVENT_PHYSICAL_VISUALS …`, `END_EVENT_COMBAT_REINDEX … restored=4`.
- **Zero** `[ERROR]` / `SEVERE` / `Error occurred while enabling` lines since deploy.
  The only WARN is the pre-existing AuthMe `GeoLite2-Country.mmdb` download warning,
  unrelated to this release.
- Event state is untouched: `6294666a-c67d-4b40-82b4-d01bcc8706a0`, `phase=COLLECTING`,
  `generation=408`, `wave=0`, `roster=0`, `boss=none`, four existing displays.
  No gameplay actor was spawned by the deploy.

## Backups and rollback

Backup: `/opt/copimine-backups/rift-wave7-visuals-20261114` — full `runtime.tar`,
validated custom PostgreSQL `copimine.dump`, `globals.sql`, the replaced plugin jar,
`config.yml`, `server.properties`, and `checksums.json`.

Rollback: stop `copimine-game.service`; restore the backed-up
`plugins/CopiMineEndEvent.jar`, `plugins/CopiMineEndEvent/config.yml` and
`server.properties` with their original `copimine-game` ownership and modes; restart.
Republish the previous client as
`CopiMineClient-0.1.4+26.3-c8d15631ee742f4f.jar` if client rollback is also required.
Do not restore the database or world archive automatically over newer player progress.

## Verification status

| Area | Status | Basis |
|---|---|---|
| Plugin + client built reproducibly from source | IMPLEMENTED | client rebuild is byte-identical; plugin staged by `PrepareMigrationCandidates.ps1`, exit 0 |
| Automated suites | AUTOMATED-TESTED | 894 passed / 0 failed / 0 errors on the filtered Python suite (telegraph, spell, visual, wave6, wave7, boss_cue, boss_spell, boss_edge_volley); `Wave7ModelAtlasCoverageTest` green; generators byte-reproducible; all three atlases 100 % opaque |
| Release manifests and locks consistent | AUTOMATED-TESTED | `profile.lock.json` client pin and `server-plugins.lock.json` candidate pin match the built bytes; prior hash retained in `replaceSha256`; "Verified 30 pinned plugin candidates" |
| Artifacts installed on production | LIVE-VERIFIED | on-disk size/sha256/ownership, service active, clean boot |
| Client asset downloadable | LIVE-VERIFIED | HTTPS `200`, exact byte count |
| Wave 7 minibosses spawn with their boss bars | **BLOCKED** | requires an online eligible player; see below |
| Boss-bar appearance, pose playback, spell silhouettes, texture appearance | **BLOCKED** | needs a real client and F2 capture; no substitute accepted |
| Live combat balance at 2/5/10/11/20 players | **BLOCKED** | needs real clients |
| Current CI on this HEAD | **NOT VERIFIED** | not run in this session |

### Why the Wave 7 runtime check is BLOCKED, not passed

Both sandbox entry points refuse to spawn Wave 7 without an eligible online player, and
that refusal is intentional and authored:

- `/cmend test wave 7` → `WAVE_TEST_START_REFUSED … reason=empty-sandbox-roster`
- `/cmend wave spawn 7` → `WAVE_INSPECTION_REFUSED … reason=empty-room-roster required=1`,
  with the source comment "Wave 7 drives per-participant trials, so an empty room cannot
  run it."

Both refusals fire before any generation is consumed, so the attempt left the event state
byte-for-byte intact (`COLLECTING`, generation 408, wave 0) — confirmed by re-reading
`/cmend status` afterwards. `/cmend test visuals mobs|boss` only reports on entities that
already exist, so it cannot be used to create one either.

The server was idle (no join/leave events for three hours, `pings=none`, `owned_living=0`)
which is what made the restart safe, and is also why no Wave 7 actor could be summoned.
Confirming the bars, poses, silhouettes and repainted atlases therefore requires a real
client session and remains outstanding.

## Code changes behind this release

Commits on `codex/rift-wave7-visuals-20261114`, in order after the production base:

- `a288a3f5` Wave 7 miniboss boss bars.
- `5bd801ec` Wave 7 miniboss poses driven by the authoritative cast state.
- `4577e5e4` one authored silhouette and one palette per spell.
- `44bb3ab2` match the shipped Ram ladder and the Warden's real emergence lifecycle.
- `e7a5fd33` repaint the Wave 7 miniboss atlases so every baked face has paint.
- `659b86dd` teach four boss probes the seams the runtime grew (31 cases that previously
  failed to compile now run and pass).
- `6f9f1584` re-pin the client artifact.
- `a7de87e1` pin the rebuilt CopiMineEndEvent candidate.
