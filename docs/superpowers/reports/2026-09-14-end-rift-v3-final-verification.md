# End Rift V3 — final server-side verification checkpoint

Date: 2026-09-14
Branch: `codex/end-rift-event`
Repository: `https://github.com/IliaZav/copimine`
Starting SHA for this repair continuation: `c5a9f282073303208788828384e1d0801e6863dc`
Previous code/test checkpoint: `150a1910b4990de973332aec3e3cb1e92f64abff`

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

## Continuation fixes

- The disposable Creative full-run now snapshots and restores the participant
  set, clears the transient wave/objective marker before boss cleanup, removes
  wave objective state, and persists the restored state. The live visual probe
  now requires `wave=0 event-mobs=0 boss=none` and the same participant count
  after cleanup.
- The server-side visual diagnostic now reports the actual client catalog
  paths instead of synthesizing filenames from display ids. The Paper probe
  reported the supplied boss texture at
  `assets/copimineclient/textures/entity/end_rift_user_boss.png` and the
  supplied geometry at
  `assets/copimineclient/models/entity/end_rift_guardian/geometry.json`.
- The first shield probe failure was in the disposable Mineflayer harness: its
  default name had 18 characters, beyond Minecraft's 16-character username
  limit, so Paper rejected the handshake before a player joined. The default
  is now `RiftShieldProbe` and the script rejects overlong names explicitly.
  The production shield and projectile paths were not changed.

## Implemented areas

- Official V2/V3 boss authority is `GENERIC_MAX_HEALTH` plus
  `LivingEntity.getHealth()/setHealth()`. The verified local boss uses max HP
  `5000`; the old `1024`-style display is not the runtime authority.
- The custom client bossbar reads real current/max HP, has phase markers and
  cleanup. Its layout now keeps title/details, phase labels and cast status on
  separate vertical lanes; the native rendering itself remains unverified here.
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
PASS — 76 passed in 1.37s; Java policies, persistence/recovery, builds and pack checks passed

python -m pytest -q tests/test_end_event_current_contract.py -k 'distributed_client_jar_contains_the_current_boss_assets or wave6_ring_displays or wave7_barriers_validate_or_repair or server_visual_diagnostics'
PASS — 4 passed, 54 deselected; current distributed client catalog and visual mappings are pinned

python -m pytest -q tests/test_end_event_current_contract.py -k boss_shield_live_probe_covers
PASS — 1 passed; the default shield probe name is within Minecraft's username limit

python -m pytest -q tests/test_end_rift_client_hud_contract.py
PASS — 2 passed; phase-marker labels and cast status have separate layout lanes

