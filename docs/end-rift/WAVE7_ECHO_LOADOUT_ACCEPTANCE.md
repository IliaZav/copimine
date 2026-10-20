# Echo copied loadout: scope and acceptance

This checkpoint is part of V3 Task E and [PR 3](https://github.com/IliaZav/copimine/pull/3).
It does not activate personal duels or reinterpret the legacy Reflection trial.
The supplied V3 contracts and [loadout plan](WAVE7_ECHO_LOADOUT_PLAN.md) remain binding.

## Implemented boundaries

- `EchoLoadoutState` freezes 41 source-slot descriptors plus a separate slot
  containing exactly **two additional ordinary golden apples**. Source
  descriptors are immutable; mutable replica quantities/damage have a
  monotonic revision. Supported damageable replicas are repaired only at
  initial creation. Real player stacks are never repaired or mutated.
- `EchoReplicaInventory` reads native storage/armor/offhand once on the server
  thread. The selected hotbar slot represents the main hand, without a second
  copy of that slot. It constructs fresh native items from allowlisted fields;
  no ItemStack/ItemMeta/PDC/nested-container clone is transferred to the actor.
- Consumption and durability changes require the current revision and reject
  duplicates, invalid slots, negative counts and over-consumption. The strict
  bounded map codec includes identity, frozen descriptors and current supplies.
  Restore preserves consumed quantities and wear, validates identity and a
  caller-provided minimum revision, and validates native material limits for
  currently materialized items. Depleted slots materialize as AIR; frozen
  depleted descriptors retain domain bounds and cannot be reactivated through
  this state API. Future journal integration must validate frozen descriptors too.
  **The official duel/persistence journal has not yet been wired to this codec.**
- The existing local probe gains `/cmend test echo loadout [player]` through
  its existing test permission, local environment, inactive encounter, native
  floor and current client-capability checks. Ordinary `echo start` remains
  the fixed-gear presentation scene. Neither mode creates an official claim.
- Copied mode cannot invent a sword, crossbow or shield absent from the frozen
  loadout. A held-main-hand shield projects main-hand use; a real offhand
  shield projects offhand use. Equipment projection prevents a single slot
  from being shown in both hands simultaneously.
- Copied-mode golden apple use requires at least 32 server ticks and consumes
  one replica apple at the next existing five-tick encounter dispatch
  (normally 35 elapsed ticks for command starts), after current
  owner/world/generation validation. Exact native timing parity remains open.
  Repeated completion ticks cannot consume/heal again. Accepted replacement, quit,
  death, stale generation and cleanup cancel pending use. Effects are native
  Regeneration II for 100 ticks and Absorption I for 2400 ticks, checked against
  pinned 1.21.1 `FoodComponents` bytecode. This is not direct instant health.
  Start/completion use bounded spatial vanilla eat/burp sounds.
- The actor retains existing zero equipment-drop chances and Echo-specific
  death drop/XP suppression. No real inventory, reward or profile observation
  is minted by the probe. No new world scan, task scheduler or fake player is
  introduced; the existing five-tick event loop owns item completion.
- The local actor is now a non-undead Pillager with explicit 20 HP rather
  than a Husk, because pinned Minecraft refuses regeneration on undead mobs.
  Native goals remain removed; native navigation awareness remains enabled.
  Raid admission is disabled before spawn registration. The detached client
  player view accepts any bound living carrier, so this changes no client/pack
  source. Body/hitbox and player mitigation parity still require native testing.

## Supported item table

| Item family | This adapter | Remaining gameplay work |
|---|---|---|
| Vanilla swords/axes | Safe material, visible name, applicable bounded vanilla enchantments and replica durability descriptor | Actual melee/cooldown/armor/knockback/critical executor and native parity |
| Vanilla armor/shield | Safe equipment projection, curse/enchant descriptors; actual shield hand | Native player-equivalent mitigation, wear and directional shield/axe-disable behavior |
| Bow/empty crossbow | Safe copied descriptor and correct use pose; absent weapons refused | One authoritative charged release, finite ammo, multishot/Infinity rules and real projectiles |
| Ordinary golden apple | Copied quantities plus two extra; copied-probe completion consumes one and applies native effects | Durable owner-duel pause/resume and balance verification |
| Listed plain food | Immutable finite descriptors (apple, bread, cooked meat/fish, baked potato, carrot/golden carrot) | Real food-use executor/hunger effects; copied probe currently eats ordinary golden apples only |
| Ordinary arrows | Finite supply state and no mint/refill | Projectile executor consumes at the authentic release boundary |
| TNT, beds, tools, other noncombat items | Inert material/count descriptor; never used or projected as a combat item | Explicit future supported-item decisions |
| PDC/custom model/attribute/unbreakable/custom food/tool/stack/durability items | Explicitly blocked; fresh projection is AIR; an overridden custom stack is recorded as a bounded inert marker (one damageable item or clamped vanilla quantity, zero wear) without changing the source | Audit and implement first-party combat adapters; cannot claim perfect custom loadout parity |
| Preloaded crossbow, nested block/container contents, tipped/spectral arrows, enchanted golden apples | Not supported as active items by this checkpoint | Explicit safe ammo/consumable adapters before enabling them |

First-party audit leads: `CopiMineArtifacts.onArtifactInteract`,
`onCrossbowArtifactShot`, `onArtifactDamage`, custom shield and repair handlers
consume catalog/ownership/PDC or Player-only behavior. Those privileges are
not cloned or invoked by this carrier adapter. Relevant first-party combat
items remain a required Task E gap, rather than silent vanilla conversions.

## Regression evidence and limits

- Initial missing-state/adapter assertions establish that the feature was
  absent, not a gameplay reproduction. Actual production carrier regressions
  subsequently failed with `frozen owner bow replaced with free fixed sword`,
  zero completed native apple effects, and the interrupted-use supply case.
  All pass after integration.
- A further actual carrier regression reproduced
  `main-hand shield moved/animated as offhand`; the corrected semantic hand
  state and native equipment pass. Existing fixed-gear walk/death/generation
  regressions retain their original assertions.
- Plain state/actual item adapter checks exercise immutability, source wear,
  exact extra allowance, finite ammo, stale receipts, restore, malformed
  receipts, privileged item refusal and native material/durability validation.
- Focused run after the native-carrier correction: **27 passed**. Full Python
  3.13 suite after the final explicit raid-admission correction: **1011 passed,
  one skipped**, with 88 existing warnings in 107.46s. Default-PATH Python 3.14
  lacked the existing backend FastAPI dependency; that collection failure was
  resolved by using the already configured Python 3.13 environment.
  The final Paper plugin build passes with five existing deprecation warnings;
  the registered End Rift gate, all **659 repository validators** (zero failures
  or skips), and `git diff --check` pass. Client/pack source
  and artifact bytes are unchanged by this checkpoint.
- **NATIVE_VERIFIED: none.** The portable item/native-navigation fixtures and
  actual Paper compilation prove adapter behavior/API compatibility; they do
  not prove rendering, native item-use/armor parity or player-visible balance.

## Review and installed evidence

- Source baseline is `bd2b4033b0da6a7c789a86200689351932e1732f` on
  `codex/end-rift-event`. Its pack-digest correction passed both exact-SHA
  [push Actions](https://github.com/IliaZav/copimine/actions/runs/37522882444)
  and [PR Actions](https://github.com/IliaZav/copimine/actions/runs/37522890042).
- Codex Security scan `311aae24-c9a6-4802-b5b1-fa61869210cb` sealed immutable
  public range `39518a8f..6e90aac9` with zero confirmed vulnerabilities and
  **partial** configuration/native/persistence coverage. Independent architecture
  inspection covered the current item, command, drop and presentation controls.
  The generated coverage's unchanged-context label mistakenly names
  `test_wave7_echo_player_equipment_adapter.py`; the captured helper is actually
  `test_end_rift_death_item_integrations.py`, inspected separately. Neither label
  establishes native proof or a new custom-item integration audit. These factual
  documentation/ledger updates are subsequent to that immutable security range.
- CodeRabbit's initial attempts failed with `rate_limit` (15 minutes, then
  30 seconds). The same immutable 11-file range subsequently completed with
  **one minor issue**: vanilla quantity checks aborted the entire capture before
  recognizing a custom item with overridden stack limits. Actual adapter RED
  reproduced `Invalid source stack quantity` for that blocked custom fixture.
  The correction recognizes privileged metadata first, records a bounded inert
  marker, preserves the real custom stack and still rejects oversized ordinary
  vanilla stacks. The additional regression passes; a two-file correction-only
  follow-up initially hit a four-minute rate limit, then completed with
  **zero issues**.
  Correction range `6e90aac9..4cb440d6` and subsequent documentation are outside
  the earlier security seal. Parent source tracing finds no new executable
  metadata transfer or refill path; this is not another sealed security scan.
- Additional actual Paper reproduction: a Husk at 10 HP rejected Regeneration
  II and remained at 10 HP. The new Pillager species accepted both apple
  effects: health readback rose from 10 to 13 HP after three seconds and
  AbsorptionAmount was exactly 4.0. Both tagged diagnostic actors were removed
  with empty loot tables. This establishes native **server effect compatibility**,
  not the client/probe's complete item-use or PvP parity. The production spawn
  adapter regression reproduced the undead rejection; its correction and
  explicit 20-HP/nonpersistent/no-raid checks pass.
- Carrier-only public range `4cb440d6..27a90804` received CodeRabbit
  `rate_limit` with a 17-minute wait. Its review remains **BLOCKED**; the later
  explicit no-raid line is also outside that attempted range and the earlier
  security seal. No zero-issue or native visual result is claimed for it.
- Empty isolated Paper was gracefully restarted with the current plugin at
  `127.0.0.1:25566`; actual RCON status is COLLECTING/generation 1315,
  zero players/roster/owned mobs and no active wave. An ownerless `echo loadout`
  command correctly refuses startup; no synthetic client acknowledgement was sent.
  All **30** installed plugin JARs match their canonical build/release files.
- Built and installed End Event SHA-256:
  `8a24ce96de283ecddda00cb97194e03ba81ec1af21df37e1a8d6645657902b3d`.
  Built/selected-profile client SHA-256:
  `f77b5b2e27e77f2bd20a580c945b7b0429460f1d8c279711e8e3822ae659517b`.
  Pack SHA-256: `55adefa07a35d1ee9323ede224a32611f0de4a583acaf7dee88e5703f5174ddc`.
  Actual loopback HTTP GET returns 28,363,975 bytes matching advertised SHA-1
  `d33363385a6ad31577e87c2669a8e5e9b6a46a95`. This proves served bytes,
  not native loading or accepted visual behavior.
- Private diagnostics: `artifacts/end-rift-waves/20261006/echo-loadout/`.
  They are excluded from commits/review inputs. Publication and this checkpoint's
  own exact-SHA Actions are separate gates until recorded after pushing.

## Exact manual probe

Use the source-built client/required pack and isolated server at
`127.0.0.1:25566`, authenticate normally, and enter a safe arena floor with
the official encounter inactive. Use a disposable, ordinary vanilla test
loadout with damaged gear, a named/enchant sword, shield, bow, empty crossbow,
finite ordinary arrows and ordinary golden apples. Record original slot
counts/durability before and after.

```
/cmend test echo loadout
/cmend test echo idle
/cmend test echo shield
/cmend test echo bow
/cmend test echo crossbow
/cmend test echo eat
/cmend test echo idle
/cmend test echo eat
/cmend test echo stop
```

Console `loadout` requires an online capable owner name. Compare held hands,
armor and accepted use poses to a real other player in front/side/back views.
Verify use cancellation, completed native effects, finite apples, source
inventory unchanged, no drops/XP, and unload/quit/repeat cleanup. Do not treat
bow/crossbow pose commands as firing: no authoritative projectile executor
exists yet. Main/offhand blocking visuals do not prove directional mitigation.

The probe is deliberately disposable. Stopping/restarting it is a **new test
scene**, not owner death/return or durable duel resume. Those claim/HP/cooldown/
supply lifecycle gates, full action policy, real criticals and private damage
isolation remain unfinished and cannot be declared accepted from this scene.
