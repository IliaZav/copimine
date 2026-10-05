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

## Current checkpoint verification

- Navigation/rendering checkpoint: [a841e57c](https://github.com/IliaZav/copimine/commit/a841e57cad25a13acbc16203042458f10b7a25fc), with successful [push Actions](https://github.com/IliaZav/copimine/actions/runs/37262812455) and [PR Actions](https://github.com/IliaZav/copimine/actions/runs/37262816935) for that exact SHA.
- AuthEffects compatibility: [9eb4a759](https://github.com/IliaZav/copimine/commit/9eb4a759881dd2b014b3cd75b89d34a0e560a84e), successful [push Actions](https://github.com/IliaZav/copimine/actions/runs/37265005808) and [PR Actions](https://github.com/IliaZav/copimine/actions/runs/37265010650) for that exact SHA.
- Wave 7 checkpoint recovery: [e6affcdc](https://github.com/IliaZav/copimine/commit/e6affcdcf9897ffd81ba726c5e7753a2aba58f7f), successful [push Actions](https://github.com/IliaZav/copimine/actions/runs/37266815952) and [PR Actions](https://github.com/IliaZav/copimine/actions/runs/37266819724) for that exact SHA.
- Full Python suite after the cancelled-death fix: **955 passed,
  1 skipped**, Python 3.13. AuthEffects and End Event rebuilt against pinned Paper.
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

## Remaining acceptance

- Ruling: retain explicitly identified `legacy-four-trials` runtime until the
  new actor/admission/presentation adapters are connected. The named policy is
  tested groundwork, not replacement gameplay. Renaming legacy actors would
  falsely reinterpret persisted outcomes; Task A integration and Tasks B–H
  remain open. A foreign layout currently requires explicit recovery.
- Finish Wave 7 tasks A–H: named runtime trials, current-inventory retention,
  death/return/grace, observed W1–6 combat profiles, private owner Echo actor and
  finite copied loadout, bounded Marksman help, Archmage authored attacks,
  versioned durable receipts and restart recovery.
- Task B source trace: ordinary respawn currently returns a roster player to
  combat after two ticks; the last committed death still wipes immediately.
  Both conflict with the required bed/explicit-return/grace contract. First-party
  Artifacts donation-loss journaling and Election/Admin official-item restore
  queues must be coordinated with per-death retention to prevent queued copies.
  The cancellation guard does not implement inventory retention or return grace.
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
  scheduler cycles after caster deaths were logged. After the final restart,
  Wave 6 combat runs with five casters, five guards and ordinary pressure mobs;
  all four diagnostic accounts rejoined and were healed to 1,000 health.
- The local AuthMe fixture permits only `EndRiftTarget1..4`; its configuration
  and backup are private runtime artifacts and are not committed. Exemptions
  remain controlled by AuthMe and are immediately revocable.
- Native acceptance is incomplete: camera/animation/effect matrix, two-player
  and five/six-player gameplay, death/return/restart/repeat matrix, and measured
  performance before/after remain mandatory. Bots and logs cannot prove rendering.

## Evidence locations

- `artifacts/end-rift-waves/20261004/`: full Python/client/gate/validator logs,
  CodeRabbit receipts and `waves1-5-runtime-20261004-234831` installation/live logs.
- `artifacts/end-rift-waves/20261005/`: Wave 7 and AuthEffects red/green
  regressions, full suites, review receipts, and `navigation-runtime/` installed
  identities / HTTP verification / protocol-target logs.
- Private local artifacts, worlds, player inventories/skins/profiles and secrets
  must not be committed or uploaded as review inputs.
