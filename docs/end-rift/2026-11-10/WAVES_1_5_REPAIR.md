# Waves 1–5 repair, especially official obelisks and fog/model continuity

The user's latest Wave 5 rules supersede the older three/five-cycle documents:
5–10 cycles by frozen roster, shrinking random 3x3 → 2x2 → 1x1 islands, less
entry time, longer fog, Slowness II outside, green perimeter, bounded revival
and elite pressure. Preserve the existing arena and require only CopiMineClient
and its bundled dependencies, independently of Iris or shaderpacks.

Canonical files reread and hashed before this task:

- WAVES: 6e1245b44a449a0ef1bab9193606398660c68f6bb2de7d7bb6082cc8cde50caf.
- GUARDIAN: 0c781e42e274a3f27762e0b568cc1d8ffd7dfd8623e5a0f1deb65ea73a94ae50.

## Reproduced defects and repairs

1. Official obelisk emergence accepted only PREPARED journals. The first layer
   commits APPLIED, so every following layer was blocked. Permit both live
   statuses only for the exact event/generation/world, including disposable
   waves. Reject restored, invalid or foreign journals. An extracted-production
   behavioral regression was red at APPLIED, then green for continuation and
   foreign/stale ownership refusal. Existing block-original checks remain.
2. A fog transition can emit a whole actor roster plus beam cleanup and phase
   updates simultaneously. The signed client has a 96-envelope burst and a
   48/s sustained allowance. The old sender transmits all 168 test messages
   immediately: the regression was red. Lost envelopes then create a counted
   stream gap, which correctly retires that stream and its model bindings.
   Pace application bodies at 24/s with a 16-body burst and a finite 256-body
   FIFO BEFORE allocating signed counters. Handshake/signature/replay checks
   remain unchanged. Drop queued old generations and clear the old backlog on
   context replacement. Never hold a pre-signed envelope in a queue; timestamp
   and sign it on actual dispatch.
3. Fog density was 2.8 extinction/block. Raise it to 4.8: at the shader's minimum
   eddy density, two blocks transmit less than 0.2 percent of scene light. The
   existing depth-based ray integration starts at the exact occupied safe-box
   boundary, with moving world-space noise. It does not alter horizon fog,
   shaderpacks, resource loading, model registries, the HUD or player input.

## Executed checks before final CI

- Active client complete test/build succeeded, including shaderc GLSL compilation
  and Minecraft's depth-input/UBO parser.
- Paper bridge JUnit suite succeeded, including 168-message burst draining,
  signed-counter continuity, finite queue overflow and old-generation refusal.
- CodeRabbit raised 1 minor issue: backward wall-clock movement could starve the
  queue. Added a red regression, rebased the refill clock without free tokens,
  and reran the full bridge suite successfully.
- Journal/fog lifecycle/AI lease group: 20 passed.
- Migration, provenance, Wave 4 and fog group: initially 301 passed, 4 skipped,
  1 failure (the exact predecessor allow-list expected the new valid predecessor
  to be absent). Added that actual deployed hash to the exact expected list;
  fresh provenance and journal regressions: 2 passed. No assertion weakened.

The built JARs are exported separately before CI. Final CI, production backup,
installation hashes, command probes and cleanup receipts are recorded in the
release artifact directory. Do not interpret these pre-CI results as deployment.

## Acceptance boundary

No GUI, native Minecraft session, AuthMe login, or human gameplay is operated.
`nativeVerified=false`: depth/density calculations and compiler tests do not
prove the final rendered appearance. Server-side actor/journal/cycle evidence
and signed-stream behavior are separate from native model, fog and combat
acceptance. Waves 1–3 receive regression coverage, not new mechanics in this pass.
