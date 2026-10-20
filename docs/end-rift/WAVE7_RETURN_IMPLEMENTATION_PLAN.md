# Wave 7 bed return and durable participation

Parent contract: V3 Wave 7 Task B/H and the full Waves 1–7 goal. This plan does
not replace named trials, Echo implementation, remaining wave repairs, native
acceptance or performance verification. Baseline for this slice:
`21dfe8fe16c5b719080cd265b34b1a07b2b74e82`, branch `codex/end-rift-event`.

## Confirmed failure and user decision

- The source-executing `test_wave7_bed_respawn_adapter.py` reproduces the real
  callback's forced return after two ticks. Its RED evidence is
  `artifacts/end-rift-waves/20261005/wave7-bed-respawn-red.log`.
- `containRealitySplitPlayers` and `isRealitySplitPlayerCandidate` admit an
  assigned living roster member without distinguishing bed/return-pending
  state. A change to the respawn callback alone would still be defeated by
  the room containment watchdog.
- Join and world-change callbacks also schedule room containment. Walking uses
  the candidate predicate; the separate teleport boundary must also distinguish
  pending owners. A pending owner may remain outside the arena, but entering a
  room requires the exact internal return-destination permit and later admission.
- `onPlayerDeath` calls `wipeOfficialAttemptIfAllDead` immediately after
  `markDead`. Ordinary respawn calls `markAlive`, and the offline grace path
  can mark an online player alive without an explicit return action. Both
  must distinguish living at a bed from admitted to current Wave 7 combat.
- The source-executing `test_wave7_all_dead_return_adapter.py` separately
  reproduced that destructive last-death boundary. Its RED evidence is
  `artifacts/end-rift-waves/20261005/wave7-last-death-grace-red.log`.
- The human requires the entrance to be calculated from configured arena
  bounds and its world. No fixed test-world or portal-room coordinates may
  become return coordinates. Ordinary bed/dimension fallback remains vanilla.

## Implement in one existing lifecycle path

1. Extend `AttemptLifecycleController` with generation-bound return-pending
   state and per-owner incarnations. Keep registered participation entitlement
   separate from live combat admission; do not create a second encounter
   engine. Pending/staging owners cannot become objective-eligible through
   `markAlive`, refresh, join, move, or the containment watchdog alone.
2. Apply Wave 7 pending state on committed death and applicable disconnect,
   before any all-dead decision. Cancel tracked owner-targeted actions and
   stale projectile effects. Cancelled Paper deaths remain side-effect-free.
   Other waves retain their existing lifecycle until their own scoped work.
3. Preserve the server's normal bed/fallback respawn. Rebind client state
   after respawn without authorizing room admission. Present a visible,
   explicit return action to the registered owner. Do not grant an outsider,
   inactive member or stale generation the action.
4. Resolve the entrance with `ArenaEntranceLocator` and
   `ArenaEntranceResolver`, using actual configured bounds/world and the
   existing combat-floor anchor. Search only nearby perimeter candidates;
   reject unloaded chunks, world-border crossings, missing support, body
   collision, fluid/waterlogging, native hazards and event temporary danger.
   Revalidate before the actual teleport. Never replace failure with the Core,
   fixed portal room, world spawn, block construction or a global scan.
5. Start a short generation/incarnation-bound staging countdown after the
   explicit action. Staging protection is at most 60 ticks and must terminate
   before outgoing offense. Abort safely on death, quit, invalid destination,
   generation change or expired attempt. Use tracked event-owned resources.
6. Admit the owner into their exact unchanged claim only after successful
   staging and final destination validation. A completed trial sends them to
   its cleared common region. Never reassign rooms, restore old inventories,
   repair real armor, replace finite Echo supplies or heal/recreate an Echo.
   Echo-specific pause/resume will use the actual duel actor/controller when
   that required actor is implemented, and remains unproven before then.
7. Replace immediate Wave 7 last-player wipe with the configured 120-second
   monotonic return window. Bed respawn and repeated quit/join cannot reset
   the window. Only valid admitted return resolves it. Expiry/abandonment
   produces explicit failure/recovery, cleans owned resources, revokes
   protection and grants no success/reward.
