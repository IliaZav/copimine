# Wave 7 Warden emergence ownership repair

Status: CODE IMPLEMENTED; automated/Paper/Minecraft acceptance NOT RUN in this slice.
The user requested implementation instead of further test runs; DeepSeek owns
subsequent acceptance. No test file was added or changed.

## Confirmed source integration defects

The previous intro ran in an unregistered BukkitRunnable, used direct teleports
that bypassed the owned movement permit, lacked event/generation fences, and
re-enabled native Warden AI at completion. Meanwhile room reconciliation removed
invulnerability every tick and the ordinary Warden combat ticker could move,
telegraph or attack during emergence. Cleanup did not own the intro timer.

## Implementation

* One WardenEmergence state belongs to the existing trial actor ticker. No second
  scheduled task, teleport owner or damage loop remains.
* Identity includes event, generation, actor and original floor anchor. Movement
  uses teleportCombatEntity and refuses to pretend a canceled move succeeded.
* Intro owns invulnerability, gravity, velocity, targeting and movement. The room
  reconciler preserves protection and combat returns before selecting any attack.
* Completion lands at the exact floor anchor, restores gravity, removes intro
  protection and gives the scripted combat loop a short ten-tick recovery. Native
  AI remains disabled because the existing trial controller owns real combat.
* A PDC checkpoint saved together with the actor contains event/generation/world,
  original floor coordinates/yaw and elapsed intro ticks. Re-adoption resumes the
  same actor rather than starting at its buried location or creating a duplicate.
  Invalid, mismatched or malformed checkpoint data enters explicit recovery.
* Trial clear, encounter-state restore and entity unregister retire in-memory
  intro ownership. Actor removal naturally retires its persistent checkpoint.
* Existing emergence particles and sound beats remain; no unrelated asset/model
  replacement was introduced.

## Handoff for DeepSeek

Check named and historical diagnostic Warden paths, exact emergence boundaries,
no damage/attacks before completion, body height, permitted/refused relocation,
roar once, script-only combat afterwards, death/removal, cancel, generation change
and cold restart during sinking/rising/roar. Confirm the saved floor and elapsed
state agree with the real Paper entity snapshot; inspect real native frames.

Server compilation including this slice succeeded with compileEndEventJava:
C:/Temp/cm-ai-code-only-20261114-build.log. No test task was invoked.
No release JAR/pin, production runtime or installed Prism client changed here.
This source slice is NOT a verified release candidate.
