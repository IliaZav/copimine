# Echo source admission: evidence and remaining acceptance

Source baseline: `a44d66715742cd7eeffe5ec1f86d509dc8a0f796` on
`codex/end-rift-event`. Its push run `37572636680` and PR run `37572641383`
both completed success. Those receipts do not prove this subsequent patch.

## IMPLEMENTED

The existing local Echo carrier had no source admission before native damage.
Shield/armor observers checked the current owner session, but that did not
prevent a helper from damaging the replica or applying a healing potion.
The generic wave direct-health path deliberately excludes this carrier.

`EchoCombatAdmission` binds one immutable event/generation/runtime epoch/duel,
owner, actor and world. The probe supplies its actual presentation duel/epoch
and current owner liveness, dimension and client capability. Closing/failed
cleanup contexts retain their identities while admitting no combat.

`EchoCombatAdmissionListener` filters damage, target selection, ignition,
projectile entity collisions, splash intensities and lingering-cloud targets.
Only owner and their own replica can inflict hostile pair damage. Admitted
potions may affect their own source as vanilla self effects. Pets and helpers
are distinct entities; a causing owner UUID does not override a foreign direct
source. Contradictory `DamageSource`/event-damager chains are rejected. Legacy
entity events with an empty source still use their actual event damager.
Unrelated ordinary interactions and native fall/environment damage remain
under their current Minecraft rules.

Projectile receipts bind the actual native handle, source and original scope.
No missing/changed-source/old-generation/old-epoch/expired receipt is admitted.
A duplicate callback cannot renew or rebind it. Lingering clouds inherit that
same launch lifetime. The existing five-tick loop visits at most 64 projectile
and 16 cloud handles, with a maximum lifetime of 1,200 ticks. Capacity rejects
additional paired launches rather than silently forgetting ownership. No world
scan, path calculation, new timer, scoreboard team or global PvP change exists.

Paired sources are nonpersistent so chunk unload cannot save an attack without
its receipt for a future restart. Replica arrows cannot be picked up as real
items. Cleanup removes only recorded handles; failures retain retry references
and close the scene's admission before further effects. The plugin's existing
close/terminal paths clear these resources with the carrier.

## AUTOMATED_PASS

- Portable source-executing focus after review: **104 passed** (57 admission cases, 13 actual
  plugin-context/cleanup cases and 34 actual carrier cases).
- Final registered Echo group: **144 passed**; complete End Rift gate passed
  after the five review regressions and repair.
- Pinned Paper 1.21.1 plugin compilation/packaging passed; five existing
  deprecation warnings. The adapter uses the actual pinned API.
- Final full Python 3.13: **1125 passed, 1 skipped, 88 existing warnings**.
  The optional selected-profile JAR check ran separately: **1 passed**.
- Final all **659/659** validators passed, zero failures/skips; source diff
  whitespace check passed. Both were repeated after the review repair.
- CodeRabbit reviewed the 12-file immutable public range `e313fc01..19dee9cb`,
  source baseline `a44d6671`: **1 issue**, completed/CLI exit 0. The first command
  used unsupported `--cwd` and failed before review; its log is retained. The
  corrected command reviewed the public snapshot without private runtime data.
- Verified review issue: rune start could commit the official ritual while a
  local pair remained active. Regraded from minor to important because a test
  filter could affect official gameplay. Five actual method regressions failed
  first, then pass: phase fence, another probe, start cleanup, failed cleanup and
  retry. Shared eligibility now applies at start/tick/admission; ritual start
  clears the local scene before deadline/controller/phase mutation. Failed
  cleanup leaves READY_FOR_PLAYERS and retries safely. This repair is subsequent
  to the immutable review; no claim that CodeRabbit re-reviewed it.
- Exact new-SHA CI/publication receipts are recorded in PR #3 after normal push,
  without a self-referential source SHA in this commit.

## Installed local runtime

The final source build and installed End Event JAR both have SHA-256
`2037380c0b9d4474e50b60d1edb41d428f427ce1b5261dbb7893df4bebcccbca`.
The isolated server is listening at `127.0.0.1:25566`, PID 35560, startup
`Done (32.044s)`. Actual RCON reports COLLECTING, zero players/participants/event
mobs and no boss. All 30 installed plugin JARs match their current canonical
copies, zero mismatches. The ownerless Echo command correctly refuses to start;
that refusal is not a gameplay receipt.

