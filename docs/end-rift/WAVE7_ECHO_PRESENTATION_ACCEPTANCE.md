# Wave 7 Echo presentation checkpoint

Source baseline: `36775775d769e0c87dd1fc4943dbaf8c7e403fab`,
`IliaZav/copimine`, `codex/end-rift-event`.
This is the V3 Task D presentation adapter and local probe. Personal duels,
the final named trial runtime and native animation parity remain unfinished.

## IMPLEMENTED

- One real Husk remains the selection/health carrier. A detached
  `OtherClientPlayerEntity` has a deterministic, distinct identity and is
  rendered through the pinned vanilla player renderer. It is never inserted
  into the client world, player list, login or server networking.
- Compact bridge v2 semantics include event/generation/epoch/duel/actor/owner,
  sequence, pose, sprint, active hand, elapsed use, duration, equipment version,
  swing, hurt and death serials. Normal native carrier packets supply movement,
  head/body orientation and equipment. No bone-transform stream is added.
- Existing owner skin-provider results retain classic/slim selection. Tint is
  scoped to skin buffers; armor/items and real players retain their colors.
  All vanilla outer layers are enabled on the detached view. Purple eyes use
  the native head UV/transform with an original transparent 64x64 mask.
- Sequence, epoch, identity, lease and removal fences reject stale states and
  delayed skin results. Native client entity removal retires a packet-owned
  identity even if it has never been drawn; world/death/reset cleanup clears
  views and active use. Counts are bounded to 32 live actors/128 identities.
- Fixed test gear belongs only to the local carrier. Its death exits before
  generic event loot/progression and suppresses gear, rewards and XP. Real
  inventories, frozen duel loadouts, official roster/profiles and rewards are
  not read or modified by this presentation probe.
- The probe shares the existing five-tick encounter loop. Routes are requested
  at most once per 40 ticks, in a short lane near its initial safe floor; no
  chase teleports, world scans or per-action scheduled tasks are introduced.
  Only this carrier's native goals are removed. It stays aware so Purpur/Paper
  can tick its native navigation and movement controls.

## Root causes and regressions

1. The bridge's effect normalizer changed `ECHO_PRESENTATION_V1` to `CHAOS`.
   The actual hello/capability-update regression failed before the normalizer
   was corrected; both now retain the capability without adding it as a shader.
2. Render-cache cleanup alone could leave a removed actor's semantic binding
   live. State retirement and the native removal hook make that scope terminal.
   State tests also cover remove-before-bind, reset versus local clear, expiry,
   classic/slim skin selection and obsolete texture tickets.
3. The first carrier adapter disabled awareness. Inspection of the running
   Purpur 1.21.1 `Mob.serverAiStep` showed its early return before navigation.
   Executing the production adapter against that navigation boundary produced
   `moves=0`; removing only its goals and keeping awareness passes. This is an
   adapter regression, not proof of real Minecraft movement or collision.
4. Full verification initially found the old staged client/modpack and a missing
   tick-fixture dependency. The build script refreshed the distributed artifacts;
   the fixture now also asserts exactly one probe tick behind the boot fence.
   Existing artifact equality and wave/boss dispatch assertions were preserved.
5. Equipment was copied only on a semantic version change. A native equipment
   packet arriving after the first bind could leave armor/offhand absent.
   Executing the original adapter produced that failure; bounded native-tick
   comparisons now copy only changed stacks without requiring a rebind.
6. Local feedback timers advanced only when rendered and could retain an
   expired hurt flash or restart it from a retained serial. The original
   source-executing feedback regression failed; the view now projects the
   carrier's native swing/hurt/death timing instead of simulating a second clock.
7. The detached gait clock also advanced only from the render call. Independent
   review identified the skipped-frame/offscreen defect; executing the actual
   tick adapter failed with `gait froze without render; updates=0`. Retained
   views now project once per native client tick, while render projection never
   advances gait. The regression covers four ticks without drawing, repeated
   draws during one tick, and terminal unload.
8. CodeRabbit's one minor issue identified that non-persistence was applied
   only after world insertion. The spawn consumer now marks the disposable
   carrier non-persistent before insertion; stale-owned-entity recovery remains
   unchanged. The obsolete view feedback serial fields were also removed;
   semantic serial validation remains in the bounded state.

## Public evidence and remaining gates

The new state/drop/asset and carrier-adapter tests are registered in
`tests/RunEndRiftEventChecks.ps1`. Private red/green, build, complete suite,
registered gate and navigation API logs are under
`artifacts/end-rift-waves/20261006/echo-presentation/` and are not review uploads.
The build uses the current Minecraft 1.21.1 / Yarn 1.21.1+build.3 baseline.

The first immutable public range was `9908a5fc..4f778c0b`: CodeRabbit completed
with one minor issue and excluded four binary files. Independent source review
reported one important gait issue and one minor unused-field issue. All three
were corrected in the working source and the actual adapter regression passed.
The corrected range, complete verification and publication receipts are
recorded separately; the first review cannot prove later edits.

### Executed corrected-checkpoint checks

- Full Python suite: **994 passed, 1 skipped**, 88 existing warnings;
  `full-python-reviewed.log`, exit 0. Client build: **265 JUnit tests**, zero
  failures/errors/skips; `client-build-gait.log`, successful Gradle build.
