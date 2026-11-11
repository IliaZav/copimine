# Release candidate `rc-0f9b69c8` — verification and deployment

Built from `0f9b69c84aa3fc2daae9ac7c3ff871ca19ba8ec5` on branch
`codex/rift-guardian-polish-20261110`. Nothing in this bundle has been run on a
Paper server or in a Minecraft client, and nothing has been deployed.

## What was verified, and how

| Claim | Evidence |
|---|---|
| Full Python suite green | `python -m pytest tests -q` → **3353 passed, 18 skipped, 0 failed** in 683 s |
| Monolith compiles against the real API | `scripts/minecraft/RunFullTreeCompile.ps1` → 384 sources, 1058 classes, exit 0 |
| Client jar is reproducible | Two independent Gradle builds produced `10b49e7b...eeed6862`, equal to `profile.lock.json → clientArtifact` |
| Server plugin builds from this commit | `copimine-end-event/build-plugin.ps1` → 2119437 bytes, exit 0 |
| RC client matches the pin | size 15013727 and sha512 equal to the lock, checked field by field |

## What was NOT verified

- **No Paper server was started.** No wave was played through, no boss phase
  reached, no block mutation observed to restore.
- **No Minecraft client was opened.** Every visual claim in the ledger is
  unverified by eye.
- **No performance measurement.** No MSPT or FPS numbers exist for this build.

`manifest.json` records all four of these as `false` rather than leaving them
implicit.

## The installed client is stale, and this matters

The user's Prism instance holds `CopiMineClient-0.1.3+26.3.jar` at **15013152**
bytes, `sha512 4810de65...`. The bundle here is **15013727** bytes,
`sha512 10b49e7b...`. Fifteen archive entries differ, and all of them are the
Wave 5 fog system — including `BlackFogGuidePolicy.class`, which the installed
jar does not contain at all.

Measured from the user's own screenshot `2026-11-10_23.31.34_3.png`, the upper
field is `rgb=(16,24,31)`, blue over red by +15. The installed jar's palette
gives +19; this bundle's violet gives +5. The photograph is of the old build.

Until the jar below is installed, the fog the user sees is not the fog this
branch draws.

## Deploying the client, on the user's machine, by hand

Nothing here is automated, and it should not be run by an agent: it overwrites
the user's own client.

```powershell
$mods = "$env:APPDATA\ElyPrismLauncher\instances\Copimine\minecraft\mods"
$new  = "<path>\artifacts\rc-0f9b69c8\CopiMineClient-0.1.3+26.3.jar"

# 1. Back up first. A .bak-* suffix is not loaded by Fabric, so it is safe.
Copy-Item "$mods\CopiMineClient-0.1.3+26.3.jar" `
          "$mods\CopiMineClient-0.1.3+26.3.jar.bak-$(Get-Date -f yyyyMMdd-HHmmss)"

# 2. Confirm the replacement before copying anything into place.
(Get-FileHash $new -Algorithm SHA512).Hash.ToLower()
# must equal 10b49e7b4f7cfc677d9d7e643676a78be5a5cd0a86250a456cbf204debbe5c63fcbec8d74c15d991ac04fdc7f6dddc415a736c6b5ab72941a307c545eeed6862

# 3. Install.
Copy-Item $new "$mods\CopiMineClient-0.1.3+26.3.jar" -Force
```

Rollback is the reverse: delete the new jar and rename the `.bak-` file back.

## Deploying the server plugin

`install_guardian_polish.py` in `guardian-polish-final` targets
`/opt/copimine-game/minecraft/server` over SSH. It was written for the earlier
bundle and pins older plugin hashes, so it does **not** describe this bundle.
Do not run it against `rc-0f9b69c8` without repinning its expectations.

The safe manual sequence is the same shape: stop `copimine-game.service`, back
up the existing plugin jar, copy the new one, start the service, then read the
log for `SEVERE` and watchdog lines before telling anyone it worked. The world,
the database and `server.properties` are not touched by a plugin swap.

## Files

| File | Size | sha256 |
|---|---|---|
| CopiMineEndEvent.jar | 2119437 | `3ec979e9e87045e7381c1652691e0d85fd5c7626c5a34c99f34dcd70aad29c9f` |
| CopiMineClient-0.1.3+26.3.jar | 15013727 | `ac0a8e81961ba76c9f25216d8329b4addc6e5218bff461bb3dd483a30b75c829` |
| CopiMineResourcePack-26.3.zip | 33371080 | `17aa6d3b2edf626f6d0740d2823d542500836277fbf3cecfd0de54e5e83d8eb9` |
| CopiMineNarcotics.jar | 370499 | `026787808eac784f1e0c2f48952c625c98a914e41f650d1b7e54521774835fb1` |

`manifest.json` carries the full sha1 and sha512 for each.
