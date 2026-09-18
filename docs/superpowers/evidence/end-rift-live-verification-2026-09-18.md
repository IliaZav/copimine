# End Rift live verification — 2026-09-18

This record belongs to commit `8a01421b5921f7663c659b917bdfd14553a12632` on
`codex/end-rift-event`.

## Automated and repository gates

- `tests/RunEndRiftEventChecks.ps1`: PASS. The gate rebuilt the Java plugins,
  client, resource pack, and authored boss-pose parity, then passed 237 current
  Python contracts, the Java policy/diagnostic/recovery suite, persistence and
  recovery checks, artifact hashing, and diff hygiene.
- Full pytest: `596 passed, 58 warnings in 11.34s`.
- GitHub Actions: all four check runs for this SHA completed successfully:
  `java-plugins` (two runs) and `static-and-contract` (two runs).
- No production server or installer deployment was performed.

## Paper live evidence

### Boss hitbox and damage authority

The exact-SHA run is recorded in
`local-runtime/boss-hitbox-live-20260918-8a01421-boss.log`. It passed the
oriented profile, proxy removal and self-healing, no-duplicate reconciliation,
melee single-authority damage, carrier-ray miss, projectile UUID dedupe,
last-seal invulnerability, and idempotent cleanup markers.

### Wave 6 ritual sphere

Two consecutive exact-SHA runs of
`tests/RunEndRiftWave6RitualLive.ps1` passed the capture order, restart
rehydration, overdue drain replay, 19.5/20-second drain cadence, one-health
floor, external-damage immunity, projectile origin, zone effects, role
separation, free-target control swap, and completion cleanup checks.

### Wave 7 barriers and restart recovery

The passing end-to-end run is archived at
`artifacts/end-rift-diagnostics/20260918-045615-8a01421b5921/`. Its structured
report is `summary.json`/`report.md` and its live step log records all of:

- physical one-block `BARRIER` wall and connected raster cells;
- journal/PDC restart rehydration with collision and visibility preserved;
- natural server-owned chamber completion and cleanup;
- explicit command cleanup and idempotent state dump.

The first attempt at `20260918-044122-8a01421b5921` timed out before the
natural-completion marker while leaving no diagnostic leaks. It was treated as
a failed/flaky live attempt, not hidden; a fresh-server rerun passed all steps.

The passing report records `diagnosticEventsDropped=0`,
`diagnosticWriteFailures=0`, `entityLeaks=[]`, `taskLeaks=[]`,
`controlLeaks=[]`, `projectileLeaks=[]`, and `wave7RestoreMismatches=[]`.

## Visual evidence boundary

The committed static model board and preview set are under
`artifacts/end-rift-v3-evidence/model-previews/` and
`artifacts/end-rift-v3-evidence/end-rift-mob-model-board-20260916.png`.
They are assembled/UV evidence only. The exact-SHA native Minecraft render
gate was not available in this run: `NATIVE_MINECRAFT: NOT VERIFIED`.
Existing native PNG/MP4 files in the working tree are preserved as user/native
artifacts and are not relabeled as proof for this commit.

## Artifact hashes

- `CopiMineClient.jar`: `c975da6b9cf42cffda2d047cb1686faa3fd84404b51ca12ff162c212aa66ffce`
- `CopiMineEndEvent.jar`: `2607d993bcba3771705300f8f14ac28c30847866da3605ddc8da298bea4833a0`
- `CopiMineResourcePack.zip`: `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9`
- `Purpur server jar`: `30403cf54f981f16e1403f172645e82d3e4a59ad6c9f1d8e98df99edb1f8ae4c`
