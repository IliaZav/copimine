# End Rift strong-test mutation evidence — 2026-09-18

This file records temporary, deliberately broken production mutants that were
run in the isolated worktree. The mutants were never committed. Each source
was restored before the corresponding final pass, and the remaining dirty
files are the user-owned visual evidence plus the intended End Rift test
changes shown by `git status --short`.

Test:
`RitualPrisonerCapturePolicyTest`

Mutation used:
In `RitualSealCapturePolicy.java`, changed the capture-radius guard from
`distanceSquared > radiusSquared` to `distanceSquared < radiusSquared`.

Expected failure:
The first eligible candidate physically inside the seal must be selected; the
mutant instead admits the outside candidate.

Observed failure:
`java.lang.AssertionError: the first eligible candidate physically inside the seal must be captured`
at `RitualPrisonerCapturePolicyTest.java:64`, with `MUTATION_TEST_EXIT=1`.

Restored SHA/diff status:
The production line was restored to `distanceSquared > radiusSquared` at
`HEAD=b5b8013cd381808028451c301b48327d140ec720`; the mutant was not staged or
committed. The final source/test pair compiled and passed after restoration.

Final pass command:
`javac -encoding UTF-8 -d tests/.mutation-capture-build copimine-end-event/src/me/copimine/endevent/domain/RitualSealCapturePolicy.java tests/RitualPrisonerCapturePolicyTest.java; java -cp tests/.mutation-capture-build RitualPrisonerCapturePolicyTest`

Final observed result:
`RitualPrisonerCapturePolicyTest OK`.

Test:
`RitualPrisonerHealthPolicyTest`

Mutation used:
In `RitualPrisonerHealthPolicy.java`, changed `safeExternalDamage(...)` from
returning `0.0D` to returning the requested damage.

Expected failure:
Captured prisoners must ignore melee, projectile, generic, and other
non-ritual external damage between authoritative drains.

Observed failure:
`java.lang.AssertionError: captured prisoner must ignore all non-ritual external damage`
at `RitualPrisonerHealthPolicyTest.java:38`, with `MUTATION_TEST_EXIT=1`.

Restored SHA/diff status:
The production line was restored to `return 0.0D` at
`HEAD=b5b8013cd381808028451c301b48327d140ec720`; the mutant was not staged or
committed. The final source/test pair compiled and passed after restoration,
including the `20_001` and `39_999` millisecond boundary cases.

Final pass command:
`javac -encoding UTF-8 -d tests/.mutation-health-build copimine-end-event/src/me/copimine/endevent/domain/RitualPrisonerHealthPolicy.java tests/RitualPrisonerHealthPolicyTest.java; java -cp tests/.mutation-health-build RitualPrisonerHealthPolicyTest`

Final observed result:
`RitualPrisonerHealthPolicyTest OK`.

Test:
`BossProjectileSweepPolicyTest`

Mutation used:
In `BossOrientedHitboxPolicy.java`, changed `segmentIntersects(...)` to return
`false` for every segment.

Expected failure:
A fast projectile segment crossing the rotated boss OBB must be accepted;
disabling the finite sweep must be caught by the test.

Observed failure:
`java.lang.AssertionError: a fast diagonal segment crossing the rotated OBB must hit`
at `BossProjectileSweepPolicyTest.java:46`, with `MUTATION_TEST_EXIT=1`.

Restored SHA/diff status:
`segmentIntersects(...)` was restored to delegate to
`segmentEntryDistance(...).isPresent()` at
`HEAD=b5b8013cd381808028451c301b48327d140ec720`; the mutant was not staged or
committed. The final OBB policy/test pair compiled and passed after
restoration.

Final pass command:
`javac -encoding UTF-8 -d tests/.mutation-sweep-build copimine-end-event/src/me/copimine/endevent/domain/BossHitboxProfile.java copimine-end-event/src/me/copimine/endevent/domain/BossHitboxTransformPolicy.java copimine-end-event/src/me/copimine/endevent/domain/BossHitboxPose.java copimine-end-event/src/me/copimine/endevent/domain/BossOrientedHitboxPolicy.java tests/BossProjectileSweepPolicyTest.java; java -cp tests/.mutation-sweep-build BossProjectileSweepPolicyTest`

Final observed result:
`BossProjectileSweepPolicyTest OK`.

Test:
`test_end_rift_evidence_portability.py::test_static_model_manifest_is_portable_and_explicitly_not_native`

Mutation used:
Changed the first preview path in
`end-rift-mob-model-preview-manifest.json` from its repository-relative path
to `C:/Users/zavod/enderman-ordinary.png`.

Expected failure:
Evidence paths must be portable repository-relative paths and must not encode a
developer workstation path.

Observed failure:
Pytest reported `AssertionError: C:/Users/zavod/enderman-ordinary.png` at
`tests/test_end_rift_evidence_portability.py:29`; result was `1 failed, 2
passed` with `MUTATION_TEST_EXIT=1`.

Restored SHA/diff status:
The preview path was restored to
`artifacts/end-rift-v3-evidence/model-previews/enderman-ordinary.png` and the
static labels remained intact. The manifest mutant was not staged or
committed.

Final pass command:
`python -m pytest -q tests/test_end_rift_evidence_portability.py`

Final observed result:
`3 passed in 0.15s`.

Final hygiene command:
`git diff --check`

Final hygiene requirement:
The command is rerun after this evidence file and all intended test/gate
changes are present. Any pre-existing user visual artifacts remain unstaged;
this evidence does not treat their presence as a clean worktree or as native
Minecraft proof.