Selected client SHA-256:
`f77b5b2e27e77f2bd20a580c945b7b0429460f1d8c279711e8e3822ae659517b`.
Pack SHA-256:
`55adefa07a35d1ee9323ede224a32611f0de4a583acaf7dee88e5703f5174ddc`;
real HTTP bytes match SHA-1 `d33363385a6ad31577e87c2669a8e5e9b6a46a95`.
Client and pack are unchanged in this checkpoint. No Minecraft window was
present at the final process check. Hashes/HTTP are not proof of native rendering.

The first permissive baseline run failed 38 cases and passed 11: actual foreign
effects and missing lifecycle binding, rather than a missing-dependency compile
failure. Additional regressions caught legacy-source/contradictory-damager/
self-potion behavior (3 fail/3 pass) and cross-world/unload persistence (2 fail).
All are green in the final focused run. Red/green logs are private local evidence
under `artifacts/end-rift-waves/20261006/echo-loadout/admission-*`.

An initial full-suite command selected system Python 3.14 and stopped collection
because its environment lacks FastAPI (2 collection errors). Its log is retained.
The supported CI interpreter is Python 3.13; no assertions or dependencies were
removed to bypass that failure.

API references: [DamageSource](https://jd.papermc.io/paper/1.21.1/org/bukkit/damage/DamageSource.html),
[PotionSplashEvent](https://jd.papermc.io/paper/1.21.1/org/bukkit/event/entity/PotionSplashEvent.html),
[AreaEffectCloudApplyEvent](https://jd.papermc.io/paper/1.21.1/org/bukkit/event/entity/AreaEffectCloudApplyEvent.html),
[ProjectileHitEvent](https://jd.papermc.io/paper/1.21.1/org/bukkit/event/entity/ProjectileHitEvent.html).

## NATIVE_VERIFIED

**NOT PERFORMED in a real Minecraft client for this patch.** Compilation and
portable/native-boundary tests do not prove source attribution, body collision,
rendering or balance in an actual multi-owner duel. No Minecraft client was
launched during the user's authorized code pass. Current source installation
and server restart must be recorded separately; they are deployment identity
evidence, not gameplay acceptance.

## Native manual matrix

Use the current local server/profile/pack and record their hashes, source SHA,
event/generation/actor/owner/action/tick and cameras. Do not spoof capability or
resource-pack acknowledgments. Do not publish real inventories, profiles or skins.

1. In COLLECTING/READY_FOR_PLAYERS with no encounter, authorized owner A starts
   `/cmend test echo loadout A`. Observer B may view this same scene. Verify A's
   native melee, axe, arrow and shield/armor outcomes; B's melee/sweep/arrow/fire
   cannot damage or wear A's replica. Confirm no double HP transaction.
2. Test A/B splash damage and healing potions, lingering clouds, pets and replica
   Thorns. Record accepted source and victim along with before/after HP/effects.
   Paired effects must not reach B; B's effects must not reach A/the replica.
   Verify owned shots pass an unrelated body rather than being consumed by it.
3. Launch an admitted arrow/cloud, then stop/replace the scene, kill/quit A,
   change dimension/capability or reset generation. Old sources must be removed
   or rejected, and no effects survive a repeated start. Test chunk unload and
   restart separately; sources cannot return from a saved chunk.
4. After `/cmend test echo stop`, normal B/ordinary-mob damage, potions and
   pickup behave normally. Repeat ten starts and compare tracked entities/tasks
   to baseline. Confirm failed cleanup can finish without another item/effect.

## Open full-goal requirements

This is one local scene, not two simultaneous official personal duels. The named
official Echo trial, exact private volume/body collision/helper entry/exit,
source geometry at impact, outgoing action executor, player hitbox/critical/item
parity, persona, durable pause/resume/outcomes and two-owner/5-6-player native
matrix remain open. No legacy Reflection success is called an Echo win.

Unattributed custom-plugin direct health/effect changes cannot be inferred from
an entity-less event. Explicit first-party custom-item adapters and their source
receipts are still required before claiming all effects are isolated. This patch
is outside the earlier Codex Security seal. Boss/Kagune and Wave 6 input/spell
semantics are unchanged. Full Waves 1-7 completion remains open.
