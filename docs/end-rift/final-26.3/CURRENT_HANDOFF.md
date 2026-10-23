# Current handoff — End Rift Waves 1–7

Updated: 2026-10-23. Previous verified source commit: `bf04d44faa58a2d281c4da6e810e64a2da784a38`; baseline source commit: `d44c8f51be00f3b6d911135f7722a155cbf20c13` (branch `codex/copimine-source-of-truth-20261020`). The tree also contains the Wave 4 reflected-projectile provenance fix described below.

## Source and CI

- `2d76ea3f` fixed the Wave 6 guard-kill diagnostic command and full-coordinate anvil landing selection.
- `d44c8f51` rebuilt the EndEvent candidate after those fixes, updated the pinned lock/receipt and installed it through the official migration installer.
- `5af6f148` requires a current Wave 1 carrier holder for canonical delivery and adds a Java regression test.
- Wave 4 fix in this tree enforces reflected-fireball provenance: both target lookup and `ObeliskIntegrityPolicy` now require the projectile's `originObeliskId` to match the obelisk being damaged. A reflected shot can no longer consume another obelisk's HP.
- New behavioral coverage: `tests/ObeliskIntegrityPolicyTest.java` and the `wrong-source-reflected-hit` runtime scenario in `tests/test_end_rift_waves2_4_mechanics.py`.
- Hosted CI run `39905475992` on `bf04d44f` completed all three mandatory jobs successfully: `migration-postgres-contract`, `static-and-contract`, `java-plugins`.
- Earlier run `39901216861` on `5af6f148` was also fully green; do not treat it as evidence for the current tree.

## Installed first-party artifacts (disposable runtime)

| Plugin | SHA-256 | Size |
|---|---|---|
| CopiMineEndEvent | `e7ef03e68a608d3a6b8447dbb072d02c4427ef3d116263d285b23e1b39701742` | 1,899,829 |
| CopiMineArtifacts | `75547398ec77fdc083c37de5a5d6449b166efb92c8af942ab0bddc5d675cf109` | 363,393 |
| CopiMineNarcotics | `d7e46fcc2bcfb8f31216cb1ea51c53bf3f492d29bc3b8c227540cb38e8ced7e2` | 372,524 |
| CopiMineUltimateAdminPlus | `70eb0caa57ae48d918170fd9fd4f9ce853bfd8c967315f774cccc606b4abe9f0` | 272,236 |
| CopiMineEconomyCore | `7fd9c779e0a5ca80b5dad4effcc0bf59d5cbcd181be7400e10432b6e69cabd32` | 170,216 |
| CopiMineElectionCore | `d2256c6976cb88a2a98b372d5f7e1b1db63013a52fdcfe519a065e3896395b65` | 234,331 |
| CopiMineWorldCore | `1b6822c1733b8ddc3dc12760e8319caed014f3148493bf1c826ca6f5153e3f14` | 36,778 |

The previous installed EndEvent artifact `152704c4b62488f2bf71ed67395a2de8fd906175ea7479dd6544769434846340` is retained only in the lock as a superseded hash.

`AuthEffects` is **not installed** in the 27-plugin receipt; only its pinned candidate hash exists.

## Evidence added this pass

- `REQUIREMENT_TEST_MATRIX.json/.md`: exactly 168 registry IDs, tests and honest statuses.
- `SOUND_CUE_MATRIX.json/.md`: 28 server cues, all Waves 1–7, both anvil volleys and four Wave 7 trials; `nativeAudioVerified=false`.
- `OWN_PLUGIN_INVENTORY.json/.md`: eight first-party manifests/builds/installed hashes and known gaps.
- `BOT_ACCEPTANCE.json/.md` and `PERFORMANCE_REPORT.md`: blocked, with no fake PASS.
- New tests: sound cue matrix, requirement matrix, own-plugin inventory contract, acceptance reports, Wave 7 named-trial completion audio, and the Wave 1 canonical holder regression in `EndRiftEncounterCoordinatorTest`.

## Hard blockers

1. `COPIMINE_CLIENT_BRIDGE_PRIVATE_KEY_FILE` is absent; the existing signing secret does not match the pinned V4 trust anchor. No approved test-anchor override exists.
2. Paper and PostgreSQL are stopped; canonical listeners `25567`/`55434` are absent.
3. No real 2/5/10/11/20-player runs, restart/death/quit recovery, combat performance or native F2/video/audio evidence.
4. GitLab push/CI remains unavailable (previous HTTP 401); GitHub is the active remote.

Do not mark Waves 1–7 fully accepted until those blockers are cleared. Automated source/contract coverage is broad but is not gameplay acceptance.

## Next safe actions

1. Provide the approved signer through normal protected configuration; do not disable V4/AuthMe and do not generate a replacement keypair.
2. Start the disposable runtime with the official initializer/installer and loopback-only ports.
3. Run the existing live runners: 2, 5, 10, 11, 20 players; all waves; death/quit/reconnect; restart; targeted boundaries; second seed.
4. Record accepted hit/death/objective receipts, timings, TPS/MSPT/memory and exact hashes.
5. Capture native F2/video+audio and update `nativeVisualVerified`/`nativeAudioVerified` only after review.