- End Event server build, rebuilt staged client/modpack and registered
  `RunEndRiftEventChecks.ps1 -SkipBuilds` all exited 0. Existing release and
  unrelated wave/boss assertions were retained. `git diff --check` passed.
- Corrected public range `4f778c0b..599f3174`: CodeRabbit completed with
  **0 issues**, ten text files reviewed and two rebuilt binaries excluded.
  The independent review's P2/P3 corrections were traced and regression-tested
  by the parent. Its requested correction-only follow-up failed with
  `Your workspace is out of credits`; no independent correction approval is
  claimed. Its listed native/combat/binary/CI exclusions remain explicit gates.
- Codex Security scan `dc783294-0e06-45c3-85ca-7eb908b44c6e` sealed the original
  immutable range with zero confirmed vulnerabilities and **partial coverage**.
  It reviewed all 18 source inventory rows and accounted for 36 changed files;
  native composition, concrete deployment and later corrections remain outside
  that seal. The independent architecture model and exact boundaries were
  retained. No patch-specific token usage was returned by its sealed documents.

### Actual isolated installation

The local server reached `Done (27.162s)` on 2026-10-06 and End Event reported
services ready in COLLECTING, generation 1315, zero online players/active wave
zero. All **30 installed plugin JARs** matched current source distribution
JARs. The current artifacts were actually installed and hash-checked:

| Artifact | SHA-256 |
| --- | --- |
| End Event plugin | `641b8535c01dcb6640e6f73a2ce2db6bdbed516b5fb9cb078a7a1f3884a641e8` |
| CopiMineClient | `f77b5b2e27e77f2bd20a580c945b7b0429460f1d8c279711e8e3822ae659517b` |
| Required resource pack | `55adefa07a35d1ee9323ede224a32611f0de4a583acaf7dee88e5703f5174ddc` |
| Rebuilt modpack | `7a648481957a7e76b68f9d90b80a8f4b948d7cebfe2185aee2e3815f8659a701` |

The client/pack matched the `ServerRP_copy_1` profile with exactly one active
CopiMineClient JAR. An actual local HTTP GET returned 28,363,975 pack bytes
matching SHA-1 `d33363385a6ad31577e87c2669a8e5e9b6a46a95` and the required-pack
configuration. No synthetic pack-success acknowledgement was sent. A console
probe start with no owner correctly refused; repeated stop returned safely.
Those responses prove admission/command routing only, not rendered mechanics.

The previous local processes had stopped without a recorded graceful shutdown.
PostgreSQL's log recorded automatic recovery and readiness; the startup
controller's unavailable ExitCode was mistakenly reported as startup failure.
Actual `pg_ctl status` and the bound listener established it was running before
the HTTP/Paper restoration continued. No database reset or world replacement
was performed. Installation and these factual receipt paragraphs were verified
after the immutable code reviews; they are not additional reviewed gameplay.

NATIVE_VERIFIED: **none**. No native client was launched for this checkpoint.
The source build and full tests do not prove Mixin injection, skin loading,
item pose, animation timing, readable tint/eyes or a second body being absent.
The local probe is a Husk carrier; exact player body/selection/crouch collision
agreement has not been established. That limitation blocks Task D acceptance
and any claim that the personal Echo duel is finished.

Bow/crossbow/eating commands project use poses and completion/cancellation;
they do not shoot, consume, heal or model finite duel supplies. Native crossbow
loaded-hold/release parity and critical attacks need the authoritative combat
adapter in Task E. No cosmetic critical flag or synthetic successful pack
acknowledgement is used to hide these gaps.

## Exact manual scene

Use the isolated local server at `127.0.0.1:25566`, the current source-built
CopiMineClient and required pack. Authenticate normally; no credentials are
provided or stored by this checkpoint. Start only with the official encounter
inactive, phase COLLECTING/READY_FOR_PLAYERS and active wave zero, near a safe
arena floor. The command requires the existing `copimine.endevent.test`
permission and `environment: local`. Missing capability refuses creation.

```
/cmend test echo start
/cmend test echo idle
/cmend test echo walk
/cmend test echo sprint
/cmend test echo crouch
/cmend test echo jump
/cmend test echo swing
/cmend test echo bow
/cmend test echo crossbow
/cmend test echo shield
/cmend test echo eat
/cmend test echo hurt
/cmend test echo death
/cmend test echo stop
```

Console start requires an online owner name after `start`. Up to eight nearby
capable viewers plus the owner are retained at creation; no ongoing viewer
scan is performed. The scene expires after five minutes or owner
death/quit/world change/capability loss/generation change. Restart it after
death rather than treating a new carrier as resumed duel HP/supplies.

Compare with a real OTHER player in third person, front/side/back, including
walk/sprint/strafe/jump/crouch/swing, bow/crossbow/shield/eat, armor, hurt and
death. Check F3+B selection and body collision, tab list, ordinary players'
colors, and repeated stop/start, chunk unload, dimension change and reconnect.
Record installed server/client/pack hashes and matched footage; mark unsupported
actions and hitbox mismatch explicitly. Do not grant native acceptance from a
command response, packet receipt or entity count.
