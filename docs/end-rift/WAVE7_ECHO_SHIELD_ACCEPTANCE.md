# Echo native shield checkpoint

This continues V3 Wave 7 Task E and [PR 3](https://github.com/IliaZav/copimine/pull/3).
It extends the existing disposable copied-loadout probe, not the official
Reflection trial or a finished personal duel. The complete V3 tasks remain open.

## Root cause and bounded change

The previous shield command equipped the correct hand and sent a client use
pose, but never called Paper's native `startUsingItem`. The server therefore
had no actual blocking hand. Carrier mobs also inherit empty native
`hurtCurrentlyUsedShield` and lack Player's axe-disable override. Re-equipping
from an unchanged loadout descriptor would erase native durability changes.
These paths were traced in the actual pinned 1.21.1 Paper/Purpur bytecode;
portable production-adapter regressions independently reproduced the missing
native hand and missing accepted-hit side effects.

- Accepted SHIELD actions start the native correct hand. Replacement and
  cleanup clear it. Minecraft owns direction, raise delay, projectile piercing
  and HP mitigation. No health write, cancelled damage or second hit is added.
- A scoped MONITOR observer accepts only an actual negative BLOCKING modifier
  on the current Echo carrier after ordinary damage protections. Cancelled
  events, unrelated entities and unblocked hits produce no shield transaction.
  Projectile damage does not disable a shield because its shooter holds an axe.
- Copied-mode blocks of at least 3 HP request `1 + floor(blocked damage)` wear
  on the equipped replica via Paper. Native enchantment handling and slot-break
  notification run once. The adapter records the observed outcome in finite
  loadout state; zero Unbreaking wear remains zero, and breaking materializes AIR.
  The real owner's item is never passed to this operation.
- A directly blocked axe attack ends use for 100 ticks. A broken shield cannot
  be selected again. This non-player adapter does not emit Player-only shield
  disable/item damage events; compatibility with such third-party listeners
  remains an explicit parity limitation.
- Accepted blocks have bounded spatial vanilla block/break sounds. Native
  block feedback and these sounds still require an authenticated client check
  for duplication, audibility and timing. No particles conceal missing blocking.
- Commit checks retain owner identity, alive/online/world, event/generation and
  client-capability fences. Hit receipts use identity, at most 32 per existing
  encounter tick, without new tasks or world scans. Older raises cannot consume
  current shield wear; duplicate receipts cannot consume twice.

## Verification and limits

RED receipts cover native main/offhand activation, finite accepted wear,
break/axe disable, event observer dispatch and stale hits across a new raise.
The tests execute production Java and the actual extracted server callback with
narrow pinned API boundaries. They do not prove Minecraft rendering or combat
balance. Full Python 3.13: **1028 passed, 1 skipped**, 88 existing warnings in
92.22 seconds. The skip is the opt-in selected-profile artifact check; it is
separately executed with `COPIMINE_PROFILE_CLIENT_JAR` against selected
`ServerRP_copy_1`: **1 passed**. This proves selected JAR bytes, not rendering.
The current registered
Echo gate passes **47** cases. Pinned Paper build, full registered End Rift gate,
all **659** repository validators (zero failures/skips) and `git diff --check`
pass without weakening checks. Client/pack are unchanged.

CodeRabbit reviewed immutable public range `4cb440d6..868c59ed`, including the
current non-undead/no-raid spawn and shield path: **0 issues**, complete, CLI exit
0. This resolves the earlier carrier-only rate-limit gate; final factual receipt
updates after that review are outside its immutable document version. This new
shield code is outside the earlier completed Codex Security seal. No new sealed
security or native acceptance result is implied. Publication/own-SHA Actions
remain a separate receipt after pushing.

The empty isolated local Paper server was gracefully restarted and is left
running at `127.0.0.1:25566`. Current built/installed End Event SHA-256 is
`23aa8d5b6bcdd7c463a4fad675e0b4e3f5059bbb5f2cc0cc7d76a8557b870d48`;
client and pack are unchanged from the copied-loadout receipt. Actual HTTP
resource-pack bytes retain SHA-1 `d33363385a6ad31577e87c2669a8e5e9b6a46a95`.
Universal Modder toolchain CHECK passes, but no Minecraft window is running;
the requested code-first boundary is retained and no client is launched here.

NATIVE_VERIFIED: **none** for this shield checkpoint. Owner-private duel
isolation, melee/critical/knockback/armor parity, authoritative bow/crossbow,
first-party combat items and durable HP/cooldown/supply resume remain Task E.
General armor/weapon wear is not established by this shield-only path. Stopping
and starting the disposable probe is a new scene, never durable duel resume.
Fixed-gear `echo start` is still a presentation scene; finite supplies apply to
`echo loadout`. Client and resource-pack source are unchanged.

## Exact manual acceptance

With current source-built client/required pack, authenticate normally on the
isolated `127.0.0.1:25566` server and use a disposable vanilla test loadout.
Keep the official encounter inactive. Record original item counts and damage.

```
/cmend test echo loadout
/cmend test echo shield
/cmend test echo idle
/cmend test echo shield
/cmend test echo stop
```

Run offhand and selected-main-hand shield cases. Compare a real other player
and Echo from front, side and back. Strike front and back after the native raise
delay; record HP, visible block, sound and replica durability. A blocked axe
should end use for 5 seconds. Test an ordinary arrow and a piercing crossbow
arrow; neither is an axe disable. Verify vanilla Unbreaking, shield break,
replacement/quit/death/generation cleanup, zero drops/XP and original inventory
unchanged. Check repeated commands do not repair wear. Do not call this local
scene the multi-owner private duel required by V3.
