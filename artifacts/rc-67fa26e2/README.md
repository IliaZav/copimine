# Release candidate — 67fa26e2

Built from `67fa26e23927a7ecf5cf96cb8fd5e384044f651c` on
`codex/rift-guardian-polish-20261110`.

## Verified

| Artifact | Size | Pin |
|---|---|---|
| `CopiMineEndEvent.jar` | 2,398,506 | sha256 `7128fc95…` = `server-plugins.lock.json` |
| `CopiMineClient-0.1.3+26.3.jar` | 15,013,971 | sha512 `a8b505bd…` = `profile.lock.json` |
| `CopiMineNarcotics.jar` | 370,499 | unchanged |
| `CopiMineResourcePack-26.3.zip` | 33,371,080 | unchanged |

The manifest was written, re-read, and every artifact hashed again from the bytes
on disk and compared field by field. All four match. The plugin digest was
reproduced by two independent `endEventJar` runs from a clean task graph, so the
build is repeatable rather than merely repeatable-looking.

## Not verified

This is the whole remaining gap and it has not narrowed:

- `nativeVerified: false` — never started on a Paper server.
- `livePaperTested: false` — no wave, phase or boss run with real players.
- `clientVisuallyVerified: false` — no model, animation, spell, fog, geyser,
  Echo copy or Kagune ring has been looked at in a Minecraft client.

3,415 automated tests and the End Rift gate say the code is internally
consistent. They cannot say whether the encounter looks or feels right. Section
26 of the master contract requires screenshot evidence for visual claims, and
the only screenshots in existence are from 10 November against an older
installed client.

Two specific unknowns worth naming, because they are new work and untested by
eye:

- The Phase 5 echo copies and decoys now resolve to the Guardian geometry,
  texture and animation family through three client dispatch arms. That the ids
  resolve is tested. That the resulting picture reads as "another Rift Guardian"
  rather than as a broken clone is not.
- The cocoon repair's fall, landing and final-fury melee are wired and adapter
  tested, but no one has watched the boss descend.

## Supersedes

`rc-bc2efe97` (plugin `af45b9a2`) and `rc-0f9b69c8` (plugin `b1e95954`).
Neither is corrupt; both are simply older than the current pins.

## Deployment

Do not deploy to production, and do not copy the client jar into the user's
Prism instance, without explicit authorization. The installer at
`artifacts/guardian-polish-final/install_guardian_polish.py` still pins
`c4abda95` in its `OLD` dictionary and would refuse this build — checked, not
assumed.

## Rollback

Both targets keep their previous jar. On the client, restore the existing
`.jar.bak-*` backup beside the mod and the previous build returns. On the
server, restore the previous `CopiMineEndEvent.jar` and restart
`copimine-game.service`. Swapping either jar touches no world data, database
migration or arena state, so rollback is a file copy plus a restart.
