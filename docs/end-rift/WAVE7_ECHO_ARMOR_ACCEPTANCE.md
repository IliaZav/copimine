# Echo finite native armor checkpoint

This continues V3 Wave 7 Task E and [PR 3](https://github.com/IliaZav/copimine/pull/3).
Baseline is [2870a8eb](https://github.com/IliaZav/copimine/commit/2870a8ebd90cd73f21a2ced8c4af4633f1b2a93d).
The existing copied-loadout probe remains disposable. Official personal duels,
the four new trials and complete Waves 1–7 acceptance remain unfinished.

## Traced failure and implementation

Pinned Paper/Purpur 1.21.1 has empty `LivingEntity.hurtArmor/hurtHelmet`
implementations for the non-player carrier. Player dispatches into the existing
`doHurtEquipment` path. Consequently a visually armored carrier loses HP while
its equipment does not wear. Rebuilding equipment from unchanged descriptors
would also recreate broken or damaged armor.

The copied probe now has a scoped accepted-damage observer and a narrow cached
bridge to that existing native equipment method. It passes the original native
damage source, original helmet amount, and the accepted base plus BLOCKING and
HARD_HAT modifiers for ordinary armor. It does not use final mitigated HP damage.
Native damage-type tags decide helmet and armor-bypass dispatch; native armor
item/source eligibility, fire resistance, Unbreaking and break notifications
remain in Minecraft. No health write, second damage event or Player emulation
is introduced. Unsupported native signatures/source handles close the local
probe through its retryable cleanup rather than silently inventing a fallback.

Observed boots/leggings/chest/helmet outcomes update only finite replica wear.
Equipment projection retains damaged gear and AIR after a break. Original player
items are never passed to this operation. Owner identity, alive/online/world,
event/generation, capability and monotonic tick checks precede native mutation;
event identity receipts reject duplicate observations. Receipt overflow closes
the disposable scene rather than leaving an actor with ignored equipment wear;
the same correction applies to its existing shield path. These are current-probe
session checks, not completed hostile-pair admission for personal duels.

Armor wear exposed a second failure: whole-loadout revision comparison cancelled
completed eating after an unrelated item changed. A reproduced regression gave
zero apple effects. Pending food now checks its own immutable slot descriptor
and remaining quantity before consuming at the current revision. Supplies only
decrease, so changes to that food slot still invalidate old use. Replacement,
death, quit, generation/capability changes and cleanup retain their cancellation
fences. Eating and armor damage can coexist; no supply is restored or transferred.

Reflection metadata is resolved once per local carrier, with no class search per
hit, new scheduler or world scan. Damage receipt storage is bounded by the
existing encounter tick. Full official duel workload/performance remains open.

## Regression and acceptance scope

RED cases execute the production carrier adapter, native bridge and extracted
event observer. They reproduce unrelated wear cancelling food, absent native
armor dispatch, finite wear/break/projection loss and missing event routing.
Coverage includes bypass and helmet dispatch, original source identity, invalid
and foreign sources, cancelled/unrelated events, fully absorbed damage still
wearing armor, duplicate/stale/quit/dead observations, Unbreaking zero wear,
broken gear and original inventory immutability.

Portable boundary tests do not prove native combat or rendering. Verification
receipts and publication/own-SHA CI are recorded below or in the linked PR only
after actually completing those steps. This code is outside the earlier sealed
Codex Security patch. Client and resource-pack sources are unchanged.

NATIVE_VERIFIED for the real Minecraft client: **none**. The client is not
launched during the requested code-first pass. Armor/body/selection/hurt/sound
parity, incoming hostile-pair isolation, outgoing melee/critical/projectile
rules, Player-only item-damage hooks, first-party custom items, persona and
durable HP/cooldown/supply resume remain explicit Task D/E gates. This checkpoint
must not be called a complete PvP-like Echo duel or official trial replacement.

## Actual native server bridge receipt

AUTOMATED_PASS: final Python 3.13 **1046 passed, 1 skipped**, 88 existing
warnings in 91.91 seconds. Production-adapter focus **48 passed**; registered
Echo group **65 passed**; pinned Paper build (five existing warnings), full End
Rift gate, all **659** repository validators (zero failures/skips) and
`git diff --check` pass. The optional profile artifact test is separately run
with `COPIMINE_PROFILE_CLIENT_JAR`: **1 passed**; it proves selected client bytes,
not a loaded/visible Minecraft session.

CodeRabbit immutable public task diff `d13cbc14..a5e30bdb` reviews nine files and
completes with **0 issues**, CLI exit zero. This factual verification section and
final ledger receipt updates follow the review's immutable document revision;
production source stays identical. No private world, real inventory/profile,
credential or native bytecode is included in the review input.

The current source-built bridge was exercised on pinned Paper/Purpur 1.21.1 in
the empty isolated local server using a private, console-only diagnostic plugin.
It created only temporary server-side Pillagers, used no fake player/profile,
client capability or resource-pack acknowledgement, and removed all seven actors.
This is server mechanics evidence, **not native Minecraft client acceptance**.

Actual readbacks: an ordinary native carrier hit lost HP but left armor wear at
zero; bridging an ordinary 12-point armor input produced three wear on boots and
helmet without changing HP. Fall/bypass produced zero wear. Falling-anvil input
16 on the helmet plus accepted ordinary input 12 produced seven helmet wear and
three boots wear. Fire retained zero wear on netherite boots while iron helmet
wore three. Fully blocked ordinary input zero caused no armor wear. A 1000-point
armor input broke the equipped items into AIR. Cleanup read back seven owned,
zero remaining. The narrow bridge does not claim to verify the whole Echo event
observer or owner-private duel in this server-only receipt.

The first diagnostic fixture wrongly classified `minecraft:generic` as ordinary
damage. Actual native tag data showed it bypasses armor, so the bridge correctly
skipped wear. That failed fixture receipt is retained privately; the corrected
seven-case run uses `MOB_ATTACK` and actually passes. No production rule or test
assertion was weakened to make that diagnostic green.

The diagnostic plugin is removed from the runtime. Clean isolated server is
ready at `127.0.0.1:25566`, with **30** canonical plugin identities matching;
current End Event SHA-256 is
`2d768f6f44f4a705a5f09fc7681df307670df9f67bd755af23db29f608cb379b`.
Actual HTTP pack SHA-1 is `d33363385a6ad31577e87c2669a8e5e9b6a46a95`.
Client/pack sources and selected client JAR are unchanged. Source publication
and actual own-SHA Actions remain a subsequent PR receipt rather than a
self-referential SHA embedded in this commit.

## Manual client verification

On the isolated local server with the current source-built client and required
resource pack, authenticate normally and keep the official encounter inactive.
Use a disposable ordinary armor/shield/apple test loadout and record original
item counts and durability privately.

```
/cmend test echo loadout
/cmend test echo eat
/cmend test echo crouch
/cmend test echo idle
/cmend test echo shield
/cmend test echo stop
```

Compare armor, hitbox, hurt and equipment from front/back/left/right against a
real other player. Hit during eating: an accepted ordinary armor hit must not
silently cancel the completed apple; an explicit action replacement must cancel
it. Verify armor wear survives action changes, absorbed hits still follow native
wear, shield-blocked ordinary damage does not also wear armor, no duplicated
drops/XP and original player gear remains unchanged. Capture native item break
feedback separately as a diagnostic, not as fair-duel balance evidence. Exercise
quit/death/generation cleanup and repeated local scene launches. Starting a new
probe is a new scene and does not prove durable duel resume.
