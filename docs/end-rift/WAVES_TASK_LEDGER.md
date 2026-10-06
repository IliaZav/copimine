# End Rift Waves 1–7 — implementation and evidence ledger

Repository: https://github.com/IliaZav/copimine/tree/codex/end-rift-event

Current task pack: `END_RIFT_WAVES_CODEX_V3.zip`. Implementation starts with
`01_WAVE7_IMPLEMENTATION_EN.md`, including shared prerequisites. The current
user report additionally requires repairing unstable wave navigation, using
the screenshot's End Crystal beam texture for caster-to-sphere links, and
making the Wave 1 carrier readable. Boss and Kagune redesign remain excluded.

Evidence classifications: IMPLEMENTED means source exists; AUTOMATED_PASS
means a named executed check passed; NATIVE_VERIFIED requires observed native
Minecraft behavior. Unconnected policy code does not count as implemented gameplay.

| Requirement / root cause | Source and regression | Automated evidence | Native evidence | Checkpoint |
| --- | --- | --- | --- | --- |
| Served resource pack differed from advertised SHA-1 | Atomic local pack synchronization and actual HTTP download verification | `test_end_rift_local_pack_sync.py`; `VerifyEndRiftLocalResourcePack.ps1` | Native client log 2026-10-05 00:08:54 accepted cached pack and reloaded server resources. Visual asset matrix remains unverified. | [130010ac](https://github.com/IliaZav/copimine/commit/130010ac95da3d11aecabd95ad4054449feec91b) |
| Test waves skipped objective ticks when AI was enabled | Shared objective dispatch | Full Python: 947 passed, 1 skipped; client: 248 tests; End Rift gate passed; all 659 repository validators passed | Wave 5 live log: three complete safe-zone/fog/restoration cycles, generation 1267. Native fog appearance remains unverified. | [8f2ea607](https://github.com/IliaZav/copimine/commit/8f2ea6075a45f8710c2ee30e59ad38c356b514bf) |
| Rejected Wave 7 snapshot restore published a new generation and erased valid state | `RealitySplitChamberController.restore`; `RealitySplitChamberControllerTest` | Observed failing regression before repair; focused Java and full gate passed after repair | Pending restart matrix | Implemented checkpoint |
| Wave 7 logical passage opened before physical wall restoration succeeded | `tryOpenCompletedRealitySplitPassages`, retryable physical restoration; `test_wave7_passage_commit.py` | Failing production-adapter regression observed; repair and retry/idempotence checks passed | Pending physical wall/collision and repeated-completion checks | Implemented checkpoint |
| Named Wave 7 admission and Echo privacy foundation | `Wave7AdmissionPolicy`, immutable attack scope; `Wave7AdmissionPolicyTest` | 23 focused Java cases and full gate passed | Policy is not connected to live trials yet; new Echo/Marksman/Archmage are NOT active | Tested foundation only |
| Wave mobs repeatedly teleported to the same global fallback; airborne footprints could be treated as invalid | Bounded inward path with hysteresis, per-mob radial emergency point, grounded footprint check; `test_wave_navigation_adapter.py` | Actual adapter failed before repair; edge, airborne, generation, emergency and failed-path fallback cases passed | Old live log proves repeated correction. Corrected native motion is pending | Implemented checkpoint |
| Melee approach continually competed with native attack navigation | Native melee owns the final four-block approach; return/dash state owns movement until finished; native retargeting preserves valid assigned target | Full Python and End Rift gate passed | Pending native combat run | Implemented checkpoint |
| Rift Step teleported to the target and could send a recovery cue after generation change | Eight-tick locked physical dash, swept collision, contact check, generation-fenced recovery; `test_wave_dash_runtime.py`, `test_end_rift_miniboss_counterplay.py` | Teleport/contact and stale-recovery regressions failed before repair; bounded step, wall, dodge, target death and generation cases passed | Pending native telegraph/dodge/recovery verification | Implemented checkpoint |
| Requested Wave 6 beam is the vanilla End Crystal textured beam | Fixed vanilla texture on authored beam mesh; full brightness, purple tint, hand-endpoint interpolation and increased width | Renderer routing/interpolation/clear regressions; 249 client tests and full client build passed | User screenshot is the reference. New renderer has not been observed in Minecraft | Implemented checkpoint |
| Wave 1 carrier only had glow and name | One full-bright authored 3D charge marker above the carrier; moving interpolation and ownership cleanup; `test_wave_carrier_marker_adapter.py` | Actual adapter single-marker/follow/death/idempotent-cleanup tests passed | Corrected marker readability pending native observation | Implemented checkpoint |
| AuthMe permitted local diagnostic names, but AuthEffects still cancelled movement and combat | Optional AuthMe `isUnrestricted(Player)` compatibility; no exemption cached as a login; `test_autheffects_authme_exemptions.py` | Production-method regression failed before repair. Revocation, missing/failing optional API and genuine-login fallback pass. Four local targets actually teleported into the arena and accepted damage | Protocol targets only; native client not launched | Implemented in this compatibility checkpoint |
| Rejected Wave 7 trial restore erased current outcomes and published a foreign generation | Validate all trial states before replacing live state; explicit legacy room identities; `RealitySplitTrialControllerTest` | Observed failing final-room, missing and null-state regression; repaired Java tests and registered gate pass | Native interrupted-recovery matrix pending | This checkpoint |
| Foreign trial schema/layout and conflicting room/trial completions were silently accepted or disabled plugin startup | Versioned legacy codec and recoverable startup helper; `Wave7TrialMigrationTest`, `test_wave7_checkpoint_recovery.py` | Actual decoder/adapter RED observed; GREEN covers valid legacy, unsupported layout/schema, malformed and conflicting receipts; registered in End Rift gate | Live four-room sandbox restored generation 1275 after clean server restart, then changed to Wave 6 without invariant failures. Native acceptance pending | This checkpoint |
| Cancelled Paper player death released controls and could wipe the last living participant | Cancelled-event registration plus defensive entry guard before every side effect; `test_end_rift_cancelled_death_adapter.py` extracts the production callback and uses the real lifecycle controller | Actual RED assertion observed before repair; cancelled callback retains roster, prisoner, bridge and rune state; committed-death cleanup remains covered; registered in End Rift gate | Native cancellation/death matrix pending | This checkpoint |
| Participant inventory could remain in slots while inventory-origin death drops were emitted | Per-lethal-event ownership receipt, current slot descriptors, bounded identity/quantity subtraction; `EventDeathProtectionListener`, `test_end_rift_inventory_listener.py`, `test_end_rift_death_eligibility.py` | Source-executing regression covers W1–7, last participant/two same-tick deaths, stale/withdrawn/cancelled/test/foreign membership, shared slot handles, independent rewards, current quantities, XP and cleanup | Actual isolated-server same-tick deaths preserve both synthetic 41-slot NBT inventories without ground copies; no native rendering claim | Inventory checkpoint; both SHA workflows passed |
| Artifacts donation-loss journal and Election/Admin recovery queues could create another entitlement before keepInventory was applied | Exact-event optional protection query in all three existing death callbacks; `test_end_rift_death_item_integrations.py` | All three actual baseline callbacks failed the no-extra-copy assertion; repaired callbacks pass with missing/disabled/throwing optional provider and ordinary/cancelled controls | Custom-item recovery integration is tested in source; full native item/return matrix remains pending | Inventory checkpoint |
| Installed ClearLag copied HIGH death drops, cleared the event and emitted the copy one tick later, bypassing HIGHEST removal | Reversible `DeathDropForwardingIntegration` hides only owned quantities from known ClearLag callbacks, restores the event view in finally, preserves ordinary/independent drops; registration rollback and disable/re-enable coverage | Actual deferred-copy RED observed; repaired callback, partial merged remainder, original exception, idempotent registration and teardown regressions pass | Real installed ClearLag reproduced retained inventory plus ground copies before repair. Fixed official rune-started attempts passed NBT and positive ordinary-drop controls twice | Inventory checkpoint |

## Current checkpoint verification

- Navigation/rendering checkpoint: [a841e57c](https://github.com/IliaZav/copimine/commit/a841e57cad25a13acbc16203042458f10b7a25fc), with successful [push Actions](https://github.com/IliaZav/copimine/actions/runs/37262812455) and [PR Actions](https://github.com/IliaZav/copimine/actions/runs/37262816935) for that exact SHA.
- AuthEffects compatibility: [9eb4a759](https://github.com/IliaZav/copimine/commit/9eb4a759881dd2b014b3cd75b89d34a0e560a84e), successful [push Actions](https://github.com/IliaZav/copimine/actions/runs/37265005808) and [PR Actions](https://github.com/IliaZav/copimine/actions/runs/37265010650) for that exact SHA.
- Wave 7 checkpoint recovery: [e6affcdc](https://github.com/IliaZav/copimine/commit/e6affcdcf9897ffd81ba726c5e7753a2aba58f7f), successful [push Actions](https://github.com/IliaZav/copimine/actions/runs/37266815952) and [PR Actions](https://github.com/IliaZav/copimine/actions/runs/37266819724) for that exact SHA.
- Full Python suite after the inventory/ClearLag repair: **961 passed,
  1 skipped**, Python 3.13. End Event, Artifacts, ElectionCore and AdminPlus
  rebuilt against pinned Paper; installed copies match their source JAR hashes.
- Full client build: **249 tests, zero failures**. Server plugin build passed.
- End Rift gate passed after the final source changes; all **659 repository
  validators passed**, with zero failures and zero skips.
- CodeRabbit reviewed the scoped 20-file patch, found one minor failed-path
  issue, and completed the three-file follow-up with **zero findings** after
  the issue was reproduced and repaired.
- Codex Security scan `e54c6576-bddd-4f7f-aa06-6f4abc51162a` completed a scoped
  source review of immutable `15dc7240..852c64f7` with no reportable findings.
  The independent architecture worker was unavailable due workspace credits;
  the parent performed the sequential fallback. The later two small follow-up
  changes are outside that sealed security range and were separately reviewed
  by CodeRabbit and the executed regression/build gates.
- CodeRabbit separately reviewed the two-file AuthEffects compatibility diff.
  Its optional-provider failure finding was reproduced before repair; the
  follow-up completed with **zero findings**. This compatibility diff is outside
  the sealed security scan above.
- CodeRabbit reviewed the seven-file initial trial-checkpoint diff with **zero
  issues**. Its follow-up initially returned `rate_limit` (34 minutes, then
  one minute until reset). After the allowance reset, the immutable four-file
  range `2b3318f7..1c7ae3ee` completed with **zero issues**, covering receipt
  consistency, gate registration, and cancelled-player-death source/regression.
- Codex Security scan `ee2605a8-cdf2-42f4-bb31-35ca2a98423b` reviewed the full
  immutable seven-file range `6887fe93..12575704`, including that follow-up,
  and completed with **zero reportable findings**. Three production files were
  reviewed sequentially in the parent; no independent worker was used. Measured
  tool usage: 1,118,630 total tokens, including 1,094,400 cached input tokens
  and 4,735 output tokens. This is a scoped source review, not native acceptance.
- Codex Security scan `60f2939c-9c99-448c-bb59-5d9b9b4a4867` reviewed the
  immutable cancelled-death range `12575704..1c7ae3ee`, one production file and
  two regression/gate files, with **zero reportable findings**. The bounded
  review ran sequentially in the parent. Measured usage: 454,169 total tokens,
  including 433,280 cached input and 2,419 output tokens. Inventory retention
  and future return changes are outside this scan.
- Native Minecraft acceptance for this checkpoint remains **NOT VERIFIED**.
  Compilation, source security review, protocol bots and hashes do not prove
  visual quality or complete gameplay acceptance.

### Inventory checkpoint evidence

- Published source: [9134f8e2](https://github.com/IliaZav/copimine/commit/9134f8e2a6da8ee0ddcc80a734b72c15964d86f9).
  Both exact-SHA workflows succeeded: [push Actions](https://github.com/IliaZav/copimine/actions/runs/37332242684)
  and [PR Actions](https://github.com/IliaZav/copimine/actions/runs/37332256560).
- Production retention preserves current storage, armor and offhand slots;
  it does not restore an entry backup, refill supplies, change gameplay world
  gamerules, or change XP policy. Independent reward drops remain separate.
- The installed ClearLag 1.12.1 `onPlayerDeath` callback was traced in bytecode:
  HIGH priority copies drops and clears the event, then schedules the copied
  list one tick later. Actual baseline server logging showed capture=40,
  HIGHEST drops=0, with retained synthetic items also physically dropped.
  The repair leaves ClearLag's JAR and configuration unchanged and guards its
  existing registered callbacks through public Bukkit registration APIs.
- Two successful isolated-server probes used the real occupied-rune official
  Wave 1 path, two participants killed in one command/tick, vanilla
  keepInventory=false, 36 occupied storage slots, four armor slots and offhand.
  Both current 41-slot NBT inventories stayed identical, including damage,
  names and curses; 40 eligible drops per death were removed and zero retained
  copies remained on the ground. Three previously removed apples were not
  restored. The outsider control lost its inventory and produced detectable
  ordinary drops. Probe finally restored the prior local gamerule=true.
- Intermediate fixture repeats exposed invalid controls: an outside death
  location let drops fall toward lava, and same-location respawn picked up
  the ordinary control before scanning. The reusable fixture now kills on
  solid arena floor and respawns the outsider away from the drop. Failed
  controls were not recorded as passes.
- A third actual-server probe passed after installing the final combat-profile
  JAR: two same-tick deaths, 41 byte-equivalent retained slots per participant,
  zero retained ground copies, spent apples unchanged and ordinary outsider
  drops detected. Evidence: private
  `profile-runtime/items-reviewed-pwsh/item-proof.json`. A preceding Windows
  PowerShell 5.1 invocation failed its UTF-8 status precondition before gameplay
  mutation; the successful probe ran in PowerShell 7. That failure is not a pass.
- Source gameplay never toggles keepInventory globally. The destructive
  synthetic fixture refuses to run with any account other than the four
  dedicated local targets online and requires explicit opt-in when changing
  its isolated world's prior rule for the test.
- CodeRabbit reviewed `916cb1f3..8461a6c3` with zero issues, then the actual
  ClearLag integration and fixture range `8461a6c3..9c3a013f` with zero issues.
  The final fixture-only separated-respawn range `9c3a013f..a05c7b9b` also
  completed with zero issues.
- Codex Security scan `6c3833cb-b05b-4b07-a9dd-1ac136adb0c8` completed the
  immutable `916cb1f3..53294942` range with zero reportable findings: all six
  production files and eight regression/gate/fixture files reviewed sequentially
  in the parent. Tool-reported usage: 1,319,212 total tokens, including
  1,256,192 cached input and 8,014 output tokens. Later fixture coordinates
  are outside that sealed range; production Java is identical. Unknown
  third-party emitters or future ClearLag changes require compatibility review.
- Full Python, four plugin builds, End Rift gate, all 659 validators and
  `git diff --check` passed. Client source is unchanged from the prior 249-test
  client build. Native rendering, full item/rejoin/restart/return matrix and
  the rest of W7 Task B remain open.

### Official combat-profile checkpoint

- Main now starts bounded per-attempt profiles with the official roster, samples
  tracked W1–6 combat every five ticks, and snapshots immutable plain aggregates
  into the existing atomic objective-progress store. Numbered wave changes keep
  the same observations; a new roster commit starts a separate profile.
- Accepted hits are recorded after the actual real-health transaction succeeds,
  despite its deliberate vanilla-event cancellation. Shield/rejected hits do
  not count. Event-identity receipts prevent double callbacks; projectile
  provenance distinguishes critical arrows, crossbow shots, thrown tridents and
  melee punches with a held bow.
- Bow release, accepted crossbow loading, shield/sprint/strafe samples, weapon
  switching, attempted swings and completed healing consumption are observed.
  Feature sample confidence and missing W1–6 coverage remain explicit. The
  32-entry recent ring and transient movement anchors are not trajectory dumps.
- Intermissions, stale/dead/disconnected/withdrawn owners, sandbox/creative
  accounts, dedicated 1000-HP targets, forced movement, W5 fog freeze and W6
  confinement are excluded. W7 does not overwrite the long-term persona.
- Source-executing service/listener/Main regressions are registered in the gate.
  Full Python suite: **964 passed, 1 skipped** (Python 3.13); End Event build
  and registered End Rift gate passed. All **659 validators passed**. CodeRabbit
  identified a same-generation receipt-ID reset; an executed RED regression
  reproduced it, and the two-file repair review completed with zero issues.
  The installed local End Event JAR matches the final source build. All 30
  installed plugins, active-profile client and actual HTTP pack identities
  match. Official rune-started W1 saved two empty diagnostic profiles: zero
  counters/coverage proves 1000-HP targets were excluded from observations.
  After the prior server and target processes stopped, fresh readiness checks
  confirmed the listeners were absent before restart. The resumed Paper reached
  its Done marker; all 30 plugin JARs, active-profile mod and downloaded HTTP
  resource pack were checked again. Four diagnostic accounts were observed at
  1000 HP on safe arena floor. Current phase is READY_FOR_PLAYERS, generation
  1293; the private `profile-runtime-resumed/` logs belong to this launch.
  This collector is a prerequisite; private Echo actors,
  owner-only damage attribution and native duel acceptance remain open.
- Codex Security scan `bdc8b77e-e8d0-4951-b2d3-4bd38bf820ed` completed
  immutable `a05c7b9b..bfe86f29`: three production files and seven ancillary
  files reviewed, with zero reportable findings. A fresh-context architecture
  pass was independently checked by the parent. Tool-reported aggregate usage:
  14,770,954 total tokens, including 14,080,128 cached input and 75,951 output.
  The later same-generation receipt correction `cebe8c69` and factual ledger
  updates are outside this sealed range; that correction has its reproduced
  regression and clean CodeRabbit follow-up. Aggregate restoration alone does
  not restore live lifecycle admission after a preserved active cold restart;
  finishing durable participation rights remains mandatory in Task B/H.

## Remaining acceptance

### Wave 7 return source candidate after `21dfe8fe`

- The real bed callback and last-player wipe each have recorded RED failures.
  The source candidate preserves normal bed/fallback respawn, excludes pending
  owners from containment/objectives, and offers explicit owner-only return.
  Entrance position derives from the configured arena/world and combat floor;
  bounded loaded-chunk probes validate support, full standing clearance, fluids,
  temporary hazards and world border. No test-world return coordinates are used.
- The existing lifecycle controls 40-tick staging and incarnation fences.
  Offense cancels protection before dispatch; pending/staging/outside owners
  cannot damage trial actors through vanilla fallback. Death/quit cancels locked
  legacy trial attacks and stops their motion without recreating actor HP.
  Projectiles and directional effects reject an old victim incarnation.
- Last admitted-player loss starts the original bounded 120-second monotonic
  window. Physical respawn and repeated quit/join do not restart it. Expiry
  cleans the attempt and enters recovery without success/rewards. Strict current
  claim/generation rights and the original deadline survive checkpoint restore;
  malformed, missing, foreign or backwards-clock rights fail closed.
- Source candidate evidence after teleport/gateway fixes: **976 passed, 1 skipped**, Python 3.13; affected
  End Event build passed with the five existing removal warnings; registered
  End Rift gate passed, including the new return tests. Unrelated dirty source
  files remain excluded. Candidate review/publication and actual-server
  bed/return/restart evidence are pending. Native verification is pending.
- Initial immutable CodeRabbit review: **1 major issue**, concerning the
  pre-admission chamber teleport. Actual source execution also reproduced the
  outside/bed teleport guard and the pre-boss timer bypassing all-dead grace.
  Exact owner/destination permits now cover internal entrance/room/rollback
  moves and are cleared on cancellation or exception. Pending owners can stay
  outside the arena, but cannot use an ordinary teleport to enter combat.
  The gateway waits for admitted living arena participants while preserving
  its original elapsed 800-tick timer and single-fire behavior. Focused checks
  passed **28 tests**. The six-file immutable follow-up review raised **1 minor
  issue** in a test that reused an already-cancelled event. It is corrected with
  a fresh staging event; no production-code issue was raised by that follow-up.
  The final two-file test/document follow-up completed with **0 issues**.
- Codex Security completed immutable public-source range `742f3544..50b4e445`
  (scan `f0e8b49a-bb9b-4305-a045-b1f56b795647`): seven production/config files
  reviewed, zero confirmed reportable vulnerabilities. Coverage is explicitly
  partial: candidate real-server/native and performance evidence are missing.
  The inherited `combatLevelY` fallback reads blocks before the new resolver's
  loaded-chunk gate, and `return enter` has no independent retry throttle after
  offense cancellation. These remain bounded source/performance follow-ups;
  the scan does not establish an exploit or prove an end-to-end no-load claim.
  No private data was supplied. This review covers this lifecycle slice only.
- Final automated source evidence: **976 passed, 1 skipped**, End Event build,
  registered End Rift gate, **659 validators** and `git diff --check` passed.
  After the final test-only review correction, all three teleport/handoff
  regressions were re-executed and passed. Client/assets are unchanged in this
  checkpoint; the prior verified client has 249 passing tests. Publication and
  current-candidate live return/restart proof remain pending.
- Named Echo actor pause, copied finite supplies, completed-owner private exit,
  ordinary helper admission and per-private-claim abandonment are NOT finished
  by this lifecycle slice. The explicitly identified legacy actors are still
  active; no legacy room receipt is relabelled as a named new trial.

The prior official profile checkpoint was committed/pushed as
`21dfe8fe16c5b719080cd265b34b1a07b2b74e82`. Its exact SHA passed both
[PR verification](https://github.com/IliaZav/copimine/actions/runs/37365347162)
and [push verification](https://github.com/IliaZav/copimine/actions/runs/37365341547).
That was the installed profile checkpoint when the preceding evidence was
recorded. On 2026-10-06 the runtime was updated to the published return/attack
checkpoint described below; current-candidate evidence must be read separately.

- Ruling: retain explicitly identified `legacy-four-trials` runtime until the
  new actor/admission/presentation adapters are connected. The named policy is
  tested groundwork, not replacement gameplay. Renaming legacy actors would
  falsely reinterpret persisted outcomes; Task A integration and Tasks B–H
  remain open. A foreign layout currently requires explicit recovery.
- Finish Wave 7 tasks A–H: named runtime trials, remaining inventory lifecycle,
  death/return/grace, observed W1–6 combat profiles, private owner Echo actor and
  finite copied loadout, bounded Marksman help, Archmage authored attacks,
  versioned durable receipts and restart recovery.
- The user requires the return interaction to derive its safe position from
  the configured arena bounds/world, without fixed test-world coordinates.
  Validate actual ground/collision with bounded local probes; ordinary bed
  respawn and explicit generation-bound return remain mandatory.
- Task B baseline trace: ordinary respawn returned a roster player to combat
  after two ticks; the last committed death wiped immediately. The new source
  candidate repairs both paths and their shared watchdog, as described above.
  Actual-server/native proof and the named actor integration remain pending. First-party
  Per-death retention and Artifacts/Election/Admin queue coordination are now
  implemented and tested, including installed ClearLag deferred-drop handling.
  Full Task B/H acceptance remains open.
- Then execute the remaining V3 prompts in order; preserve the user's confirmed
  Wave 5 freeze schedule and Wave 6 owned guard groups / QWER accumulating spells.
- Review each actual checkpoint diff with CodeRabbit and the requested security
  review, run builds and relevant full gates, commit and push without force,
  verify exact remote SHA and Actions results.
- Installed local runtime at `127.0.0.1:25566`: all **30** plugin JARs match
  current source artifacts. Current client JAR and HTTP pack hashes verified.
  Four named local protocol targets have 1,000 health each; Wave 1 started with
  nine active mobs, all nine acquired players. Actual damage and hurt feedback
  packets were observed. These receipts prove server mechanics, not rendering.
- Live Wave 6 capture placed its prisoner at approximately Y=73.4 over the
  floor at Y=68. Owned guard web/bolt/slam releases and actual gravity/barrage
  scheduler cycles after caster deaths were logged. That earlier Wave 6 combat
  run had five casters, five guards and ordinary pressure mobs. After the
  inventory probes, the current runtime is READY_FOR_PLAYERS with no active
  wave; four diagnostic accounts stand on safe arena floor at 1,000 health.
- The local AuthMe fixture permits only `EndRiftTarget1..4`; its configuration
  and backup are private runtime artifacts and are not committed. Exemptions
  remain controlled by AuthMe and are immediately revocable.
- Native acceptance is incomplete: camera/animation/effect matrix, two-player
  and five/six-player gameplay, death/return/restart/repeat matrix, and measured
  performance before/after remain mandatory. Bots and logs cannot prove rendering.

## 2026-10-06 official route and return bootstrap follow-up

- Published source [2dc65e15](https://github.com/IliaZav/copimine/commit/2dc65e155094c76f42df21f98d8135e4a350eb61)
  repairs Wave 4 special-attack starvation and bounds return-enter retries.
  Both exact-SHA workflows passed:
  [push](https://github.com/IliaZav/copimine/actions/runs/37397165081),
  [PR](https://github.com/IliaZav/copimine/actions/runs/37397171383).
  CodeRabbit completed the immutable six-file slice with zero issues. Its
  completed Codex Security diff review reported no confirmed vulnerability,
  with explicit actual-runtime/performance coverage gaps.
- At that source, all 30 installed plugin JARs matched canonical artifacts;
  actual HTTP pack and active-profile client hashes were verified. The End
  Event JAR was `59c72504d15cfae93afff92925c095f3b4d4ee38e6036afb2d324eee35b67a96`.
- A fresh two-client official attempt actually completed Waves 1–6. All four
  Wave 4 towers fired and pulsed, reflected hits destroyed all four, and the
  normal transition ran. Wave 5 completed all three scheduled fog cycles.
  Wave 6 created five casters/five owned guards and captured a participant.
  It required two manual guard approaches after the diagnostic client kept
  hitting a shielded caster; no objective or damage outcome was forced.
  Elevated health/buffs/position aids make this a server-mechanics receipt,
  not balance or native AI/rendering acceptance.
- Both original owners used the actual public return commands after death.
  Normal fallback respawn stayed outside combat; arena-derived entrance and
  40-tick staging returned each to the original claim in the same generation.
  Complete saved synthetic Inventory NBT matched before/after a further
  passive death/return, with no new ground items in the targeted death-site
  query. Earlier truncated RCON inventory text is not full inventory proof.
- Actual all-dead cold restart FAILED: valid Wave 7 codecs restored, but
  bootstrap's test-only exception let generic transient recovery erase the
  official participation. [Bootstrap repair](WAVE7_RETURN_BOOTSTRAP_REPAIR.md)
  adds a validated official participation exception. Its production-adapter
  RED reproduced that call; four focused tests, full Python **979 passed,
  1 skipped**, End Event build, registered gate and diff hygiene passed.
  Publication/review and corrected actual-server restart remain pending at
  this evidence checkpoint. The built JAR is
  `0cde587605e715d82708bd54b18e3e3e3a047a64bc0416d165520ccb69242cf8`.
- New named actors and Tasks A–H remain unfinished. This run used explicit
  legacy trials. Native footage, valid/destroyed-bed and quit/reconnect
  matrices, actor HP/supply restart receipts and measured performance deltas
  remain open; no NATIVE_VERIFIED status was added.

## 2026-10-06 corrected bootstrap receipts and return chunk boundary

- Bootstrap source [9864eb2c](https://github.com/IliaZav/copimine/commit/9864eb2cc3f36872437ef965e5ddc67d077c2395)
  passed its exact-SHA [push workflow](https://github.com/IliaZav/copimine/actions/runs/37473004728)
  and [PR workflow](https://github.com/IliaZav/copimine/actions/runs/37473013516).
  Its immutable five-file CodeRabbit review raised zero issues; sealed Codex
  Security review found no confirmed vulnerability with partial runtime/native
  coverage. These reviews do not cover subsequent source changes.
- [Actual-server acceptance record](WAVE7_RETURN_RUNTIME_ACCEPTANCE.md) separates
  two official Waves 1–6 and owner death/return receipts from the incomplete
  post-restart admission matrix. The second cold run preserved the exact event,
  generation, claims, original all-dead deadline and one owned legacy Warden.
  Fresh minimal clients remained in CONFIGURATION at the mandatory pack offer,
  so post-restart owner admission, beds and quit/reconnect remain unverified.
  No pack-loaded acknowledgement bypass or relaxed server requirement was used.
- [Return chunk-boundary repair](WAVE7_RETURN_CHUNK_BOUNDARY_REPAIR.md), V3 B/H:
  production-adapter RED caught candidate block reads before chunk checks and
  both inherited Core floor samples in staged admission. Repaired search checks
  the full player clearance before block reads; staged room validation reuses
  the verified entrance height. Two source-executing cases and the first
  integrated **26-case** focused run passed. The full Python run passed **981
  tests, 1 skipped**, and the End Event build and registered End Rift gate passed.
  The immutable six-file CodeRabbit slice completed with **zero issues**; its
  clean public snapshot also passed both new regressions. The built End Event
  JAR is `ebbcda991763175851e0ee80bc85e155563da92e1f5493695da83a63b3651c181`.
  The sealed Codex Security diff review found no confirmed vulnerability, with
  explicit native/chunk-pose, concrete persistence/listener composition and
  positive post-restart admission gaps. The source was published as
  [36775775](https://github.com/IliaZav/copimine/commit/36775775d769e0c87dd1fc4943dbaf8c7e403fab);
  its exact-SHA [push workflow](https://github.com/IliaZav/copimine/actions/runs/37508182423)
  and [PR workflow](https://github.com/IliaZav/copimine/actions/runs/37508189497)
  completed successfully. The isolated server installation was hash-verified
  and restored to COLLECTING with zero online players before the next changes.
- New named actors, native acceptance and the full original Waves 1–7 goal
  remain unfinished. No legacy completion is relabelled as a new Echo duel.

## 2026-10-06 Echo presentation integration (V3 Task D)

- [Presentation plan](WAVE7_ECHO_PRESENTATION_PLAN.md) and
  [implementation/native acceptance record](WAVE7_ECHO_PRESENTATION_ACCEPTANCE.md)
  belong to [PR 3](https://github.com/IliaZav/copimine/pull/3). The current
  named trial runtime remains legacy; no Reflection outcome is an Echo win.
- Ruling: use the existing bridge and one unregistered vanilla player view,
  with a local authorized carrier probe before activating duels. This keeps
  HP/selection on one native actor. Cost if wrong: Task D parity and exact
  hitbox/body agreement remain blocked and the adapter must be corrected
  before live personal-duel admission.
- Ruling: fixed local presentation gear can demonstrate use poses but cannot
  establish finite supplies, healing, projectiles, critical hits or duel
  resume. Cost if wrong: misleading combat acceptance; those gates stay open
  for the authoritative Task E adapter and native proof.
- Actual hello capability normalization RED, remove-before-bind/stale identity
  cases and native-navigation boundary RED are recorded. The carrier adapter
  removes only this actor's goals and preserves native navigation awareness;
  ordinary event mobs and natural mobs retain their existing controllers.
- Corrected source passed 994 Python tests (one skip), 265 client JUnit tests,
  server/client builds and the registered End Rift gate. Independent review
  reproduced render-dependent gait; native-tick projection fixes it and its
  regression covers skipped/repeated draws. The one CodeRabbit minor issue
  was fixed before native carrier insertion; the corrected immutable range
  completed CodeRabbit with zero issues. A correction-only independent follow-up
  failed due workspace credits; the parent traced/tested the corrections.
- Codex Security sealed the original immutable public range with zero confirmed
  vulnerabilities and partial native/deployment coverage. Later corrections are
  explicitly outside that seal; no whole-repository approval is implied.
- Local Paper is restored at `127.0.0.1:25566`, COLLECTING/generation 1315,
  zero online players. All 30 installed plugins match their source JARs; current
  client and pack match the selected profile, and actual HTTP pack bytes match
  advertised SHA-1. Exact current hashes, review boundaries and manual commands
  are in the acceptance record. Native captures and Task D acceptance remain
  **BLOCKED**; complete Echo combat, new trial activation and Waves 1–7 goal
  remain unfinished. Publication/CI for this checkpoint are pending below.

## Evidence locations

- `artifacts/end-rift-waves/20261004/`: full Python/client/gate/validator logs,
  CodeRabbit receipts and `waves1-5-runtime-20261004-234831` installation/live logs.
- `artifacts/end-rift-waves/20261005/`: Wave 7 and AuthEffects red/green
  regressions, full suites, review receipts, and `navigation-runtime/` installed
  identities / HTTP verification / protocol-target logs.
- `artifacts/end-rift-waves/20261005/inventory-runtime/`: private synthetic
  before/after NBT and RCON proofs; successful `items-fixed/` and
  `items-fixed-safe-repeat/`; baseline/trace/fixed server logs. Retained data
  belongs only to dedicated diagnostic names and must not be uploaded.
- `artifacts/end-rift-waves/20261005/profile-runtime/`: final profile-install
  diagnostic admission proof and the third valid same-tick inventory probe.
  `profile-runtime-resumed/`: current launch, plugin identities, HTTP probe,
  and four separate target health/effect/packet journals.
- `artifacts/end-rift-waves/20261006/`: installed-identity and official-route
  receipts, complete synthetic return-inventory fingerprints, the failed
  cold restart, bootstrap RED/GREEN, full Python/build/registered-gate logs.
- Private local artifacts, worlds, player inventories/skins/profiles and secrets
  must not be committed or uploaded as review inputs.
