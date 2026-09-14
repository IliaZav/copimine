# End Rift V3 — final server-side verification checkpoint

Date: 2026-09-14
Branch: `codex/end-rift-event`
Repository: `https://github.com/IliaZav/copimine`
Starting SHA for this repair continuation: `c5a9f282073303208788828384e1d0801e6863dc`
Code/test checkpoint: `150a1910b4990de973332aec3e3cb1e92f64abff`

This report records the server/source work and the exact local Paper evidence.
It does not claim a native Minecraft visual pass: Computer Use exposed no
native app window on this host. The final publication commit and remote SHA
are the containing commit of this report and are checked again after push.

## Root cause: damage was accepted by the event, not necessarily by HP

Paper/vanilla hurt resistance was the concrete cause of the lost or partial
damage. A LivingEntity can receive a non-cancelled `EntityDamageByEntityEvent`
while `noDamageTicks`, `maximumNoDamageTicks` and `lastDamage` still represent
the previous hit. In that window the native application may apply only the
difference from `lastDamage`, or zero. A listener that logged a positive
`finalDamage`, and a client hurt animation, therefore did not prove a real HP
mutation.

The old mixed path also made the result ambiguous: some protection listeners
could cancel later, while another path assumed Paper would apply the event. The
boss had the same native-window risk, and a virtual/visual health projection
could diverge from `LivingEntity` health.

Combat Trace reproduced the distinction. The repaired trace records the event
checkpoint, raw/final damage, cancellation before/after, no-damage state,
health before/after and next-tick health. A representative accepted record is:

```text
raw=8.000 final=8.000 cancelled_before=false cancelled_after=true
health_before=4892.000 expected_health=4884.000 health_after=4884.000
health_next_tick=4884.000 accepted=true authority=REAL_ENTITY_HEALTH diagnosis=APPLIED
```

## Fix

For a current-generation owned event mob or the official V3 boss, the path is
now a single transaction:

1. earlier protection and shield listeners are allowed to make their decision;
2. an already-cancelled valid event is not resurrected;
3. positive final damage is calculated from the Bukkit event;
4. the event is cancelled before Paper's native application;
5. exactly one clamped amount is written to the real `LivingEntity` health;
6. the next tick confirms the same real HP value;
7. rejected hits carry an explicit reason and leave HP unchanged.

This is scoped to event ownership and current generation. There is no global
`noDamageTicks=0`, no global `maximumNoDamageTicks=0`, no global
`invulnerable=false`, and no manual HP write for unrelated vanilla entities.
The only hurt-window configuration is scoped to owned event combat entities.

The relevant listener ordering is:

| Listener | Priority | `ignoreCancelled` | Purpose |
|---|---:|---:|---|
| Combat Trace open | LOWEST | false | snapshot event and real HP |
| wave-6 cross-room melee guard | HIGH | false | reject only illegal chamber crossing |
| owned wave-mob transaction | HIGHEST | true | apply exact real mob HP once |
| boss transaction | HIGHEST | false | preserve prior cancellation, shield/source checks, real HP once |
| Combat Trace close | MONITOR | false | schedule next-tick real HP check |
| aggro trace | MONITOR | true | update AI target after an accepted mob hit |

`CopiMineEndEvent` owns the event dispatch integration. The policies are
`EventRealHealthDamagePolicy` and `BossRealHealthDamagePolicy`; trace data is
implemented by `CombatTraceRecord` and `CombatTraceService`.

## Implemented areas

- Official V2/V3 boss authority is `GENERIC_MAX_HEALTH` plus
  `LivingEntity.getHealth()/setHealth()`. The verified local boss uses max HP
  `5000`; the old `1024`-style display is not the runtime authority.
- The custom client bossbar reads real current/max HP, has phase markers and
  cleanup; the native rendering itself remains unverified here.
- Supplied boss model/skin/animations are wired into the client/resource-pack
  bridge. Supplied Enderman/Spider skins are referenced for mob overlays.
- Obelisk display geometry is one complete scaled model, rather than a full
  five-block model duplicated at each layer.
- Wave 6 rings use radii `8,14,19`, 64/80/96 points, a player containment lane,
  movement enforcement and AI target/path reassertion.
- Wave 7 uses visible Amethyst barriers with physical collision and restart
  rebuild; the live probe reports 480 barrier cells and 120 visual displays.
- Rift Fireball mechanics were not changed. Reflection/obelisk tests still
  verify reflected current-generation fireballs and boss immunity.
- Recovery, cleanup, death/victory idempotency and non-event ownership guards
  remain covered. The website was not touched.

