# Follow-up to the DeepSeek release

Source reviewed: `b8221d0b` / `17f0c3a2`, plus the narrow repair below.
This record supplements the earlier status narrative without rewriting it.

## Reproduced defects

1. The production EndEvent artifact was `5a09c736...` (1,999,896 bytes).
   Its main class has Java class major 65 and its manifest names Java 21.
   The pinned 26.3 Gradle/JDK 25 build produces major 69. The old release pin
   mixed hashes/sizes from the two different build paths. Two forced target
   builds were compared byte for byte before pinning the target output.
2. Production CopiMineNarcotics was still `bea72432...`. Inspection of its real
   `EndEventBridgeBodyCodec.class` confirmed it contains neither `intro-age=`
   nor `CANCEL`. The new Wave 2 sender uses both. Deploying only EndEvent and
   the client therefore left an incompatible server bridge in place.
3. On the isolated target Paper, `cmend wave spawn 4` created four obelisks and
   six pressure mobs. `cmend wave clear` removed them but left durable
   `phase=WAVE_4, activeWave=0`; the next wave refused
   `official-attempt-active`. The sandbox initializer explicitly assigned that
   phase. That assignment is removed: disposable ownership already governs the
   obelisk tick, geometry and collision without rewriting the saved phase.

The explicit reset also now clears restored Wave 3/4 checkpoint objects and invalid
Wave 4 fields only after entity cleanup succeeds. The real reset-handler test
failed before this repair and passed afterward (66 related tests passed).

The new phase-ownership regression failed before the repair and passed after
it. Sixty related routing/obelisk tests passed. The original local COLLECTING
phase was restored only after stopping Paper, with the reproduced state saved
outside the checkout; production event state was not edited.

## Final target artifact

- EndEvent: 2,246,184 bytes,
  SHA-256 `650832a642d5f9223ffba60d3d8f165a88c172bc9962e6f2c76bf4e736f99ba2`.
- Narcotics/bridge: 370,499 bytes,
  SHA-256 `026787808eac784f1e0c2f48952c625c98a914e41f650d1b7e54521774835fb1`.
- Client: 13,584,022 bytes; matches `profile.lock.json` and passes active 26.3
  tests. It includes the custom arena volume and needs no Iris shaderpack.

EndEvent was built twice after the phase repair with identical bytes.
The previous installed EndEvent hash remains an allowed upgrade source.
Local installed JARs and both receipt hash fields were verified from real files.

## Evidence

- `artifacts/20261109-after-deepseek-focused.txt`: 44 passed before the phase repair.
- `artifacts/20261109-wave4-phase-regression.txt`: 60 passed after the phase repair.
- `artifacts/20261109-wave4-phase-repro.txt`: target build/bridge tests passed;
  repeated target EndEvent JAR byte-identical.
- `artifacts/20261109-after-deepseek-client.txt`: active client tests/build passed.
- `artifacts/20261109-final-target-wave4-status.txt`: original target runtime
  observed four active obelisks and six mobs before cleanup reproduced the bug.

Read the final gate/runtime/deployment receipts for completion status. An older
full test run overlapped the other executor's edits and cannot certify the final
tree. No native screenshot/gameplay acceptance is claimed: `nativeVerified=false`.