8. Persist validated participation, original claims, pending state,
   incarnations and the existing all-dead deadline through the current plain
   immutable objective snapshot. Restore admission separately from combat
   profiles. Reject stale, malformed, mismatched or incomplete rights rather
   than infer a grant from arbitrary saved UUIDs. A restart cannot extend grace,
   reset abandoned claims or resurrect a half-finished staged/cast action.

## Evidence required before publication

- Execute actual adapter/lifecycle tests for bed fallback, two-tick callback,
  containment, join/world change, current/foreign/stale return, safe destination,
  completed room, offensive protection cancellation, old projectile incarnation,
  repeated death/quit/join, monotonic last-player grace and restart/abandonment.
- Keep the existing cancelled-death, roster, rune, inventory and generation
  regressions intact. Re-run the complete suite and registered gate after the
  integrated production changes, build the affected server/client components,
  and run `git diff --check`.
- Review the actual checkpoint with CodeRabbit and requested scoped security
  review, stage only owned source/test/document files, commit and normal-push,
  verify exact remote SHA and its actual Actions runs.
- Run the real-server death/return/repeat/restart matrix with synthetic inventory
  and no real player data publication. Native Minecraft must additionally show
  normal bed spawn, visible return/staging, same-room combat continuation and
  no duplicated ground items. Compilation and bots cannot close that gate.

Current source progress: geometry and physical resolver are connected to the
public, entitlement-checked `/cmend return` and entrance-local
`/cmend return enter` actions. The existing lifecycle now records pending /
staging / admitted state and incarnations. Bed respawn remains normal; the
shared containment predicate excludes pending/staging owners. Original all-dead
grace uses monotonic time and enters explicit recovery after expiry. Its original
deadline and remaining duration survive strict generation/claim snapshot restore;
missing rights fail closed. A bounded ten-second checkpoint limits crash age.
Incoming staging protection is bounded to 40 ticks, and offensive damage, bow
release or other projectile launch cancels it. Pending owners also cannot use
vanilla damage fallback against event-owned Wave 7 actors. Tracked projectiles
and directional trial actions check launch incarnations; owner death/quit cancels
locked trial actions without resetting actor health or room progress.

The first immutable CodeRabbit review raised one major issue concerning the
pre-admission room teleport. Follow-up source execution also reproduced the
pending owner's ordinary outside/bed teleport being blocked. All three internal
return moves (entrance, final room and failed-admission rollback) now use the
existing exact owner/destination permit with a `finally` clear. Tests execute the
actual event boundary and permit helper, including nested foreign teleports,
another plugin's cancellation and a throwing teleport. A separate RED adapter
proved that the 800-tick gateway could bypass all-dead return grace. The handoff
now waits for an admitted living arena participant without resetting its elapsed
timer or consuming the one-shot gateway. Valid return resumes the same timer.
Both reproduced failures are recorded in
`artifacts/end-rift-waves/20261005/wave7-return-teleport-preboss-red.log`.

Executed RED probes cover the original bed callback, immediate all-dead wipe,
missing lifecycle/entry/incarnation adapters and uncancelled locked trial attack.
The source-focused regressions now pass. Full Python has 976 passing cases and
one skip; the End Event build, registered End Rift gate, 659 validators and diff
whitespace check passed. The initial CodeRabbit major and follow-up test minor
are repaired; the final test/document follow-up raised zero issues. Scoped
security review sealed `742f3544..50b4e445` with no confirmed reportable
vulnerability and explicitly partial runtime/performance coverage. It retains
two source follow-ups: the inherited combat-floor helper's block reads precede
the resolver's loaded-chunk gate, and staging retry after offense cancellation
does not yet have an independent entry cooldown. No end-to-end no-chunk-load or
measured availability claim follows from that scan.

This is not deployed or natively
verified yet. Named Echo actor pause, finite copied supplies and private owner
duel rules remain outstanding actor integration work; legacy trials remain
explicitly identified as legacy. Full gates, checkpoint review and actual-server
bed/return/restart evidence remain open acceptance work. The source checkpoint
can be published with those limits without marking Task B/H or the parent goal
complete.
