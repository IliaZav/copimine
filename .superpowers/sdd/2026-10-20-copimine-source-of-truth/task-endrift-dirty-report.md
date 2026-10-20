# Audited dirty integrated-final source checkpoint

Date: 2026-10-20. Scope: source integration and focused compilation only.

## Provenance and boundaries

- Canonical checkout: `D:\Desktop\Copimine\copimine-source-of-truth`.
- Canonical branch: `codex/copimine-source-of-truth-20261020`.
- Clean starting HEAD: `9b84987a6809c1d3a9802bfd5e1d7c062fd3b738`.
- Origin: `https://github.com/IliaZav/copimine.git`; existing GitLab remote retained.
- Read-only source: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-26-3-integrated-final`.
- Source HEAD verified before copying and after build: `4e3099a24d5ef9001c3f30521d88f2aa92009fe6`.
- Canonical End Event main source matched the integration base before applying the focused dirty delta. Existing V4/client changes are outside the imported delta.
- No branch merge, push, runtime/world/config installation, gameplay test, live plugin test, or other-worktree edit.

## Imported source

The 21 named files from the brief were imported, resolving the Java test directory typo to the actual root `tests/` paths. The coordinator explicitly confirmed these paths.

- `copimine-end-event/config.yml`
- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- `copimine-end-event/src/me/copimine/endevent/domain/PressureBudgetController.java`
- `copimine-end-event/src/me/copimine/endevent/domain/WaveProjectileAdmissionController.java`
- `copimine-end-event/src/me/copimine/endevent/domain/WavePressurePolicy.java`
- `copimine-end-event/src/me/copimine/endevent/domain/BlackFogCurtainPolicy.java`
- `tests/BlackFogCurtainPolicyTest.java`
- `tests/PressureBudgetControllerTest.java`
- `tests/WavePressurePolicyTest.java`
- `tests/WaveProjectileAdmissionControllerTest.java`
- `tests/WaveTwoStagePolicyTest.java`
- `tests/test_admin_auth_price_catalog_contract.py`
- `tests/test_end_rift_wave_ai_roles.py`
- `tests/test_kosa_shield_followup_contract.py`
- `tests/test_public_artifact_previews.py`
- `tests/test_wave4_obelisk_collapse.py`
- `tests/test_wave5_black_fog_adapter.py`
- `tests/test_wave7_named_runtime.py`
- `admin-web/backend/commerce_catalog.py`
- `deploy/ubuntu/copimine_full_replace.sh`
- `docs/end-rift/final-26.3/WAVE_02.md`

Coordinator subsequently expanded the allowlist for the focused supporting hunk in `tests/RunEndRiftWave6Wave7BoundariesLive.ps1` and the five exact shop-preview PNGs listed below. No other asset output was copied.

Behavior represented by this checkpoint: Wave 1 small-party pressure 12; Wave 2 reserve 36 across three groups; separate bounded ordinary and named five-arrow rain reservations; bounded Wave 5 perimeter curtain; one-shot obelisk fracture cue; shared random Wave7 trial selection; disabled donation catalog rows omitted; CopiMineArtifacts runtime data retained by replacement deployment.

## Necessary corrections to the copied tests and supporting runner

1. `test_wave5_black_fog_adapter.py`: corrected `SOURCE / domain` (SOURCE is a file) to `SOURCE.parent / domain`, and asserted the actual 96-by-8 computed point budget rather than a nonexistent literal `MAX_PARTICLE_POINTS = 768`.
2. `RunEndRiftWave6Wave7BoundariesLive.ps1`: removed `| Out-Null` from the new assigned `Wait-Log` call so its returned log text reaches the trial-layout regex. The live runner was parsed only, never executed.
3. `test_wave7_named_runtime.py`: matched the actual capturing-group regex and asserted parsed layout/count logic.
4. `test_admin_auth_price_catalog_contract.py`: aligned the expected Kosa description with the already canonical independent 30% HP, 20% hunger, 20% wither, 10% blindness, separate 2.5% AR description. The dirty source's replacement expectation still grouped hunger/wither and did not match canonical catalog text.

## Wave7 static trace

`preflightWave7StartBeforeWorldMutation` now calls `RealitySplitTrialController.beginNamed(..., ThreadLocalRandom.current())` for sandbox and official runs. Its random overload calls `Wave7RoomLayoutPolicy.select`. That policy sets room count to `min(player count, four trial types)`, shuffles the four-type pool, and takes a prefix without replacement. Thus two and three participants select two and three distinct trials. Existing snapshot validation enforces one participant per room for these sizes. Persisted restore does not reroll. The focused dirty main-file hunk removes only the sandbox fixed-layout helper and the sandbox echo-preflight bypass; production coordinator/policy files already implement the approved selection and needed no import.

## Shared projectile assets

No PNG import was needed. The three projectile assets were SHA-256 identical in source and canonical:

| Asset under `resourcepacks/src/assets/copimine/textures/item/` | SHA-256 |
| --- | --- |
| `end_rift_rift_bolt.png` | `4e3328ef4e7c2dcff3f30dc2fbf99a5cccd8cbeb4e6dd9c0102f5504b4185b93` |
| `end_rift_void_burst.png` | `a9f96a941d0c6e66470a20abdc32359b324bf3cdd2586ffe126c22f27e2cdba7` |
| `end_rift_web_shot.png` | `d49b32c975f3a316ef72150674ba7ce4b6b73268c41dd14cf797075ff1b6f10c` |

## Checks performed

- `git diff --check`: passed.
- Python `ast.parse` for changed commerce module plus seven imported Python contract files: passed (eight files). No imported wave/contract tests executed.
- PowerShell parser on the changed Wave6/Wave7 live runner: passed. Runner not executed.
- Focused Gradle `endEventJar --offline --no-daemon`: BUILD SUCCESSFUL, 11 tasks executed, 38 seconds; upstream API deprecation/removal warnings only.
- Exact Java runtime confirmed as Temurin `25.0.4.1+1`; Gradle `9.8.1`. Toolchains read from source worktree `build/minecraft-26.3/toolchain/jdk-25.0.4.1+1` and `gradle-9.8.1`. Suggested `local-runtime/toolchains` paths were absent, so no build was run there.
- Build command: `gradle.bat -p tools/minecraft-26.3 --no-daemon --offline endEventJar` with process-local JAVA_HOME pointing to the exact JDK.
- All five imported Java test sources compiled with exact JDK against canonical `build/minecraft-26.3/plugins/classes/java/endEvent`; none executed.
- Generated local JAR: `build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar`, 1,737,950 bytes, SHA-256 `64d80d43c933b620fd6379fac0c8d53418a28925a7d2d7bed689b88382b4bf4f`. Ignored build output, excluded from source commit and runtime installation.
- Build log: `.superpowers/sdd/2026-10-20-copimine-source-of-truth/dirty-endEventJar.log` (local evidence).

## Required shop previews

The explicitly requested `test_public_artifact_previews.py` required five previews initially absent from canonical. The coordinator explicitly expanded the allowlist to include these five exact files under `admin-web/frontend/assets/item-textures/`. Each source web image was SHA-256 identical to both its source and canonical resource-pack original under `resourcepacks/src/assets/copimine/textures/item/artifacts/` before copying. No resource-pack originals or unrelated assets changed.

| PNG | Bytes | SHA-256 |
| --- | --- | --- |
| `gravedigger_contract.png` | 337 | `7587abb17b4a7201baf36d97ae8ea058eeba330f4538d0c1faba86d6f623d60c` |
| `signal_bell.png` | 284 | `9787f9bb40596f77ad54f68690d20ed842fadbbd523c97617220f277236ee495` |
| `berserker_heart.png` | 308 | `98bcf458b3d89fcd72d2e9be9e6edde7229189278e3729f2744601ed58c51357` |
| `zhilorez_pickaxe.png` | 305 | `ad2c2d7cca7aad11dc8923af6207334705e2b135f2f4662a18f77e70442bc63f` |
| `donation_night_cloak.png` | 316 | `ade4cadbedbcbe80935772dacd4958bd9c76731979b7632ee7c1b28665db036b` |

Loaded the canonical commerce catalog directly for a source consistency check: all five IDs are present and point to `/assets/item-textures/<id>.png`. This check passed. The imported pytest suite was not executed.

## Acceptance state

Source changes imported and focused compilation passed. Tests were compiled/parsed only. Server startup/accessibility, gameplay, native Minecraft, release installation, and independent review remain unverified by this task. `nativeVerified=false`. This checkpoint is intended for one local source commit followed by independent review before any push.

## Independent review correction round

Starting checkpoint: `7318a0efb79955602f3a23454739c3ace3abfc29`. The coordinator supplied the completed independent review (two P2 policy-test defects) and the CodeRabbit Warden-layout finding, and authorized one focused correction commit plus source policy test execution.

Validated and fixed:

1. `PressureBudgetControllerTest`: imported expected totals 9/11/15 for 3/4/6 players were inconsistent with the existing ordinary budget. Production `Profile.projectileCap()` remains `max(4, min(16, 4 + players / 3))`, using integer division; this gives ordinary limits 4/5/5/6 for 2/3/4/6 players. The accepted checkpoint adds exactly five reserved named-rain children, yielding combined limits 9/10/10/11. The source change explicitly preserves ordinary caps, so production behavior was retained and the test now independently checks ordinary values and correct combined totals. Before correction, execution failed at the three-player assertion.
2. `WaveProjectileAdmissionControllerTest`: removed the old mutually exclusive reservation assumptions. The sequence now verifies ordinary-first and rain-first coexistence, atomic rejection above the ordinary cap, one named rain at a time, separate release accounting, group rollback, transfer-to-cloud accounting, release of named unmaterialized children while retaining the live child, last-child lane reuse, and clearing/reusing both fully occupied lanes. Before correction, execution failed at the assertion forbidding a rain alongside one ordinary projectile.
3. `RunEndRiftWave6Wave7BoundariesLive.ps1`: the initial random layout still requires exactly two distinct members of the valid four-trial pool. The Warden damage assertion and PASS marker now run only when `WARDEN` is selected. A layout without Warden emits an explicit NOT_APPLICABLE marker with its selected trials, and retains authoritative chamber-completion/cleanup requirements. The source contract checks the count, uniqueness, validity, conditional Warden assertion, and non-applicable result. The live runner was never executed.

Verification after correction:

- Exact JDK `25.0.4.1` compiled all five source policy tests against the already built canonical End Event classes.
- Executed only these plain Java policy tests: `BlackFogCurtainPolicyTest`, `PressureBudgetControllerTest`, `WavePressurePolicyTest`, `WaveProjectileAdmissionControllerTest`, `me.copimine.endevent.domain.WaveTwoStagePolicyTest`. All five returned `OK`.
- `python -m pytest -q tests/test_wave7_named_runtime.py -k sandbox_uses_the_same_distinct_random_trial_pool --basetemp=build/pytest-review-policy-fix -o cache_dir=build/pytest-review-cache`: `1 passed, 12 deselected`. This selected test reads source text only; no server, wave simulation, or Java subprocess fixtures were run by pytest.
- PowerShell parser on the changed runner: passed.
- `git diff --check`: passed.
- No production source, global toolchain, build JAR, live runtime, or other worktree changed in the correction round. No gameplay/live-plugin tests or push. Native/server acceptance remains unverified; `nativeVerified=false`.
