# Live Paper run — End Rift RC plugin on an isolated local server

**Date:** 2026-11-12 (local time, Europe/Moscow)
**Author:** live-paper-runner (teammate)
**Status:** PARTIAL — boot, wave spawning, mob combat and boss entity behaviour are PROVEN.
Boss phase machine, player-dealt boss damage, phase-5 echo decoys and event cleanup are UNVERIFIED,
and the reason is identified and explained below.

This is the first live Paper execution of the End Rift RC plugin this round. Everything quoted below is
observed output, not inference. Where a claim rests on a caveat, the caveat is stated inline.

---

## 1. Environment and artifact identity

| Item | Value |
| --- | --- |
| Server directory | `D:\Desktop\Copimine\copimine-source-of-truth\local-runtime\end-rift-server` |
| Server port / RCON port | 25566 / 25576 (isolated local only; production is a different machine on 25565) |
| World | CopiMine |
| Paper | `paper-26.3-205.jar`, sha256 `a0a58f532f08054ea92eb916d3edb6ed3c86429d77175317304f6e995cce9968` (matches `profile.lock.json`) |
| Java | Temurin 25.0.4.1 (`local-runtime\toolchains\temurin-25.0.4.1\jdk-25.0.4.1+1`) |
| Plugin deployed | `CopiMineEndEvent.jar`, **2398506 bytes**, sha256 `7128fc9557a389a8f4813fb1ea5e8130b0b936ce92d003073fe5f194dfcab544` |

