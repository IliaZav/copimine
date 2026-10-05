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

## Current checkpoint verification

- Full Python suite after review fixes: **952 passed, 1 skipped**, Python 3.13.
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
- Native Minecraft acceptance for this checkpoint remains **NOT VERIFIED**.
  Compilation, source security review, protocol bots and hashes do not prove
  visual quality or complete gameplay acceptance.

## Remaining acceptance

- Finish Wave 7 tasks A–H: named runtime trials, current-inventory retention,
  death/return/grace, observed W1–6 combat profiles, private owner Echo actor and
  finite copied loadout, bounded Marksman help, Archmage authored attacks,
  versioned durable receipts and restart recovery.
- Then execute the remaining V3 prompts in order; preserve the user's confirmed
  Wave 5 freeze schedule and Wave 6 owned guard groups / QWER accumulating spells.
- Review each actual checkpoint diff with CodeRabbit and the requested security
  review, run builds and relevant full gates, commit and push without force,
  verify exact remote SHA and Actions results.
- Install and verify current plugin/client/pack hashes before claiming runtime
  verification. The currently running runtime predates the uncommitted Wave 7 work.
- Native acceptance is incomplete: camera/animation/effect matrix, two-player
  and five/six-player gameplay, death/return/restart/repeat matrix, and measured
  performance before/after remain mandatory. Bots and logs cannot prove rendering.

## Evidence locations

- `artifacts/end-rift-waves/20261004/`: full Python/client/gate/validator logs,
  CodeRabbit receipts and `waves1-5-runtime-20261004-234831` installation/live logs.
- `artifacts/end-rift-waves/20261005/`: Wave 7 red/green regression receipts.
- Private local artifacts, worlds, player inventories/skins/profiles and secrets
  must not be committed or uploaded as review inputs.
