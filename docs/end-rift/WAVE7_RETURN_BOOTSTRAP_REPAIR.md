# Official Wave 7 return: bootstrap routing repair

This slice starts at `2dc65e155094c76f42df21f98d8135e4a350eb61` on
`codex/end-rift-event`. It does not complete V3 named trials or native acceptance.

## Actual failure

The isolated Paper run on 2026-10-06 reached official Wave 7 through the ritual
and Waves 1–6. Both synthetic participants died and used the actual public
`/cmend return` and `/cmend return enter` commands. Normal fallback respawn
remained outside the arena; entrance travel and 40-tick staging admitted each
owner to the original chamber, with unchanged event and generation.

The next probe killed both participants, observed the persisted all-dead grace,
then gracefully stopped and cold-started the same server. Boot took 27.512
seconds, within the original 120-second window. Startup first logged
`END_RIFT_WAVE7_SNAPSHOT_RESTORED`, then `RECOVERY_STARTED`, forced
`READY_FOR_PLAYERS` and advanced the generation. The restart probe therefore
failed. A successful decoder was not successful end-to-end recovery.

`tryBootstrap` exempted only disposable Wave 7 from the generic transient
recovery policy. A valid official participation checkpoint still reached
`recoverTransientSession`, which erased the restored claims and return rights.

## Repair and regression

Keep the existing default recovery policy. The bootstrap exception now also
accepts official Wave 7/post-wave return participation when its current
generation owns all of the following:

- the strictly restored participation receipt;
- the validated chamber assignment and trial states;
- the same original roster in the lifecycle and chamber assignment.

The exception does not create admission, reset the grace deadline, or restore
an in-flight action. The existing restoration codec leaves every owner PENDING
with a newer incarnation. The existing all-dead watchdog expires an exhausted
window after entity reindexing. Missing, mismatching or stale receipts still
follow recovery; explicit `RECOVERY_REQUIRED` remains usable.

`test_wave7_return_bootstrap.py` extracts and executes the production bootstrap
and return-context predicate with the actual state machine, participation and
chamber/trial controllers. Its RED execution reached the destructive recovery
call. GREEN checks official and post-wave continuation, unchanged deadline,
pending owners, incarnation advancement, duplicate bootstrap, missing/stale/
mismatching records, explicit recovery, disposable continuation and unchanged
ordinary-wave/boss recovery routing. The registered End Rift gate includes it.

Current source evidence: four focused regressions passed; full Python suite
**979 passed, 1 skipped, 88 existing warnings**; End Event build and registered
End Rift gate passed. `git diff --check` passed. Client and assets are unchanged.
The built End Event JAR SHA-256 is
`0cde587605e715d82708bd54b18e3e3e3a047a64bc0416d165520ccb69242cf8`.

## Evidence limits and next actual-server check

Private receipts are under `artifacts/end-rift-waves/20261006/`:
`wave4-return-live-retry/`, `wave7-return-bootstrap-red-execution.log`,
`wave7-bootstrap-full-python.log`, `wave7-bootstrap-build.log` and
`wave7-bootstrap-gate.log`. Raw worlds, accounts and inventories are not review
inputs or publication artifacts.

The earlier long-inventory RCON response was truncated by Minecraft. Its text
comparison was insufficient; it must not be used as full inventory proof.
Subsequent passive death/return probes read complete freshly saved synthetic
Inventory NBT. Both hashes were unchanged, and targeted four-block death-site
queries found no new ground item entities. This proves those two actual cases,
not the entire armor/custom-item/cancellation matrix.

The official route used elevated-health/buffed protocol clients and test-only
positioning aids. Wave 6 additionally needed two manual approaches to actual
guard positions because the probe kept attacking a shielded caster. Damage
still came from real client attack packets; no objective was forced complete.
These receipts do not prove natural combat balance, navigation quality or
rendering. The active trials were explicitly `legacy-four-trials`, not the new
Echo/Marksman/Archmage implementation.

After reviewed publication and installation, repeat the official route and
both-owner cold restart. Require unchanged original event/generation/claims/
deadline, actual new-client spawn, explicit owner commands, retained complete
inventory NBT and no duplicate actors/tasks/projectiles. Record any failure.
Named actor HP/resources and private Echo pause are separate outstanding work.
Valid/destroyed-bed, quit/reconnect, native visual/action and performance
acceptance are still unverified by this repair.
