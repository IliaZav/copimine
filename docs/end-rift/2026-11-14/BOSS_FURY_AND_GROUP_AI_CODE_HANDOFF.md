# Guardian fury and group AI implementation handoff

Status: CODE IMPLEMENTED; automated/runtime/visual acceptance NOT RUN for this slice.
Latest user decision: focus on implementation and leave testing to DeepSeek.
No new test files, test runs, client launch or production deployment in this slice.

## Changes and ownership

* BossGroupAggroPolicy used the last incoming damage timestamp as "longest waiting"
  pursuit history. With three non-attacking players this could cycle between the
  first two candidates. Candidate now carries a separate last-focused timestamp;
  the production adapter records only successful focus changes. Equal history is
  resolved deterministically by UUID. Existing pursuit lock deadlines remain intact.
* The adapter previously never cleared bossRecentAttackers at fresh-boss/cleanup
  boundaries. Damage, focus and retaliation history now reset at all three existing
  pursuit-reset boundaries. Spell target ownership remains independent.
* detectBossStuck previously treated a stationary boss within a valid melee
  opening as stuck and requested a path into its victim. Reach + vertical band +
  line-of-sight now recognizes that opening as successful pursuit.
* BossFuryComboRuntime owns one transient event/generation/boss/target-scoped
  combination. The opening accepted swipe starts two or three strikes; a three
  strike sequence ends in the existing ground slam. Follow-up strikes enter the
  SAME telegraph/contact/recovery pipeline. There is no synthetic extra hit,
  scheduled damage task, early contact, new HP or duplicate hazard owner.
* Full prior recovery completes before a four-tick inter-strike opening. Escaping
  reach/LOS breaks the sequence after at most 20 ready ticks. Invalid/controlled
  targets, wrong event/generation, cleanup and a 240-tick total ceiling cancel it.
  A miss consumes its committed strike. Survivors are never grabbed by a combo.
* Grounded fury uses at least the existing RAGE pursuit speed instead of reverting
  to the slower shielded LAST_SEAL profile. Existing source animation time mapping
  and permanent 3x accepted incoming damage remain untouched.

## DeepSeek verification scope (not a claim of execution)

Verify 2/5/10/11/20 player focus rotation, held locks, incoming attacks, canceled
Bukkit target changes, death/disconnect, repeated test boss spawn and a new encounter.
Verify both combo lengths, real accepted/missed/canceled contacts, final slam,
line-of-sight escapes, moving targets, interruptions, stale generation and timeout.
Verify no strike during preceding recovery, no second direct damage source, no
restart replay, no shield/small-Kagune resurrection, and pursuit after the last hit.
Inspect real client animations, contact timing, hit reactions and geometry.

Only compileEndEventJava was run here (no test task): BUILD SUCCESSFUL, 11 existing
API deprecation/removal warnings. Log: C:/Temp/cm-ai-code-only-20261114-build.log.
This compile also included the subsequent Warden slice. It is not gameplay proof.
Existing release candidate 555565c6 and installed Prism client were left unchanged.
The changed sources require a new server candidate and fresh release verification.
