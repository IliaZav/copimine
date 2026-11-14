# Inferno restoration ownership repair

CODE IMPLEMENTED; tests, real Paper and Minecraft acceptance delegated to DeepSeek.

The old restore adapter called a void restoreBlock helper, counted its attempt
as success, skipped blocks changed by other owners and still marked the durable
journal restored. A new Inferno called clearArenaInferno without observing its
result and could overwrite retained originals. An unsuccessful markApplied also
logged an error but continued into active damage/heating.

Changes:
* Confirm each actual current BlockData equals its stored original, including
  cells already restored by an earlier partially successful attempt.
* Restore only owned hazard surfaces. Foreign changes are conflicts, not success;
  preserve them and retain every original plus the uncompleted journal.
* Invalid data/write failure/post-write mismatch remain pending.
* Require zero pending cells AND a real successful journal markRestored before
  discarding originals or recording the completed-cast cooldown.
* clearArenaInferno returns its restoration outcome. It disarms the old hazard
  even when recovery retains its originals. Inferno entry refuses on failed clear.
* Brain/direct admission reject an outstanding Inferno original map or retained
  Phase 3 fractures. Both use the shared hazard journal; a new prepare must not
  replace an unresolved previous owner's restoration record.
* markApplied failure immediately cancels/rolls back rather than starting active
  hazard damage. A failed rollback keeps the prepared originals for recovery.

DeepSeek: actual journal prepare/apply/restore failure, partial successful retry,
foreign block conflict, invalid original data, plugin disable/restart, unloaded
world and attempted new Inferno. Confirm exact original BlockData and no false
restored receipt. A foreign block conflict intentionally needs reconciliation;
this change does not overwrite user blocks or silently repair unknown terrain.

Compilation only: C:/Temp/cm-inferno-recovery-code-20261114.log. No test task was
requested. No production world, database, installed client or release pin changed.