CopiMineClient/.gradle-dist/gradle-8.10.2/bin/gradle.bat --no-daemon test
PASS — CopiMineClient BUILD SUCCESSFUL in 15s

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
attacks=62
LIVE_MOB_COMBAT_PASS moved=1842 player_hurt=219 player_damage_applied=221 ai_targets=808 ai_paths=474
```

### Boss real HP and shield

```text
real HP: status=hp-5000/5000 physical-5000/5000; entity Health=5000.0f; max attribute=5000.0; legacy virtual marker absent — PASS
boss damage: before=2000 after=1765 delta=235; player_damage_events=47 — PASS
shield ON: 950 -> 950 — PASS
shield OFF: 3950 -> 3945 — PASS
shield restored: 950 -> 950 — PASS
```

### Five players, same target, same-tick groups

```text
players=5 events=435 same_tick_event_groups=86
starting HP=5000
accepted final damage=1756.26508891582667
expected ending HP=3243.73491108417333
actual ending HP=3243.7402
absolute health_delta=0.00528891582667
PASS — within the documented entity-health float write tolerance
```

The Wave 4 projectile regression was rerun after one disposable-client timing
miss. The passing run reported:

```text
LIVE_RIFT_WAVE4_OBELISK_PASS players=2 obelisks=4 active_before=4 reflected_hits=3 first_target_hp=2 second_target_hp=1 destroyed=true pulse_radius=5 fireball_cap=1 real_blocks=true
```

### Waves and boss phases

The official two-player Paper run passed W1 through W7, including W6
`COLLAPSE_RINGS`, W7 `REALITY_SPLIT`, all six boss phases
`AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL`, victory and cleanup. The
historical run used event `09572556-ee1d-45cb-82e7-aa7fe47a8019`; the fresh
post-fix run used event `344f3c8c-42c5-42d2-88a2-cd481ce5e5cc` and emitted all
wave transitions, `BOSS_DEFEAT_COMMITTED`, one
`BOSS_REWARDS_DELIVERED`, and `END_EVENT_VICTORY_MEMORIAL`. Its final
authoritative state was `UNLOCKED`, `wave=0`, `event-mobs=0`, `boss=none`.

The fresh run used two real Mineflayer clients (`EndRiftFinalC2` and
`EndRiftFinalD2`) with a 1200-second client lifetime. It completed the three
Wave 6 pairs and the full Wave 7 chamber objective before boss combat. The
boss started at real HP `5000.0/5000.0`; accepted transactions reduced that
same `LivingEntity` through every phase, including `TELEGRAPHING`, `RECOVERY`
and `LAST_SEAL`, and the lethal transaction recorded
`health_before=62.5 health_after=0.0 lethal=true`. No wipe or offline-grace
condition occurred in this run.

### Fresh continuation probes

The continuation changes were rebuilt into the isolated local Paper runtime
and rechecked with the current distributed client JAR:

```text
LIVE_WAVE6_BOUNDARIES_PASS rings=3 radii=8,14,19 visual_displays=240 visual_points=64,80,96 leash_policy=true player_containment=true
LIVE_WAVE7_BARRIERS_PASS chambers=2 cells=480 visual_displays=120 barrier=13,68,-39 collision=true
LIVE_WAVE7_BARRIER_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0
LIVE_BOSS_REAL_HEALTH_PASS boss=a0de7b5e-7aee-427a-84f2-879f643013ee status=hp-5000/5000 physical-5000/5000 attribute-unclamped=true current-health-marker=true legacy-virtual-marker=false
LIVE_RIFT_WAVE4_OBELISK_PASS players=2 obelisks=4 active_before=4 reflected_hits=3 first_target_hp=2 second_target_hp=1 destroyed=true pulse_radius=5 fireball_cap=1 real_blocks=true
LIVE_MOB_COMBAT_PASS moved=1813 attacks=37 player_hurt=73 player_damage_applied=76 ai_targets=449 ai_paths=246
LIVE_CURRENT_AI_PASS waves=1,2,3,4,5,6,7 boss_phases=AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL teleport_guards=2
```

The visual five-player probe also created the current portal layers and
obelisk displays and finished with:

```text
CURRENT_VISUAL_CLIENTS_PASS count=5 core=8,68,-39
CURRENT_WAVE3_VISUAL_PASS portals=3 layers=FRAME,INNER,SHARD displays=12
CURRENT_WAVE4_VISUAL_PASS obelisk=real-block-state telegraph=present
CURRENT_VISUAL_FINAL_CLEANUP_PASS wave=0 event-mobs=0 boss=none
```

The ring display transform was corrected from an offset below the combat
floor to `combatFloorY() + 1.0D`; Wave 7 now repairs a missing generation
chamber assignment before clearing and rebuilding its walls. Manual
disposable-wave cleanup also resets the transient `activeWave` marker and
persists the restored state. The current client distribution was rebuilt and
contains the imported boss geometry, skin, HUD, ordinary supplied skins and
both supplied action animations. The current hashes are:

```text
client JAR SHA-1    1d9f9ef1445903556ca1d443e33cd02b03f0f75b
client JAR SHA-256  4cf4c92f82cd201b975c57b0b88fb2a12ecd1f677d74fdd68d976704b0409895
modpack SHA-1       2380aee0310793bd4d6fb33c0f8072f71fddbb52
modpack SHA-256     0a07cd05c7931ebd7c736ff1b1ef83ff9f60482a6121d900d9767501eab716b5
```

## User bug matrix

| User-reported bug | Root cause | Change and files | Paper/Minecraft verification | Result |
|---|---|---|---|---|
| Boss and mob resources were old or missing | The distributed client JAR did not contain the current imported boss geometry/skin/HUD and the ordinary visual catalog was not tied to the actual runtime paths | Rebuilt `thirdparty/client-mods/CopiMineClient-0.1.1.jar`; tightened the catalog mapping in `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`; added the distributed-JAR contract in `tests/test_end_event_current_contract.py` | Current JAR contents, Paper visual probe, and current client log accepted the boss and supplied mob texture paths | Source/distribution fixed; native appearance is not verified because the Computer Use surface is unavailable |
| Spider had no custom model | The supplied archives contain a spider skin but no spider geometry | Kept vanilla spider geometry and bound the supplied `end event.rar` spider skin through the existing renderer; no fake spider model was invented | Paper mob probe and client resource lookup pass | Correctly adapted to available assets; a separate spider mesh would require a supplied spider geometry asset |
| Boss model was wrong and animations were not visible | Runtime clients could be stale even when server references were correct | Distributed current boss geometry, texture, importer/renderer classes and `udar_iz_grudi`/`udar_po_zemle` clips in the client JAR | Boss visual cue and phase probes pass; JAR contains every required class/resource | Current runtime artifact is fixed; native mesh/animation alignment remains unverified |
| Boss HP and boss bar were wrong/broken | Old UI/client artifact and a virtual-health path could diverge from the real entity | Real `LivingEntity` HP authority remains 5000; current `EndRiftBossBarHud` is distributed; real-health and boss-visual wrappers are covered | `LIVE_BOSS_REAL_HEALTH_PASS` and boss visual wrapper pass | Server authority and distributed HUD fixed; native artwork still requires an exposed client window |
| Obelisks had wrong/no model or texture | Diagnostics reported a nonexistent `copimineclient` entity texture path for server `ItemDisplay` models | Corrected obelisk/fireball/tentacle server paths to `assets/copimine/textures/item/...`; kept client namespace only for the articulated overlay | Wave 4 created CMD `830010` displays; obelisk reflection/destroy probe passes | Runtime mapping fixed; native UV/appearance still not verified |
| Rift gates looked like a cube or had no texture/model | The live server had the three-layer portal model, but the public pack URL was serving an old pack | Current pack retains the arch/frame/inner/shard models and textures; Wave 3 mapping is checked by `tests/test_end_event_current_contract.py` | Paper created CMD `830007/830008/830009` layers; current local pack contains the model | Local source/artifact fixed; public deployment is still stale and native appearance is unverified |
| Core was not seated on its block | Core visual transform/origin was previously below or off the block | Kept the corrected core placement and current visual contract | Five-player visual probe reports the current core and cleanup | Paper/source pass; native multi-angle placement not verified |
| Projectiles could not be reflected | User confirmed this was a testing error, not an event bug | No projectile speed, timer, hitbox, damage, trajectory or reflection code was changed | Wave 4 regression reports reflected hits and obelisk destruction | Preserved and regression-tested |
| Wave 6 rings were tiny/invisible and passable | The thin ring ItemDisplay strip was translated to `-0.94F`, inside the solid floor; the visual lane and containment needed an end-to-end check | Raised the ring strip to `0.02F` above the combat floor and retained server containment/leash/AI handling in `CopiMineEndEvent.java` | `LIVE_WAVE6_BOUNDARIES_PASS` reports three rings, radii `8,14,19`, 240 visual displays and player containment | Paper mechanics and visibility contracts pass; native visual still not verified |
| AI inside rings was broken | AI target/path decisions and zone enforcement were not being proven together | Preserved target/path reassertion and bounded teleport guards; added/ran current AI phases and combat probes | `LIVE_CURRENT_AI_PASS` and `LIVE_MOB_COMBAT_PASS` pass | AI behaves in Paper runtime |
| Wave 6 was incomplete or softlocked | Sequential disposable probes could leave stale transient wave state | Reset `activeWave` on disposable clear, persist state, and require final cleanup in `RunEndRiftVisualFivePlayerLive.ps1` | Wave 6 live probe and final `wave=0 event-mobs=0 boss=none` cleanup pass | Fixed in local test flow; official full-run probe also passed |
| Wave 7 had no visible walls/rooms | A lost chamber assignment could cause cleanup before rebuild, leaving no replacement boundaries | Added `ensureRealitySplitChamberAssignment()` before `clearRealitySplitBarriers("wave7-rebuild")` in `CopiMineEndEvent.java`; retained visible Amethyst barriers and collision journal | `LIVE_WAVE7_BARRIERS_PASS` reports 480 cells/120 displays and collision; cleanup restores blocks | Paper logic pass; native room/barrier appearance still not verified |

## Distribution and deployment gate

The current local resource pack is built and pinned at SHA-1
`f5805906b94977dce2728b93a63997af7339da17` and SHA-256
`335f68a8ccf1a5be6fecfd97b711a4684d61cc47c486c43637bd4dbcaa3bc1c1`. The
configured public URL
`https://copimine.ru/resourcepacks/CopiMineResourcePack.zip` was checked
read-only and returned HTTP 200 but only 553461 bytes, ETag
`"37e0ebd8f499b572f84574ae5b5be1f0"`, last modified
`2026-08-30 21:19:39Z`. It is not the current 24 MB local pack. No production
upload was performed; the public pack must be replaced by an authorized
release/deployment before a production player can see these current gate,
obelisk, model and bossbar assets.

## Native Minecraft verification status

Computer Use was explicitly attempted again. It returned `apps=[]`: the
Minecraft Java process exists on the host, but no native Minecraft window was
exposed as a targetable Computer Use surface. Consequently native
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