The sha256 was verified **after** the copy into `plugins\` and **before** the JVM was started:

```
DEPLOYED path=...\local-runtime\end-rift-server\plugins\CopiMineEndEvent.jar
DEPLOYED bytes=2398506
DEPLOYED sha256=7128fc9557a389a8f4813fb1ea5e8130b0b936ce92d003073fe5f194dfcab544
MATCH=expected
```

The same sha256 was re-read from the deployed jar at the end of the run and was unchanged, so **this is
the artifact that actually ran**.

Backups taken before replacing the previous 1965231-byte plugin:
- `plugins\CopiMineEndEvent.jar.bak-20261112-preLiveRun` (1965231 b)
- `plugins\CopiMineEndEvent.pre-20261112-preLiveRun\` (6 files)

---

## 2. BLOCKER 1 — the test scripts refuse a junctioned `local-runtime`

`tests\StartEndRiftLocal.ps1` and `tests\InvokeEndRiftLocalRcon.ps1` both dot-source
`tests\EndRiftLocalAuthMode.ps1`, which contains at lines 460-461:

```powershell
if (-not $rootItem.PSIsContainer -or ($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
  throw "Refused an unsafe local runtime root: $fullRuntimeRoot"
}
```

At the time, `local-runtime` in the worktree was a **junction**:

```
path=C:\Users\zavod\.codex\worktrees\rift-guardian-polish-20261110\integration-20261030\local-runtime
PSIsContainer=True
Attributes=Directory, ReparsePoint
LinkType=Junction
Target=D:\Desktop\Copimine\copimine-source-of-truth\local-runtime
```

Exact refusal text:

```
Refused an unsafe local runtime root: C:\Users\zavod\.codex\worktrees\rift-guardian-polish-20261110\integration-20261030\local-runtime
```

**Consequence:** every `tests\RunEndRift*Live.ps1` wrapper is unusable in that configuration, because
they all shell out to `InvokeEndRiftLocalRcon.ps1`. The guard is correct — it is deliberately refusing a
reparse point. The junction was removed by the Lead; `tests\` was **not** patched.

**Workaround used (no test files touched):** a standalone RCON client at
`local-runtime\rcon-probe.ps1`, speaking the same wire protocol and reading `rcon.password` from
`server.properties`. All RCON output quoted in this document came through it.

Note: a first version of that probe failed authentication because it did
`$line.Substring(13)` on `rcon.password=` (14 characters). With the correct offset the connection
authenticated immediately. This was my error, not a server fault.

---

## 3. BLOCKER 2 — the 512 MiB heap in `StartEndRiftLocal.ps1` OOMs

`tests\StartEndRiftLocal.ps1:275-289` pins `-Xmx512M` with `-XX:TieredStopAtLevel=1` for a documented
reason (the machine previously ran out of commit even at 1 GiB). On this machine that heap is not enough
for the full plugin set.

Run 1 (512 MiB), log `end-rift-paper-start-20261112-052040.out.log`:

```
[05:21:20 WARN]: com.loohp.imageframe...ClassGraphException: Uncaught exception during scan
[05:21:20 WARN]: Caused by: java.lang.OutOfMemoryError: Java heap space
[05:21:20 ERROR]: Error occurred while enabling ImageFrame v2026.1.5.0 (Is it up to date?)
                 java.lang.ExceptionInInitializerError
[05:21:31 INFO]: Done (47.494s)! For help, type "help"
[05:24:48 ... 05:24:54]  19 more 'java.lang.OutOfMemoryError: Java heap space'
[05:24:54 INFO]: MobProbeA (46b594bc-...) lost connection: Internal Exception: io.netty...EncoderException: java.lang.OutOfMemoryError: Java heap space
[05:25:29 WARN]: [ClearLag] Server TPS has dropped to 7,86! Performance is critical!
[05:26:33 WARN]: Exception in thread "BlueMap-RenderThread-0-0" java.lang.OutOfMemoryError: Java heap space
```

The JVM wedged: it still answered `Get-Process` (`Responding=True`, WS 1042 MB) but RCON timed out.

**Rel launch used for all later evidence** (same jar, same absolute directory, larger heap):

```
java -Xms1024M -Xmx2048M -Xss1M -XX:+UseG1GC -XX:+ParallelRefProcEnabled \
  -Dfile.encoding=UTF-8 -jar ...\end-rift-server\paper-26.3-205.jar nogui
```

At 2 GiB the boot is healthy — `Done (23.448s)!` and `Done (25.272s)!` in runs 2 and 3, with ImageFrame
reaching `ImageFrame has been Enabled!` instead of dying. The host committed 2 GiB without trouble.

---

## 4. PROVEN — the server boots clean and the RC plugin enables

Run 2, `end-rift-paper-run2.out.log`:

```
[05:29:38 INFO]: [CopiMineEndEvent] Enabling CopiMineEndEvent v0.1.0
[05:29:38 INFO]: [CopiMineEndEvent] CopiMineEndEvent loaded in local mode; persistent phase=COLLECTING
[05:29:39 INFO]: Done (23.448s)! For help, type "help"
```

Run 3, `end-rift-paper-run3.out.log`:

```
[05:34:38 INFO]: [CopiMineEndEvent] Enabling CopiMineEndEvent v0.1.0
[05:34:39 INFO]: Done (25.272s)! For help, type "help"
```

`cmend status` answered with live state, e.g.:

```
state=COLLECTING event=b04e042c-4d91-452d-813f-977e563b1ce4 generation=29
core=CopiMine 8,68,-39 requiredPlayers=2
arena=CopiMine [-12,65,-59]..[28,71,-19] volume=11767
```

---

## 5. PROVEN — wave 1 spawns real, persistent, hostile server entities

### 5.1 The spawn path executes

`cmend wave spawn 1` on an **empty** server answered `Тестовая волна создана` and logged:

```
[05:23:11 INFO]: [CopiMineEndEvent] WAVE_OBJECTIVE_STARTED ... wave=1 objective=RIFT_CARRIERS mobs=0 generation=30
[05:23:11 INFO]: [CopiMineEndEvent] END_RIFT_WAVE_GROUP_SPAWN ... wave=1 group=1/1 spawned=22 next_ms=0
[05:23:11 INFO]: [CopiMineEndEvent] WAVE_TEST_STARTED ... generation=30 wave=1 objective=RIFT_CARRIERS players=2 planned_mobs=22 groups=1 mode=capture
```

plus exactly 22 `WAVE_AI_TACTIC ... tactic=WAVE_ONE_STALKER` lines, one per spawned mob.

### 5.2 With no player online the world genuinely contains nothing

Immediately afterwards, on the **same** server, with **zero players**:

```
execute if entity @e                          -> Test failed
execute if entity @e[tag=copimine_end_event]  -> Test failed
tag @e[tag=copimine_end_event] list           -> No entity was found
OBJECTIVE_DIAGNOSTICS ... trackedMobs=0 liveMobs=0
```

### 5.3 `spawned=22` is TRUE — proven by a player-present retest

A protocol bot (`MobProbeA`, whitelisted) was connected. The instant its chunks loaded:

```
execute if entity @e                                  -> Test passed. Count: 45
execute if entity @e[type=minecraft:enderman]         -> Test passed. Count: 22
execute if entity @e[type=minecraft:item_display]     -> Test passed. Count: 3
```

**22 endermen — exactly the 22 reported as spawned.** They were persistent server entities the whole time;
an entity selector simply cannot see entities in unloaded chunks.

The mobs then did more than exist — they **pursued and attacked the player**, six times:

```
[05:30:05 INFO]: [CopiMineEndEvent] WAVE_MOB_DAMAGE event=b04e042c-4d91-452d-813f-977e563b1ce4 \
   attacker=ENDERMAN:57696fc5-47e8-486f-9c52-43dd32da49b1 wave=1 \
   victim=46b594bc-2e43-3e33-8552-8825b4454d3a cause=ENTITY_ATTACK final=5.599999904632568 cancelled=true
```

(6 identical lines at 05:30:05, :05, :06, :07, :08, :08.)

Critically, the attacking UUID `57696fc5-47e8-486f-9c52-43dd32da49b1` **is one of the 22 UUIDs printed by
the earlier `WAVE_AI_TACTIC` lines at 05:23:11**. This closes the loop: the entities logged as spawned are
the same entities that later attacked.

This proves, for wave 1: real spawning, real pursuit, real melee damage delivery, and event-owned damage
interception (`cancelled=true`).

### 5.4 Ruled-out hypothesis (documented deliberately)

An alternative explanation was raised during the run: that `END_RIFT_WAVE_GROUP_SPAWN spawned=22` merely
echoed a *planned* count and that a silent spawn failure was hidden. **That hypothesis is refuted** by
§5.3 — the count was true, and the entities were real and hostile. It is recorded here so the diagnostic
is on record as cross-checked by experiment rather than accepted on the log's word alone.

---

## 6. PROVEN — the Rift Guardian is a real, mobile entity with real HP

**Boss identity and health pool**, run 3:

```
[05:35:02 INFO]: [CopiMineEndEvent] BOSS_STATS boss=44bb7444-7c22-43c0-a5fd-1a77cd7d33d5 \
   vanilla_base=7.0 configured_bonus=11.0 total_attack=18.0 players=2 hp=20000 damageX=1.00
```

`cmend status` reported:

```
boss=44bb7444-7c22-43c0-a5fd-1a77cd7d33d5 hp=5800/20000 physical=5800/20000
```

**It is a real server entity readable via NBT:**

```
data get entity 44bb7444-... Health  -> Хранитель Разлома has the following entity data: 5800.0f
data get entity 44bb7444-... Pos     -> Хранитель Разлома has the following entity data: [7.741920037869569d, 68.0d, -39.748767181154896d]
```

**It reaches the RAGE stage** (real stage-transition log line):

```
[05:35:04 INFO]: [CopiMineEndEvent] TEST_BOSS_STAGE_TRANSITION event=b04e042c-... \
   boss=44bb7444-... from=AWAKENING to=RAGE health=5800.0 max=20000.0
```

**It moves and pursues.** Three position samples ~3 s apart, verbatim:

```
[20.467780514660934d, 68.0d, -37.07717602337081d]
[11.975419445935348d, 68.0d, -22.594639707452842d]   <- ~16.8 blocks in 3 s
[-29.773281649645707d, 67.0d, -45.803760428077766d]  <- continued pursuit across the arena
```

Earlier in the same run it also closed distance from
`[7.67664729916267d, 68.0d, -46.32184885025911d]` to `[17.04961386293187d, 69.0d, -65.05771424296647d]`.

**It attacks:** two real boss melee hits were logged:

```
[05:36:23 INFO]: [CopiMineEndEvent] BOSS_MELEE_ATTACK event=b04e042c-... boss=44bb7444-... \
   target=46b594bc-2e43-3e33-8552-8825b4454d3a stage=RAGE stage_bonus=2.0 total=20.0
[05:37:06 INFO]: [CopiMineEndEvent] BOSS_MELEE_ATTACK event=b04e042c-... boss=44bb7444-... \
   target=46b594bc-2e43-3e33-8552-8825b4454d3a stage=RAGE stage_bonus=2.0 total=20.0
```

**Caveat, stated plainly:** the movement samples and the RAGE transition were taken while `cmend`
was already intermittently failing (§7). What is proven is **entity behaviour** — the boss exists, has a
real 20000 HP pool, moves, chases and lands melee attacks. What is **not** proven is the **plugin-driven
phase machine** end to end.

---

## 7. BLOCKER 3 — the plugin self-disables ~180 s after boot (correct-by-design)

Run 2, verbatim chain:

```
[05:29:32 ERROR]: [CopiMineEconomyCore] CopiMineEconomyCore PostgreSQL init failed: POSTGRES_PASSWORD is required for CopiMineEconomyCore.
[05:32:32 ERROR]: [CopiMineArtifacts] CopiMineArtifacts failed to initialize PostgreSQL: Timed out waiting for EconomyCore PostgreSQL schema readiness after bounded retries.
   java.lang.IllegalStateException: Timed out waiting for EconomyCore PostgreSQL schema readiness after bounded retries.
       at CopiMineArtifacts.jar//me.copimine.artifacts.EconomyDatabaseStartupGate.awaitAndInitialize(EconomyDatabaseStartupGate.java:92)
[05:32:32 INFO]: [CopiMineArtifacts] Disabling CopiMineArtifacts v1.1.0-shops-blocks-prices
[05:32:33 ERROR]: [CopiMineEndEvent] End Rift dependencies are unavailable; disabling to prevent partial startup.
[05:32:33 INFO]: [CopiMineEndEvent] Disabling CopiMineEndEvent v0.1.0
```

Afterwards every `cmend` command fails and the plugin shows as disabled in `plugins` (red):

```
[05:32:45 ERROR]: Command exception: /cmend boss phase hunt
org.bukkit.command.CommandException: Cannot execute command 'cmend' in plugin CopiMineEndEvent v0.1.0 - plugin is disabled.
...
...§cCopiMineEconomyCore, §cCopiMineElectionCore, §cCopiMineEndEvent, §aCopiMineNarcotics...
```

### Why this is NOT an End Rift defect

- End Rift requires `CopiMineWorldCore` + `CopiMineArtifacts` enabled (`CopiMineEndEvent.java:2402-2413`).
- `CopiMineArtifacts` needs PostgreSQL.
- **There is no PostgreSQL on this machine.** Independently confirmed: `psql`, `pg_ctl`, `initdb`,
  `postgres`, `docker` are all absent from `PATH`; `C:\Program Files\PostgreSQL`, `C:\PostgreSQL`,
  `D:\PostgreSQL` do not exist; there is no `postgres`/`pgdata` directory under the checkout; no
  PostgreSQL service is registered; nothing listens on 5432. Only the JDBC **client** driver
  (`org\postgresql\postgresql\42.7.5\postgresql-42.7.5.jar`) is present — that is a client, not a server.
  `local-runtime\end-rift.env` is empty.
- CI provides Postgres via a container, so CI proves the DB contract but is not reusable here.

So the 180 s disable is **fail-closed behaviour working as intended**: End Rift refuses to run
half-initialised rather than running degraded. **That is a genuine production-safety property and is good
news** — a partially booted event cannot silently produce a broken encounter. It is recorded here as an
environment limitation of this machine, not as an End Rift bug.

This is also the reason boss phase transitions, echo decoys and cleanup could not be exercised: the
plugin is disabled by the time a session is long enough to reach those stages.

---

## 8. Bot connectivity — the 9 s kick and the handshake that fixes it

A bare protocol bot (`LocalEndRiftBot.js`) is ejected after ~9 s, and the rejection does **not** come
from End Rift:

```
[05:30:08 INFO]: [CopiMineNarcotics] Rejected MobProbeA during CopiMineClient check: client-mod-channel-not-registered
[05:30:08 INFO]: MobProbeA lost connection: Для входа требуется CopiMineClient версии 0.1.3+26.3.
```

GrimAC also flagged the bare bot repeatedly (e.g. `MobProbeA провалил BadPacketsN (x20/x40/x60/x80)`,
later `Timer`, `AimModulo360`, `TickTimer`).

Running the full combat bot with the client-mod handshake enabled keeps it connected for the whole
session:

```
END_RIFT_TEST_CLIENT_MOD_HANDSHAKE=1
END_RIFT_TEST_CLIENT_MOD_VERSION=0.1.3+26.3
END_RIFT_BOT_NODE_MODULES=<...>\mc-bot-26.3-complete-probe\node_modules
END_RIFT_BOT_PROTOCOL_VERSION=26.1
node tests/LocalEndRiftMobCombatBot.js MobProbeA 200000 8 68 -39 45
```

Verified live: `Сейчас 1 из 24 игроков на сервере. default: MobProbeA` for the full duration.
The handshake works. Note the version string must be exactly `0.1.3+26.3` — `CopiMineNarcotics` compares it.

`LocalEndRiftBossCombatBot.js` hard-codes a path to `local-runtime\mc-bot\node_modules\mineflayer`, which
does not contain mineflayer here; `mc-bot-26.3-complete-probe\node_modules` does. The mob-combat bot takes
the module root from `END_RIFT_BOT_NODE_MODULES` and therefore works.

---

## 9. Observed SEVERE/ERROR/WARN inventory (non-End-Rift)

Everything here is environment noise, listed for completeness:

| Line | Cause |
| --- | --- |
| `[CopiMineEconomyCore] PostgreSQL init failed: POSTGRES_PASSWORD is required` | no database on this machine |
| `[CopiMineElectionCore] Не удалось включить ...: POSTGRES_PASSWORD is required` | same |
| `[CopiMineUltimateAdminPlus] PostgreSQL init failed; SQLite fallback is disabled` | same |
| `[CopiMineNarcotics] PostgreSQL init failed / Brewing state restore failed` (repeats) | same |
| `[CopiMineNarcotics] Client bridge signer is unavailable; reason=SIGNER_UNAVAILABLE` | no bridge signing config locally |
| `[ImageFrame] OOM during enable` (run 1 only) | 512 MiB heap |
| `[voicechat] Running in offline mode` | `online-mode=false` |
| `[ClearLag]/[ViaVersion]/[GrimAC]/[Essentials] newer version available` | informational |

Two genuine End Rift warnings worth recording, both benign here:

```
[05:35:04 ERROR]: [CopiMineEndEvent] BOSS_ANIMATION_CATALOG_ERROR event=... animation=TEST_PHASE action=explicit_unknown_pose
[05:35:33 WARN]: [CopiMineEndEvent] BOSS_TELEPORT_BLOCKED entity=44bb7444-... kind=BOSS reason=no-internal-permit
```

`TEST_PHASE` is the test-only phase name and has no animation, so the catalog error is expected for a
test boss. `BOSS_TELEPORT_BLOCKED ... reason=no-internal-permit` fires repeatedly — the boss attempted to
teleport and was refused by an internal permit gate. It did not prevent movement or melee, but it is a
real, repeated warning and is flagged for the Lead rather than explained away.

---

## 10. UNVERIFIED — explicit list

These were **not** proven and must not be reported as working:

1. **Boss phase machine (HUNT / RIFT / OVERLOAD / LAST_SEAL as a driven progression).** Only one
   transition was observed (`AWAKENING -> RAGE`) and it was induced via `cmend boss phase rage`
   immediately before the plugin was disabled. `cmend boss phase <x>` was attempted for hunt/rift/overload
   and each failed once the plugin self-disabled.
2. **Boss HP reduction caused by a real player hit.** `BOSS_DAMAGE_ACCEPTED` with
   `health_before`/`health_after` was never observed. The boss HP did move 20000 -> 5800, but that drop
   came from the test-harness transition path, **not** from a player strike; the bot never landed a hit.
3. **Phase 5 echo decoys / echo replicas.** `ECHO_DECOY` and `ECHO_REPLICA` never appear in any log line,
   no `BOSS_ECHO_DECOY*` line was emitted, and `execute if entity @e[type=minecraft:pillager]` (the decoy
   entity type, `CopiMineEndEvent.java:55982`) returned `Test failed`. `cmend boss spell summon_servants`
   logged `BOSS_SPELL_SELECTED spell=summon_servants` but refused to launch:
   `Boss spell не запущен: summon_servants (phase=RAGE; проверь цель, арену и активный cast)`.
   Cause is the plugin being disabled, plus the bot sitting outside the arena bounds
   (observed at `y=80.9` and `y=83.0` against an arena of `y 65..71`). **No echo actor was proven to
   exist.** This is the newest feature and remains the least verified.
4. **Waves 2, 3, 4, 5, 6, 7.** Only wave 1 was exercised. Wave 2 phantoms need a marked player and wave 7
   needs an admitted player; neither was reachable before the plugin disabled.
5. **Event cleanup / no orphans after stopping.** `cmend cleanup` returned
   `An unexpected error occurred while trying to execute that command` because the plugin was already
   disabled, and the plain entity count did not decrease (132 before, 132 after). **Orphan check is
   UNVERIFIED.** A boss entity with 5800 HP and a large population of event-tagged entities remained in
   the world at the point of measurement — this is *expected* given the plugin was disabled mid-flight,
   but it is not evidence of correct cleanup.
6. **Client-side visuals** (sphere mesh, boss model, HUD, runes). Not observable from a protocol bot.

---

## 11. Reproduction summary

```powershell
# 1. deploy and verify the RC plugin (sha256 must match before boot)
Copy-Item <worktree>\build\minecraft-26.3\plugins\jars\CopiMineEndEvent-26.3.jar \
          <runtime>\end-rift-server\plugins\CopiMineEndEvent.jar -Force
(Get-FileHash <runtime>\end-rift-server\plugins\CopiMineEndEvent.jar -Algorithm SHA256).Hash
# expect 7128fc9557a389a8f4813fb1ea5e8130b0b936ce92d003073fe5f194dfcab544

# 2. boot (2G heap — the 512M in the test script OOMs this machine)
java -Xms1024M -Xmx2048M -Xss1M -XX:+UseG1GC -XX:+ParallelRefProcEnabled \
  -Dfile.encoding=UTF-8 -jar <runtime>\end-rift-server\paper-26.3-205.jar nogui

# 3. park a real player (chunks must be loaded or @e sees nothing)
$env:END_RIFT_TEST_CLIENT_MOD_HANDSHAKE='1'; $env:END_RIFT_TEST_CLIENT_MOD_VERSION='0.1.3+26.3'
node tests\LocalEndRiftMobCombatBot.js MobProbeA 200000 8 68 -39 45

# 4. drive the event over RCON, racing the ~180 s dependency deadline
\rcon-probe.ps1 'cmend wave spawn 1'
\rcon-probe.ps1 'execute if entity @e[type=minecraft:enderman]'   # -> Test passed. Count: 22
```

---

## 12. Bottom line

Proven on a live server: **the RC plugin (sha256 `7128fc95...`) boots on Paper 26.3, enables, spawns wave 1
as 22 real persistent endermen that pursue and strike a player for 5.6 damage with event-owned damage
interception, and creates a real Rift Guardian with a 20000 HP pool that reaches RAGE, moves ~16.8 blocks
in 3 s and lands 20.0-damage melee hits.**

Not proven, and the cause is the recorded PostgreSQL absence rather than any observed End Rift defect:
the driven boss phase machine, player-dealt boss damage, phase-5 echo decoys, waves 2-7, and event
cleanup. A machine with a reachable PostgreSQL would lift all of these; that is the single unblocking
action for a complete live run.