## Assets supplied by the user

| Asset | Runtime use/status |
|---|---|
| `models.rar` | used for boss model, skin and supplied boss animation set |
| `modelsboss.rar` | byte-identical to `models.rar`; same boss assets/use |
| `end event.rar` | used for `enderman-1.png`, `spider.png`, and the two boss attack animations |
| `udar_po_zemle.animation.json` | wired to the boss ground-strike animation |
| `udar_iz_grudi.json` | wired to the boss chest-strike animation |

The supplied archives contain no ordinary-mob geometry, gate geometry,
obelisk geometry, room geometry or wave-7 barrier model. Ordinary mob
geometry therefore remains the project’s vanilla/generator mapping while the
provided skins and provided boss model are used where the runtime supports
them. This is a source/resource fact, not a claim that a missing asset was
successfully rendered.

## Automated verification

Commands and results:

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftEventChecks.ps1
PASS — 68 passed in 1.11s; Java policies, persistence/recovery, builds and pack checks passed

python -m pytest -q tests/test_end_rift_multiplayer_probe_contract.py tests/test_end_rift_recovery_contract.py
PASS — 4 passed

powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftRecoverySmoke.ps1
PASS — current and rotated gzip logs, durable phase and unlock recovery
```

The five-player contract now requires at least 100 accepted events when five
players are requested; the synchronized burst also continues normal attack
cadence. This prevents a false PASS from four attacks per bot.

## Live Paper verification

Evidence files are under
`artifacts/end-rift-v3-evidence/20260914-022712/`.

### Mob

```text
attacks=69
LIVE_MOB_COMBAT_PASS moved=2020 player_hurt=62 player_damage_applied=65 ai_targets=42 ai_paths=25
```

### Boss real HP and shield

```text
boss damage: before=2000 after=1720 delta=280 — PASS
shield ON: 950 -> 950 — PASS
shield OFF: 3950 -> 3942 — PASS
shield restored: 950 -> 950 — PASS
```

### Five players, same target, same-tick groups

```text
players=5 events=495 same_tick_event_groups=97
starting HP=5000
accepted final damage=1999.14377391338560
expected ending HP=3000.85622608661440
actual ending HP=3000.8586
absolute health_delta=0.00237391338560
PASS — within the documented entity-health float write tolerance
```

### Waves and boss phases

The official two-player Paper run passed W1 through W7, including W6
`COLLAPSE_RINGS`, W7 `REALITY_SPLIT`, all six boss phases
`AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL`, victory and cleanup. Final
state was `UNLOCKED`, `wave=0`, `event-mobs=0`, `boss=none`.

## Native Minecraft verification status

Computer Use was explicitly attempted again. It returned `apps=[]` and no
native Minecraft window or accessible client process. Consequently native
screenshots/video, client FPS, actual in-client model/texture appearance,
bossbar artwork, tentacle animation/bone alignment, UVs, clipping, room
visibility and barrier appearance are **NOT VERIFIED**. Paper and resource
contracts cannot replace that gate, so this report’s release verdict is:

```text
SOURCE/CONTRACTS: PASS
PAPER RUNTIME: PASS
AUTOMATED REGRESSION: PASS
NATIVE MINECRAFT VISUAL/AUDIO/INPUT QA: NOT VERIFIED
RELEASE VERDICT: NOT READY FOR FINAL VISUAL RELEASE
```

The remaining action is to rerun the supplied native Computer Use check on a
host where the Minecraft client window is exposed, then capture real
screenshots/video for the listed models, textures, bossbar, rooms, gates,
obelisk, core, rings, tentacles and all wave/boss transitions.

## Video files

| File | What it would show | Build | Status |
|---|---|---|---|
| `video/01_END_RIFT_V3_FULL_FINAL_RUN.mp4` | native continuous W1–W7 and boss run | current local artifact | NOT VERIFIED — no native recorder/window |
| `video/clips/*.mp4` | native mechanics and animation clips | current local artifact | NOT VERIFIED — no native recorder/window |

No video is claimed or fabricated. The evidence directory contains the
server-side logs and reports only.

## Screenshots

| Location | What it would show | Status |
|---|---|---|
| `screenshots/` | portal, obelisks, core, rooms, boss, tentacles and phase visuals | NOT VERIFIED — native Minecraft surface unavailable |
| `client/` | client log and native capture metadata | native QA note only; no client screenshot |

Additional server-side probes are recorded in
`artifacts/end-rift-v3-evidence/20260914-022712/reports/extended-live-results.md`.
